package io.github.mimimishkin.jni.annotations

/**
 * This annotation is similar to Kotlin `expect` modifier but for JNI.
 * An annotation used to indicate that a function is expected to have its implementation provided through JNI (Java
 * Native Interface). This is applied to functions that rely on platform-specific or native implementations.
 *
 * This annotation provides the following safety guaranties at runtime:
 * 1. All functions marked with this annotation have an implementation with [JniActual] annotation.
 * 2. Each pair of [JniExpect] and [JniActual] functions has the same parameters.
 *
 * If they are not met, you will receive an error with explanations at compile time.
 * So you don't need to worry about [UnsatisfiedLinkError].
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class JniExpect()