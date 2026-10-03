package org.sample

import io.github.mimimishkin.jni.binding.annotation.JniExpects
import io.github.mimimishkin.jni.binding.annotation.LoadMethod

/** Native learns the VM in `JNI_OnLoad`; every thread it starts later attaches to it on demand. */
@JniExpects
object Threads {

    @LoadMethod
    @Suppress("UnsafeDynamicallyLoadedCode")
    private fun load() {
        System.loadLibrary("example")
    }

    /** Whether the calling thread is attached to the VM. */
    external fun isAttached(): Boolean

    /** `Thread.currentThread().name`, read from native. */
    external fun currentThreadName(): String

    /** Starts [threads] native threads that each call [Progress.report] [iterations] times. */
    external fun daemonThreadReports(threads: Int, iterations: Int): Long
}

/** Reached from native with `FindClass`/`GetStaticMethodID`, not through a binding. */
object Progress {
    /** Assigned by the sample before the stress run and cleared afterwards. */
    @Volatile
    var onReport: ((Int) -> Unit)? = null

    @JvmStatic
    fun report(value: Int) {
        onReport?.invoke(value)
    }
}
