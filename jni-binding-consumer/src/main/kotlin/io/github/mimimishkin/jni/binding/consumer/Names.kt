package io.github.mimimishkin.jni.binding.consumer

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal fun String.pkg() = FqName(this)

internal fun String.ident() = Name.identifier(this)

internal fun FqName.classId(name: String) = ClassId(this, name.ident())

internal fun ClassId.callableId(name: String) = CallableId(this, name.ident())