package io.github.mimimishkin.jni.binding.annotation

/**
 * Instead of duplicating the `className` parameter in each [JniActual] annotation, you can use this one.
 *
 * This annotation is applied to top-level objects and replaces [JniActual] by itself.
 * Each function inside the annotated object will be treated in the same way as if it was annotated with [JniActual]
 * with [className] as the parameter of the same name and its own name as `methodName` parameter.
 *
 * @property className The fully qualified name of the class. E.g., `"com.example.NativeHelper"`. Optional: may be empty
 * if the annotated object has the same qualifier as the desired class.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class JniActuals(val className: String = "")