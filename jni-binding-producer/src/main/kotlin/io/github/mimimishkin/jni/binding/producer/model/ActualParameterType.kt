package io.github.mimimishkin.jni.binding.producer.model

import io.github.mimimishkin.jni.binding.producer.Symbols
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.StandardClassIds

/**
 * The binary representation type of a `@JniActual` parameter or return value on the native side,
 * bridging three spellings of one concept:
 * - [jvmType] - the JVM type name (`int`, `java.lang.String`, `@NonNull java.lang.String`, ...),
 * - [signatureType] - the JNI field descriptor used in C symbols and `RegisterNatives` signatures,
 * - [nativeClassId] - the Kotlin/Native type a facade exposes the value as.
 */
public sealed class ActualParameterType(public open val jvmType: String) {
    // kotlin.Boolean binary representation can vary depending on the platform,
    // so we need to expose functions with UByte and manually convert it to boolean value
    public data object BooleanIn : ActualParameterType("boolean")
    public data object BooleanOut : ActualParameterType("boolean")
    public data object UByte : ActualParameterType("boolean")
    public data object Byte : ActualParameterType("byte")
    // kotlin.Char always has `unsigned short` binary representation, the same as JVM uses,
    // so it's safe to use it directly in parameters/return types.
    public data object Char : ActualParameterType("char")
    public data object UShort : ActualParameterType("char")
    public data object Short : ActualParameterType("short")
    public data object Int : ActualParameterType("int")
    public data object Long : ActualParameterType("long")
    public data object Float : ActualParameterType("float")
    public data object Double : ActualParameterType("double")
    public data object Unit : ActualParameterType("void")
    public data class Ref(override val jvmType: String) : ActualParameterType(jvmType)

    /** The JVM type name without the nullability prefix (`@NonNull `/`@Nullable `). */
    public val withoutNullabilityPrefix: String
        get() {
            return jvmType.removePrefix("@NonNull ").removePrefix("@Nullable ")
        }

    /**
     * The JNI field descriptor of this type (`I`, `Z`, `Ljava/lang/String;`, `[I`, ...). Array
     * suffixes in the JVM type name (`int[]`) are translated to leading `[` dimension characters,
     * which is where the JNI descriptor nests the element type.
     */
    public val signatureType: String
        get() {
            tailrec fun signatureType(type: String, dimension: kotlin.Int): String {
                if (type.endsWith("[]")) return signatureType(type.dropLast(2), dimension + 1)
                val type = when (type) {
                    "boolean" -> "Z"
                    "byte" -> "B"
                    "char" -> "C"
                    "short" -> "S"
                    "int" -> "I"
                    "long" -> "J"
                    "float" -> "F"
                    "double" -> "D"
                    "void" -> "V"
                    else -> "L${type.replace(".", "/")};"
                }
                return "[".repeat(dimension) + type
            }

            return signatureType(withoutNullabilityPrefix, 0)
        }

    /**
     * The Kotlin/Native class a facade exposes this type as. `kotlin.Boolean` is represented as
     * `UByte` (the JNI `jboolean`); [Ref] types are exposed as `COpaquePointer`, keeping the facade
     * free of any concrete cinterop type.
     */
    public val nativeClassId: ClassId
        get() = when (this) {
            BooleanIn, BooleanOut, UByte -> StandardClassIds.UByte
            Byte -> StandardClassIds.Byte
            Char -> StandardClassIds.Char
            UShort -> StandardClassIds.UShort
            Short -> StandardClassIds.Short
            Int -> StandardClassIds.Int
            Long -> StandardClassIds.Long
            Float -> StandardClassIds.Float
            Double -> StandardClassIds.Double
            Unit -> StandardClassIds.Unit
            is Ref -> Symbols.COpaquePointer
        }

    public companion object {
        /**
         * Concatenates the JNI signature of [parameters]; when [returnType] is given the result is the
         * full `(params)return` method descriptor, otherwise just the parameter part (as used for the
         * overload disambiguation suffix, which never includes the return type).
         */
        public fun mapSignature(parameters: Iterable<ActualParameterType>, returnType: ActualParameterType?): String {
            val parameters = parameters.joinToString("") { it.signatureType }
            if (returnType != null) {
                val returnType = returnType.signatureType
                return "($parameters)$returnType"
            } else {
                return parameters
            }
        }

        /**
         * Maps a JVM type name to its [ActualParameterType]: the primitives to their singleton, `void`
         * to [Unit], everything else to [Ref] preserving the name verbatim. A nullability prefix is
         * ignored when recognizing a primitive, since a primitive is never nullable. [isReturn] selects
         * the `boolean` in/out split: parameters are converted to and from `kotlin.Boolean`, returns are not.
         */
        public fun fromJavaType(jvmType: String, isReturn: Boolean): ActualParameterType {
            return when (jvmType.removePrefix("@NonNull ").removePrefix("@Nullable ")) {
                "boolean" -> if (isReturn) BooleanOut else BooleanIn
                "byte" -> Byte
                "char" -> Char
                "short" -> Short
                "int" -> Int
                "long" -> Long
                "float" -> Float
                "double" -> Double
                "void" -> Unit
                else -> Ref(jvmType)
            }
        }
    }
}

