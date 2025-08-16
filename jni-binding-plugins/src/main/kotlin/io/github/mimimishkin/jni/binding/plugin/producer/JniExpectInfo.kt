package io.github.mimimishkin.jni.binding.plugin.producer

import kotlinx.serialization.Serializable

/**
 * Contains information about a function annotated with `@JniExpect`.
 *
 * @property className name of the class where the function is defined.
 * @property methodName name of the function.
 * @property isStatic whether the function is static.
 * @property parameterTypes list of types of function parameters in Java notation (e.i., primitives as their names,
 *           arrays as their names with brackets, and other classes as their reflection names, e.g.
 *           `"int"`, `"float[]"`, `java.lang.String`).
 * @property returnType type of function return value in Java notation (e.g., `"void"`, `"java.lang.String"`).
 */
@Serializable
public data class JniExpectInfo(
    val className: String,
    val methodName: String,
    val isStatic: Boolean,
    val parameterTypes: List<String>,
    val returnType: String,
)