package io.github.mimimishkin.jni.binding.annotation

/**
 * Instead of repeating the [JniExpect] annotation, you can apply this annotation to the entire class, object or file.
 *
 * This annotation is applied to top-level objects and replaces [JniExpect] by itself.
 * Each `external` function inside the annotated class/object/file will be treated in the same way as if it was
 * annotated with [JniExpect].
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
public annotation class JniExpects()