package io.github.mimimishkin.jni.binding.annotation

/**
 * The function annotated with this annotation will be called when the class loader containing the native library is
 * garbage collected.
 *
 * This is useful for cleanup operations. Because this function is called in an unknown context (such as from a
 * finalizer), the programmer should be conservative on using Java VM services and refrain from arbitrary Java
 * call-backs.
 *
 * Annotated function must be top-level and have no or single parameter of type `JavaVM`.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class JniOnUnload()