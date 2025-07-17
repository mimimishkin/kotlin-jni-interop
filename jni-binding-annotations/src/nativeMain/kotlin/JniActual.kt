package io.github.mimimishkin.jni.annotations

/**
 * This annotation is similar to Kotlin `actual` modifier but for JNI.
 * An annotation used to indicate that a function is the actual implementation of a function expected to be provided
 * through JNI (Java Native Interface). This is applied to functions that provide the native implementation
 * for platform-specific or native functionality.
 *
 * This annotation provides the following safety guaranties at runtime:
 * 1. All functions marked with [JniExpect] have an implementation with this annotation.
 * 2. Each pair of [JniExpect] and [JniActual] functions has the same parameters.
 *
 * If they are not met, you will receive an error with explanations at compile time.
 * So you don't need to worry about [UnsatisfiedLinkError].
 *
 * @property className The fully qualified name of the class. E.g., `"com.example.NativeHelper"`
 * @property methodName The name of the method in the class, no mater static or instance. E.g., `"nativeComputation"`.
 * May be empty if the name is the same as the marked function has.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class JniActual(val className: String, val methodName: String = "")