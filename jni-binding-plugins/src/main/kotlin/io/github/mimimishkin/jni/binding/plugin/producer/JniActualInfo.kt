package io.github.mimimishkin.jni.binding.plugin.producer

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName
import io.github.mimimishkin.jni.binding.plugin.JavaType

internal data class JniActualInfo(
    val container: ClassName?,
    val methodQualifier: String,
    val parameters: List<Parameter>,
    val returns: Parameter,
    val isStatic: Boolean?,
    val jvmClass: ClassName,
    val jvmMethod: String,
    val objOrClsType: TypeName?,
    val envType: TypeName?,
) {
    data class Parameter(
        val type: JavaType,
        val castExpression: String? = null,
        val originalObjectType: TypeName? = null,
    )
}