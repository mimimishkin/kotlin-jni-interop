package org.sample

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.nio.ByteBuffer
import java.util.ArrayList
import java.util.HashMap
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : Activity() {

    private lateinit var output: TextView
    private val log = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = TextView(this)
        output.textSize = 13f

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        column.addView(title("jni-binding · android-basic"))
        column.addView(button("callbacks") { runCallbacks() })
        column.addView(button("threads") { runThreads() })
        column.addView(button("memory") { runMemory() })
        column.addView(button("critical natives") { runCritical() })
        column.addView(button("exceptions") { runExceptions() })
        column.addView(button("Java / Android API") { runAndroidApi() })
        column.addView(button("packaging") { runPackaging() })
        column.addView(output)

        setContentView(ScrollView(this).apply { addView(column) })
    }

    private fun title(text: String) = TextView(this).apply {
        this.text = text
        textSize = 18f
        setPadding(0, 0, 0, 24)
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun report(line: String) {
        log.append(line).append('\n')
        output.text = log
    }

    private fun runCallbacks() {
        report("ping() = ${Callbacks.ping()}")
        report("echo(\"hey\") = ${Callbacks.echo("hey")}")
        report("sum(1, 2, 3) = ${Callbacks.sum(1, 2, 3)}")
        report("fill(8, 42) = ${hex(Callbacks.fill(8, 42))}")

        val seen = StringBuilder()
        val value = Callbacks.roundTrip(object : Replies {
            override fun onInt(value: Int) {
                seen.append("onInt($value) ")
            }

            override fun onText(value: String) {
                seen.append("onText($value) ")
            }

            override fun onBytes(value: ByteArray) {
                seen.append("onBytes(${value.size}) ")
            }
        }, 7)
        report("roundTrip(7) = $value, callbacks: ${seen.toString().trim()}")

        val delivered = AtomicInteger()
        val total = Callbacks.fromNativeThreads(object : Replies {
            override fun onInt(value: Int) {
                delivered.incrementAndGet()
            }

            override fun onText(value: String) {
                delivered.incrementAndGet()
            }

            override fun onBytes(value: ByteArray) {
                delivered.incrementAndGet()
            }
        }, 4, 100)
        report("fromNativeThreads(4, 100) delivered $total, observed ${delivered.get()}")
    }

    private fun runThreads() {
        report("isAttached() = ${Threads.isAttached()}")
        report("currentThreadName() = ${Threads.currentThreadName()}")

        val reported = AtomicInteger()
        Progress.onReport = { reported.incrementAndGet() }
        val delivered = try {
            Threads.daemonThreadReports(4, 100)
        } finally {
            Progress.onReport = null
        }
        report("daemonThreadReports(4, 100) delivered $delivered, Progress saw ${reported.get()}")
    }

    private fun runMemory() {
        val buffer = Memory.allocateDirect(16, 1)
        report("allocateDirect(16, 1) checksum = ${Memory.fillDirect(buffer, 16, 1)}")
        report("fillDirect(heap buffer) = ${Memory.fillDirect(ByteBuffer.allocate(16), 16, 1)} (-1: no address)")
        Memory.freeDirect(buffer)

        val token = Memory.retain(Any())
        report("retain → $token; isRetained = ${Memory.isRetained(token)}")
        report("release($token) = ${Memory.release(token)}; isRetained = ${Memory.isRetained(token)}")

        report("localFrameStress(100, 100) created ${Memory.localFrameStress(100, 100)} locals in 100 frames")
    }

    private fun runCritical() {
        runGroup("critical natives") {
            var hypot = 0.0
            var mix = 0L
            var odd = 0
            var scale = 0L
            repeat(10_000) {
                hypot += criticalHypot(3.0, 4.0)
                mix += criticalMix(1, 100)
                if (criticalIsOdd(7)) odd++
                scale += criticalScale(21L, 2)
                criticalBurn(1, 100)
            }
            report("  hypot sum  = $hypot (50000.0)")
            report("  mix sum    = $mix")
            report("  isOdd hits = $odd (10000)")
            report("  scale sum  = $scale (420000)")
        }
    }

    /**
     * Runs [block] under a heading and turns any refusal by the runtime into a reported line.
     */
    private fun runGroup(title: String, block: () -> Unit) {
        report("[$title]")
        try {
            block()
            report("  ok")
        } catch (e: Throwable) {
            report("  ${e.javaClass.simpleName}: ${e.message?.lines()?.firstOrNull()}")
        }
    }

    private fun runExceptions() {
        try {
            Exceptions.throwNative("thrown natively")
            report("throwNative returned normally, which should not happen")
        } catch (e: IllegalStateException) {
            report("throwNative threw ${e.javaClass.simpleName}: ${e.message}")
        }

        val thrower = object : Thrower {
            override fun boom(): String = throw IllegalStateException("boom from Kotlin")
        }
        report("catchFromJava = ${Exceptions.catchFromJava(thrower)}")

        try {
            Exceptions.propagateFromJava(thrower)
            report("propagateFromJava returned normally, which should not happen")
        } catch (e: IllegalStateException) {
            report("propagateFromJava threw ${e.javaClass.simpleName}: ${e.message}")
        }

        report("describePending = ${Exceptions.describePending()}")
    }

    private fun runAndroidApi() {
        report("appendTo = ${AndroidApi.appendTo(StringBuilder(), "ab", 3)}")

        val map = HashMap<String, Int>()
        map["a"] = 1
        map["b"] = 2
        report("bump($map, 10) = ${AndroidApi.bump(map, 10)}, map now $map")

        val list = ArrayList<String>()
        list.add("alpha")
        list.add("beta")
        list.add("gamma")
        report("join($list) = ${AndroidApi.join(list, ", ")}")

        report("logcat = ${AndroidApi.logcat("jni-sample", "hello from native")} (see logcat)")
    }

    private fun runPackaging() {
        report("primary ABI = ${Build.SUPPORTED_ABIS.first()}")
        report("all ABIs = ${Build.SUPPORTED_ABIS.joinToString()}")
        report("native library loaded: ${Callbacks.ping() == "pong"}")
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString(" ") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
