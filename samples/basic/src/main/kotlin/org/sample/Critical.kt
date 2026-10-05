package org.sample

import io.github.mimimishkin.jni.binding.annotation.CriticalNative
import io.github.mimimishkin.jni.binding.annotation.JniExpect
import io.github.mimimishkin.jni.binding.annotation.LoadMethod
import java.lang.System.mapLibraryName
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.absolutePathString

// Every function below is `@CriticalNative`.
// Note that such methods must be `static` (a top-level `external fun` compiles to a static JVM method, so these are
// fine) and must NOT be `synchronized`.

// Parameters can only be primitives or primitive arrays.
@CriticalNative
@JniExpect
external fun sum(values: IntArray): Long

// Return value can only be primitive.
@CriticalNative
@JniExpect
external fun dot(left: DoubleArray, right: DoubleArray): Double

@CriticalNative
@JniExpect
external fun isAllPositive(values: IntArray): Boolean

@CriticalNative
@JniExpect
external fun fillTwice(values: LongArray)

@Suppress("UnsafeDynamicallyLoadedCode")
@LoadMethod
private fun loadCritical(os: String, arch: String) {
    val libPath = "/natives/$os-$arch/${mapLibraryName("critical")}"
    val libStream = object {}.javaClass.getResourceAsStream(libPath)
        ?: error("No native library found at $libPath")
    val outputPath = Files.createTempFile(null, libPath.substringAfterLast('/'))
    libStream.use { input ->
        Files.copy(input, outputPath, StandardCopyOption.REPLACE_EXISTING)
    }
    outputPath.toFile().deleteOnExit()
    System.load(outputPath.absolutePathString())
}