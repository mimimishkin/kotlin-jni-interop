package io.github.mimimishkin.jni.binding.producer

import com.google.devtools.ksp.symbol.KSFile

internal interface WithSource {
    val source: KSFile
}