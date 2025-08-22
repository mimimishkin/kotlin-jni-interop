package io.github.mimimishkin.jni.binding.annotation

/**
 * Adds information about according jvm type for this type.
 *
 * If this annotation is applied multiple times, the last one will be used. For example:
 * ```
 * typealias BaseClass = @WithJvmType("java.lang.Number") JObject
 * typealias SpecificClass = @WithJvmType("java.lang.Integer") BaseClass
 * ```
 *
 * @property type fully qualified class name of this parameter without generics and in Java style (e.g.,
 *           `"java.lang.String"`, `"int"`, `"long[][]"` etc.)
 */
@Target(AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
@Repeatable
public annotation class WithJvmType(val type: String)