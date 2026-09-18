package io.github.mimimishkin.jni.binding.consumer.model

import kotlinx.serialization.Serializable

@Serializable
public data class JniActualInfo(
    val needEnv: Boolean,
    val isStatic: Boolean?,
    val className: String,
    val methodName: String,
    val parameterTypes: List<JavaType>,
    val returnType: JavaType,
    val source: String
) {
    public fun isStasisValuable(): Boolean = isStatic != null
}