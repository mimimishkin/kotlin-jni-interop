package io.github.mimimishkin.jni.binding.consumer.model

import kotlinx.serialization.Serializable

/**
 * A `@JniExpect` declaration reduced to plain serializable data.
 *
 * The JVM names in here are the ones the backend actually emitted (see
 * [io.github.mimimishkin.jni.binding.consumer.jvm.JniExpectMatcher]), so nothing has to be re-derived from source or
 * annotations on the other side. That is the whole point of persisting them: the `generateJniActuals` Gradle task reads
 * this file and writes `@JniActual` stubs whose reference types carry the JVM names as `@WithJvmType` typealiases,
 * without needing the producer to have been built first.
 *
 * Unlike [JniExpectInfo], which holds IR declarations so diagnostics can be reported against the exact offending
 * declaration, this model contains no compiler references and survives the compiler process.
 */
@Serializable
public data class JniExpectDeclaration(
    val className: String,
    val methodName: String,
    val isStatic: Boolean,
    val parameterTypes: List<JavaType>,
    val parameterNames: List<String> = emptyList(),
    val returnType: JavaType,
    val targets: List<String> = emptyList(),
    val isCritical: Boolean,
    val superClasses: Map<String, String?> = emptyMap(),
) {
    /**
     * Identity of the declaration as a JVM method: the same method reached through two expects restricted to different
     * targets is one declaration, not two.
     */
    public val signatureKey: String
        get() = listOf(className, methodName, isStatic, parameterTypes.joinToString(",") { it.type }, returnType.type)
            .joinToString("|")
}
