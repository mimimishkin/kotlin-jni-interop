package io.github.mimimishkin.jni.binding.plugin.producer

import com.google.devtools.ksp.findActualType
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.Nullability.NULLABLE
import com.squareup.kotlinpoet.ksp.toTypeName
import io.github.mimimishkin.jni.binding.plugin.JavaType
import io.github.mimimishkin.jni.binding.plugin.resolve


/**
 * Types of signatures that can be used in function annotated with `@JniActual` and `@JniOnLoad`/`@JniOnUnload`.
 */
public sealed class SignatureTypes {
    public companion object {
        public fun valueOf(name: String): SignatureTypes {
            return when (name) {
                "FromJniBindings" -> FromJniBindings
                "CorrectlyNamed" -> CorrectlyNamed
                "Generic" -> Generic
                else -> throw IllegalArgumentException("Unknown signature type: $name")
            }
        }

        internal val castReturns: Map<String, String> = mapOf(
            "kotlin.Boolean" to ".toJBoolean()",
            "kotlin.Char" to ".toJChar()",
        )

        internal val castParameter: Map<String, String> = mapOf(
            "kotlin.Boolean" to ".tKJBoolean()",
            "kotlin.Char" to ".toKChar()",
        )
    }

    /**
     * Functions annotated with `@JniActual` and `@JniOnLoad`/`@JniOnUnload` must have the correct receiver, parameters
     * and return type:
     * - Primitive types must be one of `JBoolean`, `JByte`, `JChar`, `JShort`, `JInt`, `JLong`, `JFloat`, `JDouble`
     *   or simply Kotlin primitive type.
     * - Reference types must be one of `JObject`, `JClass`, `JObject`, `JClass`, `JThrowable`, `JString`, `JArray`,
     *   `JBooleanArray`, `JByteArray`, `JCharArray`, `JShortArray`, `JIntArray`, `JLongArray`, `JFloatArray`,
     *   `JDoubleArray`, `JObjectArray` (yes, it will be checked if jni-binding-consumer plugin is used).
     * - JNI environment parameter must be of type `JniEnv`.
     * - Java VM parameter must be of type `JavaVM`.
     *
     * Use this in tandem with `jni-binding` library.
     */
    public data object FromJniBindings : SignatureTypes() {
        internal val aliases: Map<String, JavaType> = mapOf(
            "io.github.mimimishkin.jni.JBoolean" to JavaType.Boolean,
            "io.github.mimimishkin.jni.JByte" to JavaType.Byte,
            "io.github.mimimishkin.jni.JChar" to JavaType.Char,
            "io.github.mimimishkin.jni.JShort" to JavaType.Short,
            "io.github.mimimishkin.jni.JInt" to JavaType.Int,
            "io.github.mimimishkin.jni.JLong" to JavaType.Long,
            "io.github.mimimishkin.jni.JFloat" to JavaType.Float,
            "io.github.mimimishkin.jni.JDouble" to JavaType.Double,
            "io.github.mimimishkin.jni.JObject" to JavaType.Object,
            "io.github.mimimishkin.jni.JClass" to JavaType.Class,
            "io.github.mimimishkin.jni.JThrowable" to JavaType.Throwable,
            "io.github.mimimishkin.jni.JString" to JavaType.String,
            "io.github.mimimishkin.jni.JArray" to JavaType.Array,
            "io.github.mimimishkin.jni.JBooleanArray" to JavaType.BooleanArray,
            "io.github.mimimishkin.jni.JByteArray" to JavaType.ByteArray,
            "io.github.mimimishkin.jni.JCharArray" to JavaType.CharArray,
            "io.github.mimimishkin.jni.JShortArray" to JavaType.ShortArray,
            "io.github.mimimishkin.jni.JIntArray" to JavaType.IntArray,
            "io.github.mimimishkin.jni.JLongArray" to JavaType.LongArray,
            "io.github.mimimishkin.jni.JFloatArray" to JavaType.FloatArray,
            "io.github.mimimishkin.jni.JDoubleArray" to JavaType.DoubleArray,
            "io.github.mimimishkin.jni.JObjectArray" to JavaType.ObjectArray,
        )

        internal val kotlinTypes: Map<String, JavaType> = mapOf(
            "kotlin.Boolean" to JavaType.Boolean,
            "kotlin.UByte" to JavaType.Boolean,
            "kotlin.Byte" to JavaType.Byte,
            "kotlin.Char" to JavaType.Char,
            "kotlin.UShort" to JavaType.Char,
            "kotlin.Short" to JavaType.Short,
            "kotlin.Int" to JavaType.Int,
            "kotlin.Long" to JavaType.Long,
            "kotlin.Float" to JavaType.Float,
            "kotlin.Double" to JavaType.Double,
        )

        internal val primitives: List<String> = listOf(
            "io.github.mimimishkin.jni.JBoolean",
            "io.github.mimimishkin.jni.JByte",
            "io.github.mimimishkin.jni.JChar",
            "io.github.mimimishkin.jni.JShort",
            "io.github.mimimishkin.jni.JInt",
            "io.github.mimimishkin.jni.JLong",
            "io.github.mimimishkin.jni.JFloat",
            "io.github.mimimishkin.jni.JDouble",
            "kotlin.Boolean",
            "kotlin.UByte",
            "kotlin.Byte",
            "kotlin.Char",
            "kotlin.UShort",
            "kotlin.Short",
            "kotlin.Int",
            "kotlin.Long",
            "kotlin.Float",
            "kotlin.Double",
            "io.github.mimimishkin.jni.JVoid",
            "kotlin.Unit",
        )

        internal val returnTypeAliases = aliases + mapOf("io.github.mimimishkin.jni.JVoid" to JavaType.Void)
        internal val returnTypeKotlin = kotlinTypes + mapOf("kotlin.Unit" to JavaType.Void)

        internal fun make(
            type: KSType,
            aliases: Map<String, JavaType>,
            kotlinTypes: Map<String, JavaType>,
            castExpression: Map<String, String>,
        ): JniActualInfo.Parameter {
            var javaType: JavaType? = null
            var qualifier: String? = null

            val alias = type.declaration as? KSTypeAlias
            if (alias != null) {
                qualifier = alias.qualifiedName?.asString()
                javaType = aliases[qualifier]
            }

            if (javaType == null) {
                val clazz = alias?.findActualType() ?: type.declaration as KSClassDeclaration
                qualifier = clazz.qualifiedName?.asString()
                javaType = kotlinTypes[qualifier]
            }

            if (javaType == null) {
                throw IllegalArgumentException("Unsupported parameter/return type: $type")
            }

            val isNullable = type.nullability == NULLABLE
            val isPrimitive = qualifier in primitives
            if (isPrimitive && isNullable) {
                throw IllegalArgumentException("Primitive type cannot be nullable: $type")
            }

            return JniActualInfo.Parameter(
                type = javaType,
                castExpression = castExpression[qualifier],
                originalObjectType = if (isPrimitive) null else type.toTypeName()
            )
        }

        override fun makeParameter(type: KSType): JniActualInfo.Parameter {
            return make(type, aliases, kotlinTypes, castParameter)
        }

        override fun makeReturnType(type: KSType): JniActualInfo.Parameter {
            return make(type, returnTypeAliases, returnTypeKotlin, castReturns)
        }

        override fun checkReceiver(type: KSType): Boolean? {
            return when ((type as? KSTypeAlias)?.qualifiedName?.asString()) {
                "io.github.mimimishkin.jni.JClass" -> true
                "io.github.mimimishkin.jni.JObject" -> false
                else -> throw IllegalArgumentException("Unsupported receiver type for @JniActual function: $type")
            }
        }
    }

    /**
     * Functions annotated with `@JniActual` and `@JniOnLoad`/`@JniOnUnload` must have the correct receiver, parameters
     * and return type:
     * - Primitive types must be one of `UByte`/`Boolean`, `Byte`, `UShort`/`Char`, `Short`, `Int`, `Long`, `Float`,
     *   `Double`.
     * - Reference types must be `CPointer` of type with name `_jobject`.
     * - JNI environment parameter must be `CPointerVar` of type with name `JNINativeInterface_`.
     * - Java VM parameter must be `CPointerVar` of type with name `JNIInvokeInterface_`.
     *
     * Use this in tandem with your own Cinterop configuration.
     */
    public data object CorrectlyNamed : SignatureTypes() {
        internal val primitives: Map<String, JavaType> = mapOf(
            "kotlin.UByte" to JavaType.Boolean,
            "kotlin.Boolean" to JavaType.Boolean,
            "kotlin.Byte" to JavaType.Byte,
            "kotlin.UShort" to JavaType.Char,
            "kotlin.Char" to JavaType.Char,
            "kotlin.Short" to JavaType.Short,
            "kotlin.Int" to JavaType.Int,
            "kotlin.Long" to JavaType.Long,
            "kotlin.Float" to JavaType.Float,
            "kotlin.Double" to JavaType.Double,
        )

        internal val returnTypes: Map<String, JavaType> = primitives + mapOf("kotlin.Unit" to JavaType.Void)

        internal fun make(
            type: KSType,
            primitives: Map<String, JavaType>,
            castExpressions: Map<String, String>,
            checkObject: Boolean,
        ): JniActualInfo.Parameter {
            val clazz = (type.declaration as? KSTypeAlias)?.findActualType() ?: type.declaration as KSClassDeclaration
            val qualifier = clazz.qualifiedName?.asString()
            val isNullable = type.nullability == NULLABLE
            if (qualifier == "kotlinx.cinterop.CPointer") {
                fun checkObject(): Boolean {
                    val typeArgument = type.arguments.singleOrNull()?.type?.resolve(resolveAliases = true)?.declaration
                    return typeArgument?.qualifiedName?.asString()?.endsWith("_jobject") == true
                }

                if (!checkObject || checkObject()) {
                    return JniActualInfo.Parameter(
                        type = JavaType.Object,
                        originalObjectType = type.toTypeName()
                    )
                }
            }

            val javaType = primitives[qualifier]
                ?: throw IllegalArgumentException("Unsupported parameter/return type: $type")

            if (isNullable) {
                throw IllegalArgumentException("Primitive type cannot be nullable: $type")
            }

            return JniActualInfo.Parameter(
                type = javaType,
                castExpression = castExpressions[qualifier],
            )
        }

        override fun makeParameter(type: KSType): JniActualInfo.Parameter {
            return make(type, primitives, castParameter, true)
        }

        override fun makeReturnType(type: KSType): JniActualInfo.Parameter {
            return make(type, returnTypes, castReturns, true)
        }

        override fun checkReceiver(type: KSType): Boolean? {
            val clazz = (type.declaration as? KSTypeAlias)?.findActualType() ?: (type.declaration as KSClassDeclaration)
            if (clazz.qualifiedName?.asString() == "kotlinx.cinterop.CPointer") {
                val typeArgument = type.arguments.singleOrNull()?.type?.resolve(resolveAliases = true)?.declaration
                val isObject = typeArgument?.qualifiedName?.asString()?.endsWith("_jobject")
                if (isObject == true) return null
            }

            throw IllegalArgumentException("Unsupported receiver type for @JniActual function: $type")
        }
    }

    /**
     * The same as [CorrectlyNamed] but reference types must be just `CPointer` (of any type).
     *
     * Use this if you want to disable signature type checks.
     */
    public data object Generic : SignatureTypes() {
        override fun makeParameter(type: KSType): JniActualInfo.Parameter {
            return CorrectlyNamed.make(type, CorrectlyNamed.primitives, castParameter, false)
        }

        override fun makeReturnType(type: KSType): JniActualInfo.Parameter {
            return CorrectlyNamed.make(type, CorrectlyNamed.returnTypes, castReturns, false)
        }

        override fun checkReceiver(type: KSType): Boolean? {
            val clazz = (type.declaration as? KSTypeAlias)?.findActualType() ?: (type.declaration as KSClassDeclaration)
            if (clazz.qualifiedName?.asString() == "kotlinx.cinterop.CPointer") {
                return null
            }

            throw IllegalArgumentException("Unsupported receiver type for @JniActual function: $type")
        }
    }

    internal abstract fun makeParameter(type: KSType): JniActualInfo.Parameter

    internal abstract fun makeReturnType(type: KSType): JniActualInfo.Parameter

    internal abstract fun checkReceiver(type: KSType): Boolean?
}