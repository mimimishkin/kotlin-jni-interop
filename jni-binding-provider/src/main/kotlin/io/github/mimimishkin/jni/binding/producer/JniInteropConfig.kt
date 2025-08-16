@file:UseSerializers(
    ClassNameAsStringSerializer::class,
    TypeNameAsStringSerializer::class,
    MemberNameAsStringSerializer::class
)

package io.github.mimimishkin.jni.binding.producer

import com.google.devtools.ksp.findActualType
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.Nullability.NULLABLE
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.github.mimimishkin.jni.binding.producer.GenerateBindingProcessor.Companion.paket
import io.github.mimimishkin.jni.binding.producer.JniActualInfo.Parameter
import io.github.mimimishkin.jni.binding.producer.JniInteropConfig.Companion.FromJniBinding
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/**
 * Information about configured interop with JNI. Every function annotated with `@JniActual`
 * should have signature with classes defined below.
 *
 * @property envType Type of `env` parameter. For example, `io.github.mimimishkin.jni.binding.JniEnv`.
 * @property vmType Type of `vm` parameter. For example, `io.github.mimishkin.jni.binding.JavaVM`.
 * @property objType Type of `obj` parameter. For example, `io.github.mimimishkin.jni.binding.JObject`.
 * @property clsType Type of `cls` parameter. For example, `io.github.mimimishkin.jni.binding.JClass`.
 * @property forOnLoad Information required if binding on load mode was selected.
 *
 * @see FromJniBinding
 */
@Serializable
public data class JniInteropConfig(
    val envType: TypeName,
    val vmType: TypeName,
    val objType: TypeName,
    val clsType: TypeName,

    val forOnLoad: ForOnLoadInfo? = null,
) {
    /**
     * @property nativeMethodType Type of `JNINativeMethod` structure. For example,
     *           `io.github.mimimishkin.jni.binding.JniNativeMethod`.
     * @property getEnv Member name of `GetEnv` function. For example, `io.github.mimimishkin.jni.binding.GetEnv`.
     *           This name will be imported to be used in [expressionGetEnv] as `%getEnv:M`.
     * @property expressionGetEnv Kotlin Poet expression to get `env` pointer from `vm` pointer. `vm` of type [vmType]
     *           and `jniVersion` of type [Int] parameters are available. For example, `%vm:L.%getEnv:M(%jniVersion:L)"`.
     * @property findClass Member name of `FindClass` function. For example,
     *           `io.github.mimimishkin.jni.binding.FindClass`. This name will be imported to be used in
     *           [expressionFindClass] as `%findClass:M`.
     * @property expressionFindClass Expression to find class by name. `env` of type [envType] and `className` of type
     *           `CPointer<ByteVar>` parameters are available. Also, code will be executed with the context parameter of
     *           type [envType]. For example, `"%findClass:M(%className:L)"`.
     * @property registerNatives Member name of `RegisterNatives` function. For example,
     *           `io.github.mimimishkin.jni.binding.RegisterNatives`. This name will be imported to be used in
     *           [expressionRegisterNatives] as `%registerNatives:M`..
     * @property expressionRegisterNatives Expression to register native methods. `env` of type [envType], `clazz` of
     *           type [clsType], `natives` of type `CArrayPointer<JNINativeMethod>` and `nativesCount` of type [Int]
     *           parameters are available. Also, code will be executed with the context parameter of type [envType].
     *           For example, `%registerNatives:M(%class:L, %natives:L, %nativesCount:L)`.
     */
    @Serializable
    public data class ForOnLoadInfo(
        public val nativeMethodType: ClassName,
        public val getEnv: MemberName,
        public val expressionGetEnv: String,
        public val findClass: MemberName,
        public val expressionFindClass: String,
        public val registerNatives: MemberName,
        public val expressionRegisterNatives: String,
    )

    public companion object {
        /**
         * Preconfigured [JniInteropConfig] for jni binding library.
         */
        public val FromJniBinding: JniInteropConfig = JniInteropConfig(
            envType = ClassName(paket, "JniEnv"),
            vmType = ClassName(paket, "JavaVM"),
            objType = ClassName(paket, "JObject"),
            clsType = ClassName(paket, "JClass"),
            forOnLoad = ForOnLoadInfo(
                nativeMethodType = ClassName(paket, "JniNativeMethod"),
                getEnv = MemberName(paket, "GetEnv"),
                expressionGetEnv = "%vm:L.%getEnv:M(%jniVersion:L)",
                findClass = MemberName(paket, "FindClass"),
                expressionFindClass = "%findClass:M(%className:L)",
                registerNatives = MemberName(paket, "RegisterNatives"),
                expressionRegisterNatives = "%registerNatives:M(%class:L, %natives:L, %nativesCount:L)"
            ),
        )
    }

    /**
     * Whether `objType` is different from `clsType`.
     */
    val hasDistinctClassType: Boolean
        get() = objType != clsType

    internal fun makeType(type: KSType, isReturnType: Boolean): Parameter {
        val actualType = (type.declaration as? KSTypeAlias)?.findActualType() ?: type.declaration as KSClassDeclaration
        val qualifier = actualType.qualifiedName!!.asString()
        if (qualifier == "kotlinx.cinterop.CPointer") {
            return Parameter.Ref(type.toTypeName())
        }

        val parameter = when (qualifier) {
            "kotlin.Boolean" -> if (isReturnType) Parameter.BooleanOut else Parameter.BooleanIn
            "kotlin.UByte" -> Parameter.UByte
            "kotlin.Byte" -> Parameter.Byte
            "kotlin.Char" -> if (isReturnType) Parameter.CharOut else Parameter.CharIn
            "kotlin.UShort" -> Parameter.UShort
            "kotlin.Short" -> Parameter.Short
            "kotlin.Int" -> Parameter.Int
            "kotlin.Long" -> Parameter.Long
            "kotlin.Float" -> Parameter.Float
            "kotlin.Double" -> Parameter.Double
            "kotlin.Unit" if isReturnType -> Parameter.Unit
            else -> error("Unsupported parameter/return type: $actualType")
        }

        require(type.nullability != NULLABLE) { "Primitive parameter/return type cannot be nullable: $type" }

        return parameter
    }

    internal fun checkEnv(type: KSType) {
        require(type.toTypeName() == envType || type.findActual().toTypeName() == envType) {
            "env parameter must be of type $envType, but was $type"
        }
    }

    internal fun checkObjOrCls(receiver: KSType?): Pair<TypeName?, Boolean?> {
        if (receiver == null) return null to null
        val type = receiver.toTypeName()

        val isStatic = when (type) {
            objType -> false
            clsType -> true
            else -> when (receiver.findActual().toTypeName()) {
                objType -> false
                clsType -> true
                else -> {
                    val mustTypes = if (hasDistinctClassType) "either $objType or $clsType" else objType.toString()
                    error("Extension receiver type must be $mustTypes, but was $type")
                }
            }
        }

        return type to isStatic
    }

    internal fun checkVm(type: KSType) {
        require(type.toTypeName() == vmType || type.findActual().toTypeName() == vmType) {
            "vm parameter must be of type $vmType, but was $type"
        }
    }
}
