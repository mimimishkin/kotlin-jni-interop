package io.github.mimimishkin.jni.binding.producer

import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.TypeName

internal data class JniActualInfo(
    override val source: KSFile,
    val method: MemberName,
    val parameters: List<Parameter>,
    val returns: Parameter,
    val needEnv: Boolean,
    val needObjOrCls: Boolean,
    val isStatic: Boolean?,
    val jvmClass: ClassName,
    val jvmMethod: String,
    val jvmSignature: JvmSignature,
): WithSource {
    sealed class Parameter(
        open val type: TypeName,
        val castExpression: String = "",
    ) {
        data object BooleanIn : Parameter(ClassName("kotlin", "UByte"), ".let { it != 0.toUByte() }")
        data object BooleanOut : Parameter(ClassName("kotlin", "UByte"), ".let { if (it) 1u else 0u }")
        data object UByte : Parameter(ClassName("kotlin", "UByte"))
        data object Byte : Parameter(ClassName("kotlin", "Byte"))
        data object CharIn : Parameter(ClassName("kotlin", "UShort"), ".let { Char(it) }")
        data object CharOut : Parameter(ClassName("kotlin", "UShort"), ".code.toUShort()")
        data object UShort : Parameter(ClassName("kotlin", "UShort"))
        data object Short : Parameter(ClassName("kotlin", "Short"))
        data object Int : Parameter(ClassName("kotlin", "Int"))
        data object Long : Parameter(ClassName("kotlin", "Long"))
        data object Float : Parameter(ClassName("kotlin", "Float"))
        data object Double : Parameter(ClassName("kotlin", "Double"))
        data object Unit : Parameter(ClassName("kotlin", "Unit"))
        data class Ref(override val type: TypeName) : Parameter(type)

        val isPrimitive: Boolean get() = this !is Ref
    }

    data class JvmSignature(
        val parameters: List<String>,
        val returnType: String,
    ) {
        companion object {
            tailrec fun mapType(type: String, dimension: Int = 0): String {
                if (type.endsWith("[]")) return mapType(type.dropLast(2), dimension + 1)
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
        }

        override fun toString(): String {
            val parameters = parameters.joinToString("") { mapType(it) }
            val returnType = mapType(returnType)
            return "($parameters)$returnType"
        }
    }

    init {
        infix fun Parameter.matches(jvmParameter: String): Boolean {
            return when (jvmParameter) {
                "boolean" -> this in listOf(Parameter.BooleanIn, Parameter.BooleanOut, Parameter.UByte)
                "byte" -> this == Parameter.Byte
                "char" -> this in listOf(Parameter.CharIn, Parameter.CharOut, Parameter.UShort)
                "short" -> this == Parameter.Short
                "int" -> this == Parameter.Int
                "long" -> this == Parameter.Long
                "float" -> this == Parameter.Float
                "double" -> this == Parameter.Double
                "void" -> this == Parameter.Unit
                else -> this is Parameter.Ref
            }
        }

        require(parameters.size == jvmSignature.parameters.size) {
            "Number of parameters in @WithJvmSignature does not match the number of parameters in the function"
        }

        parameters.zip(jvmSignature.parameters).forEach { (parameter, jvmParameter) ->
            require(parameter matches jvmParameter) {
                "Parameter types in @WithJvmSignature do not match the types of the function parameters: " +
                        "expected '${jvmParameter}', but '${parameter.type}' was found"
            }
        }

        require(returns matches jvmSignature.returnType) {
            error("Return type in @WithJvmSignature does not match the return type of the function: expected " +
                    "'${jvmSignature.returnType}', but '${returns.type}' was found")
        }
    }
}