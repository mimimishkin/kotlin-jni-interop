package org.sample

import io.github.mimimishkin.jni.binding.annotation.JniExpect
import io.github.mimimishkin.jni.binding.annotation.JniExpects
import io.github.mimimishkin.jni.binding.annotation.LoadMethod
import java.io.PrintStream
import java.lang.System.mapLibraryName
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.absolutePathString

// The consumer plugin will check that every @JniExpect has its @JniActual pair in the provided native libraries.
// Each platform's library has its own actuals file (see build.gradle.kts of the native module), so the JVM side just
// declares the external functions once and the plugin matches them per target.
//
// Pairing is done by the full JVM class name and the external function's name. A top-level function lives in the file
// facade class, so its `className` is `org.sample.MainKt`.
//
// On the native side a @JniActual may have one of three shapes: no receiver, a `JClass` receiver, or a
// `context(JniEnv)` parameter (see `samples/basic/native/src/nativeMain/kotlin/Main.kt`). Here, since the function has
// no receiver to read an instance from, the implementation only needs the environment and returns `JString?`.
@JniExpect
external fun outerFun(): String

// Instead of repeating @JniExpect on every member we apply @JniExpects to the whole object.
// Each `external` function and property inside will be treated as if it was annotated with @JniExpect.
@JniExpects
@Suppress("NonASCIICharacters", "ClassName")
object Главный {
    // @LoadMethod marks a function that loads the native library. In an object annotated with @JniExpects it is called
    // in the object's <init>, so the library is loaded as soon as `Главный` is accessed.
    //
    // The `os` and `arch` parameters are optional. When present, `os` receives the OS family ("windows", "linux" or
    // "macos") and `arch` receives the normalized architecture ("x86", "x86_64", "aarch32", "aarch64", "riscv32",
    // "riscv64"). There is also a
    // `vendor` parameter receiving `System.getProperty("java.vendor")` unchanged.
    @Suppress("UnsafeDynamicallyLoadedCode")
    @LoadMethod
    private fun load(os: String, arch: String) {
        val libPath = "/natives/$os-$arch/${mapLibraryName("native")}"
        val libStream = Главный::class.java.getResourceAsStream(libPath)
            ?: error("No native library found at $libPath")
        val outputPath = Files.createTempFile(null, libPath.substringAfterLast('/'))
        libStream.use { input ->
            Files.copy(input, outputPath, StandardCopyOption.REPLACE_EXISTING)
        }
        outputPath.toFile().deleteOnExit()
        System.load(outputPath.absolutePathString())
    }

    // Note that @JniExpects is not applied to nested classes, so we need to annotate this one explicitly.
    // On the native side a nested class needs a separate top-level binding with the JVM nested class name, i.e.
    // `@JniActuals(className = $$"org.sample.Главный$Nested")`.
    @JniExpects
    class Nested {
        external fun sayHello(printStream: PrintStream)
    }

    // Overloads with the same name are handled.
    external infix fun Int.add(second: Int): Int
    external infix fun Float.add(second: Float): Float

    // Expect a parameter to be nullable because this function can be called from an environment that does not check
    // nullability (e.g. from Java code or reflection).
    external fun ByteArray.sumArray(): Long

    // `Array<Float>` is mapped to the JVM array type `java.lang.Float[]`. The native side must declare
    // the same type, e.g. via the `JFloatRefArray` type alias used in the native sample, so that the JNI signature and
    // the JVM signature match.
    external fun Array<Float>.sumArray2(): Long

    // The native side accesses this field (note that it's static after compilation) through JNI reflection and the
    // value can be both read and written even though the field is `private` and `final`.
    private val privateFinalField: String = "Old"

    external fun editPrivateFinalField(newValue: String)

    fun getPrivateFinalField(): String = privateFinalField
}