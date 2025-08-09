package io.github.mimimishkin.jni.binding.plugin.producer

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.joinToCode
import io.github.mimimishkin.jni.binding.plugin.JavaType

internal object BindingGenerator {
    private val CName = ClassName("kotlin.native", "CName")

    private fun CName(externName: String): AnnotationSpec =
        AnnotationSpec.builder(CName).addMember("%S", externName).build()

    private val COpaquePointerType = ClassName("kotlinx.cinterop", "COpaquePointer")

    private fun JniActualInfo.Parameter.toTypeAndCast(): Pair<TypeName, String> {
        val clazz = when (type) {
            JavaType.Boolean -> UByte::class
            JavaType.Byte -> Byte::class
            JavaType.Char -> UShort::class
            JavaType.Short -> Short::class
            JavaType.Int -> Int::class
            JavaType.Long -> Long::class
            JavaType.Float -> Float::class
            JavaType.Double -> Double::class
            JavaType.Void -> Unit::class
            else -> return originalObjectType!! to castExpression.orEmpty()
        }

        return clazz.asClassName() to castExpression.orEmpty()
    }

    fun ClassName.mapNativeFunName(method: String): String {
        fun String.escape(): String = asIterable().joinToString("") { c ->
            when {
                c == '_' -> "_1"
                c == ';' -> "_2"
                c == '[' -> "_3"
                c.code > 127 -> "_0${c.code.toString(16).padStart(4, '0').lowercase()}"
                else -> c.toString()
            }
        }

        val paket = packageName.escape().replace('.', '_')
        val clazz = simpleNames.joinToString("$").escape()
        val method = method.escape()
        return "Java_" + paket + "_" + clazz + "_" + method
    }

    fun jniFunction(info: JniActualInfo): FunSpec {
        val funName = info.methodQualifier.substringAfterLast('.') + "JniBinding"
        return FunSpec.builder(funName).apply {
            addAnnotation(CName(externName = info.jvmClass.mapNativeFunName(info.jvmMethod)))
            addModifiers(PUBLIC)

            val envName = "env"
            addParameter(envName, info.envType ?: COpaquePointerType)

            val objOrClsName = "objOrCls"
            addParameter(objOrClsName, info.objOrClsType ?: COpaquePointerType)

            val parameters = info.parameters.mapIndexed { i, parameter ->
                val parameterName = "param$i"
                val (parameterType, castExpression) = parameter.toTypeAndCast()

                addParameter(parameterName, parameterType)

                parameterName to castExpression
            }

            val returnsCastExpression = info.returns.toTypeAndCast().let { (type, castExpression) ->
                returns(type)
                castExpression
            }

            val invokeExpression = buildCodeBlock {
                if (info.container != null) {
                    beginControlFlow("with(%T)", info.container)
                }

                if (info.envType != null) {
                    beginControlFlow("with(%L)", envName)
                }

                val receiverNotation = if (info.objOrClsType == null) "" else "%1L."
                val invokeNotation = "%2L(%3L)"
                val castNotation = "%4L"

                addStatement(
                    receiverNotation + invokeNotation + castNotation,
                    objOrClsName,
                    info.methodQualifier,
                    parameters.joinToString { (name, castExpression) -> name + castExpression },
                    returnsCastExpression,
                )

                if (info.envType != null) {
                    endControlFlow()
                }

                if (info.container != null) {
                    endControlFlow()
                }
            }

            addStatement("return %L", invokeExpression)
        }.build()
    }
}