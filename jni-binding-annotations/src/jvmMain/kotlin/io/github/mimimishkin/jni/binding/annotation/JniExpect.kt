package io.github.mimimishkin.jni.binding.annotation

/**
 * Indicates that a function is expected to have its implementation provided through JNI.
 * This is applied to functions that rely on platform-specific or native implementations.
 *
 * The following requirements must be applied at compile-time:
 * 1. All functions marked with this annotation have an implementation with `JniActual` annotation.
 * 2. Each pair of [JniExpect] and `JniActual` functions has the same parameters.
 *
 * So you don't need to worry about [UnsatisfiedLinkError].
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class JniExpect()