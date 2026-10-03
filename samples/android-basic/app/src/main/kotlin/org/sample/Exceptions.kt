package org.sample

import io.github.mimimishkin.jni.binding.annotation.JniExpects
import io.github.mimimishkin.jni.binding.annotation.LoadMethod

/** A pending JNI exception has to be cleared or left for the JVM; most JNI calls misbehave while one is set. */
@JniExpects
object Exceptions {

    @LoadMethod
    @Suppress("UnsafeDynamicallyLoadedCode")
    private fun load() {
        System.loadLibrary("example")
    }

    /** Raises an `IllegalStateException` in native; the Kotlin caller gets the exception, not the return value. */
    external fun throwNative(message: String): String

    /** Calls [Thrower.boom], catches the Java exception in native, describes it and clears it. */
    external fun catchFromJava(thrower: Thrower): String

    /** Calls [Thrower.boom] and leaves the exception pending for the Kotlin caller. */
    external fun propagateFromJava(thrower: Thrower): String

    /** Creates an exception, inspects and clears it, and returns a description. */
    external fun describePending(): String
}

/** Implemented in Kotlin, called from native, throws a Java exception on demand. */
interface Thrower {
    fun boom(): String
}
