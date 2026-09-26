package org.sample

import io.github.mimimishkin.jni.binding.annotation.JniExpect
import java.lang.System.mapLibraryName
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.absolutePathString

// Bindings from the `nativeHello` producer module. Unlike `native`, every platform of that module exports a DIFFERENT
// function, so each expect below is restricted to its own target with the `targets` parameter of `@JniExpect` (an
// empty list, the default, would require a matching actual on every target). Being top-level functions, they live in
// the `org.sample.ImplByTargetsKt` facade class.
@JniExpect("mingwX64")
external fun windowsHello(): String

@JniExpect("linuxX64")
external fun linuxX64Hello(): String

@JniExpect("linuxArm64")
external fun linuxArm64Hello(): String

// Top-level external functions have no `@JniExpects` container to inject a `@LoadMethod` into, so the "hello" library
// is loaded manually before the functions above are called (see [JniBindingTest]).
object HelloNative {
    @Suppress("UnsafeDynamicallyLoadedCode")
    fun load() {
        val libPath = "/natives/$os-$arch/${mapLibraryName("hello")}"
        val libStream = HelloNative::class.java.getResourceAsStream(libPath)
            ?: error("No native library found at $libPath")
        val outputPath = Files.createTempFile(null, libPath.substringAfterLast('/'))
        libStream.use { input ->
            Files.copy(input, outputPath, StandardCopyOption.REPLACE_EXISTING)
        }
        outputPath.toFile().deleteOnExit()
        System.load(outputPath.absolutePathString())
    }

    private val os: String = when {
        System.getProperty("os.name").lowercase() in listOf("windows", "win32") -> "windows"
        "mac" in System.getProperty("os.name").lowercase() -> "macos"
        else -> "linux"
    }

    private val arch: String = when (System.getProperty("os.arch").lowercase()) {
        in listOf("x86", "i386", "i686") -> "x86"
        in listOf("amd64", "x86_64", "x64") -> "x86_64"
        in listOf("aarch64", "arm64") -> "aarch64"
        else -> error("Unsupported architecture: ${System.getProperty("os.arch")}")
    }
}