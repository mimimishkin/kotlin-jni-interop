package io.github.mimimishkin.jni.binding.producer.model

import kotlinx.serialization.Serializable

/**
 * Serialized information about a single `@JniActual` function.
 *
 * This is the data contract with the consumer plugin: files containing a JSON list of these are written by
 * the producer and passed to the consumer compiler plugin.
 *
 * [parameterTypes] and [returnType] are JVM types. Nullability is stored as a `@NonNull ` / `@Nullable ` prefix,
 * the same format the consumer plugin's [io.github.mimimishkin.jni.binding.consumer.model.JavaType] uses.
 */
@Serializable
public data class JniActualInfo(
    val needEnv: Boolean,
    val isStatic: Boolean?,
    val className: String,
    val methodName: String,
    val parameterTypes: List<String>,
    val returnType: String,
    val source: String,
)
