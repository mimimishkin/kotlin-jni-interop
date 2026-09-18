package io.github.mimimishkin.jni.binding.producer

import io.github.mimimishkin.jni.binding.producer.model.ActualParameterType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/** Builds an [FqName] from a dot-separated package string, e.g. `"kotlinx.cinterop".pkg()`. */
internal fun String.pkg() = FqName(this)

/** Builds an identifier [Name] from a string. */
internal fun String.ident() = Name.identifier(this)

/** Builds a top-level [ClassId] under this package. */
internal fun FqName.classId(name: Name) = ClassId(this, name)
internal fun FqName.classId(name: String) = ClassId(this, name.ident())

/** Builds a top-level [CallableId]. */
internal fun FqName.callableId(name: Name) = CallableId(this, name)
internal fun FqName.callableId(name: String) = CallableId(this, name.ident())

/** Builds a member [CallableId] on this class. */
internal fun ClassId.callableId(name: Name) = CallableId(this, name)
internal fun ClassId.callableId(name: String) = CallableId(this, name.ident())

/**
 * Converts the JNI function info into C symbol name the JVM can resolve.
 *
 * @return name in `Java_<encoded class>_<encoded method>[__<encoded parameters>]` format.
 */
internal fun jniCName(
    jvmClass: String,
    method: String,
    parameters: List<ActualParameterType> = emptyList(),
): String {
    val clazz = jvmClass.split('.').joinToString("_") { it.escapeSignature() }
    val method = method.escapeSignature()
    if (parameters.isNotEmpty()) {
        val parameters = ActualParameterType.mapSignature(parameters, returnType = null).escapeSignature()
        return "Java_${clazz}_${method}__$parameters"
    } else {
        return "Java_${clazz}_${method}"
    }
}

/**
 * Escapes a string for use inside a JNI C symbol name, following the JNI spec's `mangling` rules:
 * `/` becomes `_`, `_`/`;`/`[` become `_1`/`_2`/`_3`, and every other non-alphanumeric character
 * becomes `_0xxxx` (its UTF-16 code unit padded to four hex digits).
 */
internal fun String.escapeSignature() = asIterable().joinToString("") { c ->
    when (c) {
        '/' -> "_"
        in 'A'..'Z', in 'a'..'z', in '0'..'9' -> c.toString()
        '_' -> "_1"
        ';' -> "_2"
        '[' -> "_3"
        else -> "_0${c.code.toString(radix = 16).padStart(4, '0')}"
    }
}