package io.github.mimimishkin.jni.binding.annotation

/**
 * Annotates a function that loads the native library:
 * - In a companion object of a class. It will be called in the parent class's `<clinit>`.
 * - In an `object`. It will be called in the object's `<init>`.
 *
 * It may optionally have the following String parameters: `os`, `arch`, and `vendor` of a `String` type:
 * - If the `os: String` parameter is present, the OS family will be passed in, or `System.getProperty("os.name")` if
 *   this cannot be determined. These values can be `windows`, `linux`, or `macos`. Other OS families will not be
 *   determined.
 * - If the `arch: String` parameter is present, the normalized `System.getProperty("os.arch")` will be passed in. That
 *   is, instead of something from "x86", "i386", "ia-32", "i686", "x86-64", "x86_64", "amd64", "x64", "arm-v7",
 *   "armv7", "arm", "arm32", "aarch64", "arm-v8", "arm64", "riscv32", "rv32", "riscv64", "rv64" the list is shortened
 *   to "x86", "x86_64", "aarch32", "aarch64", "riscv32", "riscv64".
 *   If arch is not in the list above, it is passed as is.
 * - If the `vendor: String` parameter is present, `System.getProperty("java.vendor")` will be passed to it unchanged.
 *
 * For example:
 * ```kotlin
 * class MyClass {
 *     companion object {
 *         @LoadMethod
 *         private fun load(os: String, arch: String) {
 *             val from = "natives/$os-$arch/${mapLibraryName("native")}"
 *             val libStream = MyClass::class.java.classLoader.getResourceAsStream(from)
 *             val lib = Files.createTempFile("lib", from.substringAfter('/')).toFile()
 *             libStream.use { input -> lib.outputStream().use { output ->
 *                 input.copyTo(output)
 *             } }
 *             lib.deleteOnExit()
 *             System.load(lib.absolutePath)
 *         }
 *     }
 * }
 * ```
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class LoadMethod