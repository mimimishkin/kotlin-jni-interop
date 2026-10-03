package org.sample

import io.github.mimimishkin.jni.binding.annotation.JniExpects
import io.github.mimimishkin.jni.binding.annotation.LoadMethod

/**
 * Every `external` below is matched by name against a function of the native `@JniActuals` object, and the JNI
 * signature is derived from the Kotlin types - a mismatch is a compile error, not a crash on a device.
 */
@JniExpects
object Callbacks {

    @LoadMethod
    @Suppress("UnsafeDynamicallyLoadedCode")
    private fun load() {
        System.loadLibrary("example")
    }

    /**
     * Returns a constant, so a load failure is distinguishable from a call failure.
     */
    external fun ping(): String

    /**
     * Round trips a [String] through native.
     */
    external fun echo(message: String): String

    /**
     * Three [Int] arguments, passed by value.
     */
    external fun sum(a: Int, b: Int, c: Int): Int

    /**
     * Native allocates and returns the array.
     */
    external fun fill(size: Int, seed: Int): ByteArray

    /**
     * Native calls back into [listener] and returns [value], or -1 if no callback arrived.
     */
    external fun roundTrip(listener: Replies, value: Int): Int

    /**
     * Starts [threads] native threads that each call [listener] [iterations] times and returns the
     * total delivered.
     */
    external fun fromNativeThreads(listener: Replies, threads: Int, iterations: Int): Long
}

/**
 * Implemented in Kotlin, called from native.
 */
interface Replies {
    fun onInt(value: Int)
    fun onText(value: String)
    fun onBytes(value: ByteArray)
}
