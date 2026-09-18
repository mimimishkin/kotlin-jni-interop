package io.github.mimimishkin.jni.binding.annotation

/**
 * Indicates that a function is expected to have its implementation provided through JNI.
 *
 * May be applied to Kotlin `external` functions and properties. Applying both to getter/setter and property itself
 * makes no effect.
 *
 * @param targets The consumer plugin will check that every `JniExpect` has its `JniActual` pair, even if the
 * annotatee will be used only on one platform. This param allows to shrink the search field to the specified
 * target names (e.g. `"mingwX64"`). An empty list (the default) means all targets.
 * This rule will be applied to all declarations
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.SOURCE)
public annotation class JniExpect(vararg val targets: String = [])