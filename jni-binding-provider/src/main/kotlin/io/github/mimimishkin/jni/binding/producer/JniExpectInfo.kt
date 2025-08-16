@file:UseSerializers(ClassNameAsStringSerializer::class)

package io.github.mimimishkin.jni.binding.producer

import com.squareup.kotlinpoet.ClassName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
public data class JniExpectInfo(
    val className: ClassName,
    val methodName: String,
    val isStatic: Boolean,
    val parameterTypes: List<String>,
    val returnType: String,
) {
    internal fun matches(actualInfo: JniActualInfo): Boolean {
        if (actualInfo.method.enclosingClassName != className) return false
        if (actualInfo.method.simpleName != methodName) return false
        if (actualInfo.parameters.size != parameterTypes.size) return false
        parameterTypes.zip(actualInfo.jvmSignature.parameters) { expected, actual ->
            if (expected != actual) return false
        }
        if (returnType != actualInfo.jvmSignature.returnType) return false
        if (actualInfo.isStatic != null && actualInfo.isStatic != isStatic) return false

        return true
    }

    override fun toString(): String {
        val clazz = className.reflectionName()
        val parameters = parameterTypes.joinToString(", ")
        val modifier = if (isStatic) "static " else ""
        return "$modifier$returnType $clazz.$methodName($parameters)"
    }
}