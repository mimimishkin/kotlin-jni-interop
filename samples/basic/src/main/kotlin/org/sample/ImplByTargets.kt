package org.sample

import io.github.mimimishkin.jni.binding.annotation.JniExpect
import io.github.mimimishkin.jni.binding.annotation.LoadMethod
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

@JniExpect("macosArm64")
external fun macosArm64Hello(): String

// The functions above are top-level, so they are not members of a `@JniExpects` class/object and there is no
// container to inject a load call into. A top-level `@LoadMethod` needs none: the plugin adds a static initializer to
// this file's facade class (`org.sample.ImplByTargetsKt`), which runs on the first call of any of the functions
// above, so simply using this file is enough to load the library.
@Suppress("UnsafeDynamicallyLoadedCode")
@LoadMethod
private fun loadHello(os: String, arch: String) {
    val libPath = "/natives/$os-$arch/${mapLibraryName("hello")}"
    val libStream = object {}.javaClass.getResourceAsStream(libPath)
        ?: error("No native library found at $libPath")
    val outputPath = Files.createTempFile(null, libPath.substringAfterLast('/'))
    libStream.use { input ->
        Files.copy(input, outputPath, StandardCopyOption.REPLACE_EXISTING)
    }
    outputPath.toFile().deleteOnExit()
    System.load(outputPath.absolutePathString())
}