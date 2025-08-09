package io.github.mimimishkin.jni.binding.plugin

import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeReference
import com.squareup.kotlinpoet.ClassName

internal fun ClassName.Companion.guess(fullName: String): ClassName =
    ClassName(fullName.substringBeforeLast('.', ""), fullName.substringAfterLast('.'))

internal fun KSTypeReference.resolve(resolveAliases: Boolean): KSType {
    val type = resolve()
    if (!resolveAliases) return type
    val resolved = (type.declaration as? KSTypeAlias)?.type?.resolve(true)
    return resolved ?: type
}