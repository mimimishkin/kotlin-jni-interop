package io.github.mimimishkin.jni.binding.annotation

/**
 * Adds information about jvm signature for functions annotated with [JniActual].
 *
 * If is used together with [WithJvmType], this annotation will override it.
 *
 * @property parameterTypes array of fully qualified class names of the function parameters without generics and in Java
 *           style (e.g., `"java.lang.String"`, `"int"`, `"long[][]"` etc.)
 * @property returnType fully qualified name of the function return type in Java style.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class WithJvmSignature(val parameterTypes: Array<String>, val returnType: String)