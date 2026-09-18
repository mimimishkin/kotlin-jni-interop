package io.github.mimimishkin.jni.binding.consumer.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind.STRING
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(JavaTypeSerializer::class)
public data class JavaType(
    val type: String,
    val nullable: Boolean = false,
) {
    override fun toString(): String {
        if (type in primitiveTypes) return type
        val nullabilityMarker = if (nullable) "@Nullable " else "@NonNull "
        return nullabilityMarker + type
    }
}

private val primitiveTypes = setOf("boolean", "byte", "char", "short", "int", "long", "float", "double", "void")

public object JavaTypeSerializer : KSerializer<JavaType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("io.github.mimimishkin.jni.binding.consumer.model.JavaTypeSerializer", STRING)

    override fun serialize(encoder: Encoder, value: JavaType) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): JavaType {
        val str = decoder.decodeString()
        return when {
            str.startsWith("@Nullable ") -> JavaType(str.removePrefix("@Nullable "), true)
            str.startsWith("@NonNull ") -> JavaType(str.removePrefix("@NonNull "), false)
            else -> JavaType(str, false)
        }
    }
}