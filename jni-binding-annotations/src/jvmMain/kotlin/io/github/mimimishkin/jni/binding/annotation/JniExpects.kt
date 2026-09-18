package io.github.mimimishkin.jni.binding.annotation

/**
 * Instead of repeating the [JniExpect] annotation, you can apply this annotation to the entire class, object or file.
 *
 * This annotation is applied to classes and files and replaces [JniExpect] by itself.
 * Each `external` function and property inside the annotated class/object/file will be treated in the same way as if
 * it was annotated with [JniExpect].
 *
 * Note: nested classes are not considered as `@JniExpects`, so if you have a file with two classes, one of which is
 * nested in another, and all of them have some external functions, you must apply this annotation 3 times.
 *
 * @param targets The consumer plugin will check that every `JniExpect` has its `JniActual` pair, even if the
 * annotatee will be used only on one platform. This param allows to shrink the search field to the specified
 * target names (e.g. `"mingwX64"`). An empty list (the default) means all targets.
 * You can use [JniExpect] to override this rule for a single function/property if needed.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
public annotation class JniExpects(vararg val targets: String = [])