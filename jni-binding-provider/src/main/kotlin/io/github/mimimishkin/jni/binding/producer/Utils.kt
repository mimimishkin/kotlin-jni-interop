package io.github.mimimishkin.jni.binding.producer

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeReference
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

public fun ClassName.Companion.guess(fullName: String): ClassName {
    val packageName = fullName.substringBeforeLast('.', "")
    val simpleNames = fullName.substringAfterLast('.').split('$')
    return ClassName(packageName, simpleNames)
}

public object ClassNameAsStringSerializer : KSerializer<ClassName> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("io.github.mimimishkin.jni.binding.producer.ClassNameAsStringSerializer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ClassName) {
        encoder.encodeString(value.reflectionName())
    }

    override fun deserialize(decoder: Decoder): ClassName {
        return ClassName.guess(decoder.decodeString())
    }
}

public fun TypeName.Companion.guess(fullName: String): TypeName {
    if (fullName == "*") return STAR
    
    var name = fullName
    val variance = when {
        name.startsWith("in ") -> {
            name = name.removePrefix("in").trimStart()
            "in"
        }
        name.startsWith("out ") -> {
            name = name.removePrefix("out").trimStart()
            "out"
        }
        else -> null
    }
    
    val isNullable = name.endsWith('?')
    name = name.removeSuffix("?")
    
    val className = ClassName.guess(name.substringBefore('<'))

    val generics = if ('<' !in name) emptyList() else buildList {
        val names = name.substringAfter('<').substringAfterLast('>')
        var depth = 0
        var start = 0
        for (i in names.indices) {
            when (names[i]) {
                '<' -> depth++
                '>' -> depth--
                ',' if depth == 0 -> {
                    this += guess(names.substring(start, i).trim())
                    start = i + 1
                }
            }
        }
        this += guess(names.substring(start).trim())
    }

    val type = className.parameterizedBy(generics).copy(isNullable)
    return when (variance) {
        "in" -> WildcardTypeName.consumerOf(type)
        "out" -> WildcardTypeName.producerOf(type)
        else -> type
    }
}

public fun TypeName.qualifiedName(): String {
    return when (this) {
        is ClassName -> reflectionName() + if (isNullable) "?" else ""
        is ParameterizedTypeName -> buildString {
            append(rawType.reflectionName())
            if (typeArguments.isNotEmpty()) {
                append('<')
                append(typeArguments.joinToString(", ") { it.qualifiedName() })
                append('>')
            }
            if (isNullable) append('?')
        }
        is WildcardTypeName -> when {
            this == STAR -> "*"
            inTypes.isNotEmpty() -> "in ${inTypes[0].qualifiedName()}"
            else -> "out ${outTypes[0].qualifiedName()}"
        }
        else -> throw IllegalArgumentException("Unsupported type: $this")
    }
}

public object TypeNameAsStringSerializer : KSerializer<TypeName> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("io.github.mimimishkin.jni.binding.producer.TypeNameAsStringSerializer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: TypeName) {
        encoder.encodeString(value.qualifiedName())
    }

    override fun deserialize(decoder: Decoder): TypeName {
        return TypeName.guess(decoder.decodeString())
    }
}

public object MemberNameAsStringSerializer : KSerializer<MemberName> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("io.github.mimimishkin.jni.binding.producer.MemberNameAsStringSerializer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: MemberName) {
        encoder.encodeString(value.canonicalName)
    }

    override fun deserialize(decoder: Decoder): MemberName {
        val name = decoder.decodeString()
        return MemberName(name.substringBeforeLast('.', ""), name.substringAfterLast('.'))
    }
}

internal tailrec fun KSTypeAlias.findActual(): KSType {
    val resolvedType = type.resolve()
    val alias = resolvedType.declaration as? KSTypeAlias
    return alias?.findActual() ?: resolvedType
}

internal fun KSType.findActual(): KSType {
    return (declaration as? KSTypeAlias)?.findActual() ?: this
}

internal fun KSTypeReference.resolveActual(): KSType {
    return resolve().findActual()
}

internal fun KSAnnotation.isAnnotation(paket: String, simpleName: String): Boolean {
    return shortName.asString() == simpleName &&
            annotationType.resolve().declaration.qualifiedName?.asString() == "$paket.$simpleName"
}

internal fun CodeBlock.Builder.controlFlow(controlFlow: String, vararg args: Any?, block: CodeBlock.Builder.() -> Unit) {
    beginControlFlow(controlFlow, *args)
    block()
    endControlFlow()
}

internal fun FunSpec.Builder.controlFlow(controlFlow: String, vararg args: Any, block: FunSpec.Builder.() -> Unit) {
    beginControlFlow(controlFlow, *args)
    block()
    endControlFlow()
}

internal fun CodeBlock.Companion.of(
    format: String,
    namedArgs: Map<String, Any?>,
): CodeBlock = buildCodeBlock {
    addNamed(format, namedArgs)
}

internal fun FunSpec.Builder.addStatement(
    format: String,
    namedArgs: Map<String, Any?>,
): FunSpec.Builder {
    addCode("«")
    addNamedCode(format, namedArgs)
    addCode("\n»")
    return this
}

