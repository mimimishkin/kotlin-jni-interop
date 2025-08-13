package io.github.mimimishkin.jni.binding.annotation

/**
 * The function annotated with this annotation will be called when the native library is loaded (for example, through
 * `System.loadLibrary`).
 *
 * Annotated function must be top-level and have no or single parameter of type `JavaVM`.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class JniOnLoad()