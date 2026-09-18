package io.github.mimimishkin.jni.binding.annotation

/**
 * Marks a [JniActual] function as a JNI critical native function. Such a function does not receive a `JniEnv` or a
 * `JClass`/`JObject` reference. Its parameters and return value must be primitives or primitive arrays only.
 *
 *  On Android this works as expected, thanks to the platform's built-in critical native support. On the JVM it may only
 *  work up to JDK 22, is undocumented, and requires the legacy `ExposeFunctions` binding mode. Prefer ProjectPanama
 *  instead.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public actual annotation class CriticalNative