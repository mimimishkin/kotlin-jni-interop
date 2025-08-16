package io.github.mimimishkin.jni.binding.producer

import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.MemberName

internal data class JniHookInfo(
    override val source: KSFile,
    val method: MemberName,
    val needVm: Boolean
) : WithSource