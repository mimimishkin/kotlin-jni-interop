package io.github.mimimishkin.jni.annotations

/**
 * Indicates that a function is the actual implementation of a function expected to be provided through JNI.
 * This is applied to top-level functions that provide the native implementation for platform-specific or native
 * functionality.
 *
 * If used in tandem with `JniExpect`, the following requirements must be applied at compile-time:
 * 1. All functions marked with `JniExpect` have an implementation with this annotation.
 * 2. Each pair of `JniExpect` and [JniActual] functions has the same parameters.
 *
 * So you don't need to worry about [UnsatisfiedLinkError].
 *
 * Should be applied to functions that:
 * - are top-level
 * - have no or one context parameter of type `JniEnv`
 * - hava a receiver of type `JObject` or `JClass` or don't have any receiver.
 *
 * @property className The fully qualified name of the class. E.g., `"com.example.NativeHelper"`
 * @property methodName The name of the method in the class, no mater static or instance. E.g., `"nativeComputation"`.
 * Optional: may be empty if the name is the same as the marked function has.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class JniActual(val className: String, val methodName: String = "")