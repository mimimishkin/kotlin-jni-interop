package io.github.mimimishkin.jni.binding.annotation

/**
 * Indicates that a function (public or internal) is the actual implementation of a function expected to be provided
 * through JNI.
 *
 * Should be applied to functions that:
 * - are top-level
 * - have no one or a single context parameter of type `JniEnv`
 * - have a receiver of type `JObject` or `JClass` or don't have any receiver.
 *
 * @property className The fully qualified name of the class. E.g., `"com.example.NativeHelper"`
 * @property methodName The name of the method in the class, no mater static or instance. E.g., `"nativeComputation"`.
 * Optional: may be omitted if the name is the same as the marked function has.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class JniActual(val className: String, val methodName: String = "")