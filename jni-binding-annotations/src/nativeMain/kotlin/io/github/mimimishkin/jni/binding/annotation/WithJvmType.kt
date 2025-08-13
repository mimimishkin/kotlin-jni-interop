package io.github.mimimishkin.jni.binding.annotation

/**
 * Adds information about according jvm type for this type.
 *
 * @property type fully qualified class name of this parameter without generics and in Java style (e.g.,
 *           `"java.lang.String"`, `"int"`, `"long[][]"` etc.)
 */
@Target(AnnotationTarget.TYPE, AnnotationTarget.TYPEALIAS)
@Retention(AnnotationRetention.BINARY)
public annotation class WithJvmType(val type: String)