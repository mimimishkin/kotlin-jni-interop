@file:OptIn(ExperimentalCompilerApi::class)

package io.github.mimimishkin.jni.binding.producer

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import io.github.mimimishkin.jni.binding.producer.model.JniActualInfo
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.File
import java.lang.reflect.Method
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * End-to-end tests of the producer plugin through the kotlin-compiler-test-framework.
 *
 * The plugin generates IR that references the native interop API (`JniEnv`, `JavaVM`, `JObject`, `COpaquePointer`,
 * `kotlin.native.CName`), which does not exist in a JVM compilation. The tests provide minimal stub declarations for
 * that API as part of the compiled module, mirroring how the real `jni-binding` library is a dependency in native
 * builds.
 */
class JniBindingProducerCompilationTest {

    private val stubSources = listOf(
        SourceFile.kotlin(
            "stubs/jniBinding.kt",
            """
            package io.github.mimimishkin.jni.binding

            import io.github.mimimishkin.jni.binding.annotation.WithJvmType
            import kotlinx.cinterop.CFunction
            import kotlinx.cinterop.CPointer
            import kotlinx.cinterop.CPointerVar
            import kotlinx.cinterop.AutofreeScope
            import kotlinx.cinterop.MemScope

            class _jobject
            class _jclass

            typealias JniEnv = CPointerVar<jni.JNINativeInterface_>
            typealias JavaVM = CPointerVar<jni.JNIInvokeInterface_>

            typealias JObject = @WithJvmType("java.lang.Object") CPointer<out _jobject>
            typealias JClass = @WithJvmType("java.lang.Class") CPointer<out _jclass>

            typealias JBoolean = UByte

            inline fun Boolean.toJBoolean(): JBoolean = if (this) 1u else 0u
            inline fun JBoolean.toKBoolean(): Boolean = this == 1u.toUByte()

            typealias JRef<T> = CPointer<out T>

            class JNINativeMethodRegistry

            class ByteBuf

            val String.modifiedUtf8: ByteBuf get() = TODO()

            fun JavaVM.useEnv(version: Int, block: context(MemScope, JniEnv) () -> Unit): Unit = TODO()

            context(env: JniEnv, autofreeScope: AutofreeScope)
            fun findClass(name: ByteBuf): JClass? = TODO()

            context(env: JniEnv, autofreeScope: AutofreeScope)
            fun JClass.registerNatives(count: Int, block: JNINativeMethodRegistry.() -> Unit): Unit = TODO()

            context(autofreeScope: AutofreeScope)
            fun JNINativeMethodRegistry.register(
                name: ByteBuf,
                signature: ByteBuf,
                functionPointer: JRef<CFunction<*>>,
            ): Unit = TODO()
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "stubs/jni.kt",
            """
            package jni

            class JNINativeInterface_
            class JNIInvokeInterface_
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "stubs/cinterop.kt",
            """
            package kotlinx.cinterop

            class CPointer<out T>
            class CPointerVarOf<T : CPointer<*>>

            typealias CPointerVar<T> = CPointerVarOf<CPointer<T>> 
            typealias COpaquePointer = CPointer<Nothing>

            open class AutofreeScope
            open class MemScope : AutofreeScope()

            class CFunction<out R : Function<*>>
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "stubs/cname.kt",
            """
            package kotlin.native

            @Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
            @Retention(AnnotationRetention.RUNTIME)
            annotation class CName(val externName: String, val shortName: String = "")
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "stubs/annotations.kt",
            """
            package io.github.mimimishkin.jni.binding.annotation

            @Target(AnnotationTarget.FUNCTION)
            @Retention(AnnotationRetention.SOURCE)
            annotation class JniActual(val className: String, val methodName: String = "")

            @Target(AnnotationTarget.CLASS)
            @Retention(AnnotationRetention.SOURCE)
            annotation class JniActuals(val className: String = "")

            @Target(AnnotationTarget.FUNCTION)
            @Retention(AnnotationRetention.SOURCE)
            annotation class JniOnLoad

            @Target(AnnotationTarget.FUNCTION)
            @Retention(AnnotationRetention.SOURCE)
            annotation class JniOnUnload

            @Target(AnnotationTarget.TYPE)
            @Retention(AnnotationRetention.BINARY)
            @Repeatable
            annotation class WithJvmType(val type: String)

            @Target(AnnotationTarget.FUNCTION)
            @Retention(AnnotationRetention.SOURCE)
            annotation class WithJvmSignature(vararg val parameterTypes: String, val returnType: String)
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "stubs/staticCFunction.kt",
            """
            package kotlinx.cinterop

            fun <R> staticCFunction(function: () -> R): CPointer<CFunction<() -> R>> = TODO()
            fun <P1, R> staticCFunction(function: (P1) -> R): CPointer<CFunction<(P1) -> R>> = TODO()
            fun <P1, P2, R> staticCFunction(function: (P1, P2) -> R): CPointer<CFunction<(P1, P2) -> R>> = TODO()
            fun <P1, P2, P3, R> staticCFunction(function: (P1, P2, P3) -> R): CPointer<CFunction<(P1, P2, P3) -> R>> = TODO()
            fun <P1, P2, P3, P4, R> staticCFunction(function: (P1, P2, P3, P4) -> R): CPointer<CFunction<(P1, P2, P3, P4) -> R>> = TODO()
            fun <P1, P2, P3, P4, P5, R> staticCFunction(function: (P1, P2, P3, P4, P5) -> R): CPointer<CFunction<(P1, P2, P3, P4, P5) -> R>> = TODO()
            fun <P1, P2, P3, P4, P5, P6, R> staticCFunction(function: (P1, P2, P3, P4, P5, P6) -> R): CPointer<CFunction<(P1, P2, P3, P4, P5, P6) -> R>> = TODO()
            """.trimIndent(),
        ),
    )

    private fun compile(vararg sources: SourceFile, jniVersion: Int? = null): KotlinCompilation =
        compileWithActualsFile(File.createTempFile("actuals", ".json"), *sources, jniVersion = jniVersion)

    private fun compile(
        vararg sources: SourceFile,
        jniVersion: Int? = null,
        allowSeveralHooks: Boolean = false,
        useRegisterNatives: Boolean = false,
    ): KotlinCompilation =
        compileWithActualsFile(
            File.createTempFile("actuals", ".json"),
            *sources,
            jniVersion = jniVersion,
            allowSeveralHooks = allowSeveralHooks,
            useRegisterNatives = useRegisterNatives,
        )

    private fun compileWithActualsFile(
        actualsFile: File,
        vararg sources: SourceFile,
        jniVersion: Int? = null,
        allowSeveralHooks: Boolean = false,
        useRegisterNatives: Boolean = false,
    ): KotlinCompilation =
        KotlinCompilation().apply {
            this.sources = stubSources + sources
            compilerPluginRegistrars = listOf(JniBindingProducerRegistrar())
            commandLineProcessors = listOf(JniBindingProducerCommandLineProcessor())
            pluginOptions = listOf(
                PluginOption("jni-binding-producer", "actualsFile", actualsFile.absolutePath),
                (if (jniVersion != null) {
                    PluginOption("jni-binding-producer", "jniVersion", jniVersion.toString())
                } else null),
                (if (allowSeveralHooks) {
                    PluginOption("jni-binding-producer", "allowSeveralHooks", "true")
                } else null),
                (if (useRegisterNatives) {
                    PluginOption("jni-binding-producer", "useRegisterNatives", "true")
                } else null),
            ).filterNotNull()
            inheritClassPath = true
            kotlincArguments = listOf("-Xallow-kotlin-package")
        }

    private fun cNameValue(method: Method): String {
        val annotation = method.annotations.first { it.annotationClass.simpleName == "CName" }
        return annotation.javaClass.getMethod("externName").invoke(annotation) as String
    }

    /** The facade method with the given name, wherever in the compiled output it was generated. */
    private fun JvmCompilationResult.getFacadeMethod(methodName: String): Method =
        this.getFacadeClass(methodName).declaredMethods.first { it.name.matchesFacade(methodName) }

    /** Whether any generated class declares a facade method with the given name. */
    private fun JvmCompilationResult.hasFacadeMethod(methodName: String): Boolean =
        compiledClassAndResourceFiles
            .asSequence()
            .filter { it.isFile && it.extension == "class" }
            .mapNotNull { classFile ->
                val className = classFile.relativeTo(outputDirectory).path
                    .removeSuffix(".class")
                    .replace(File.separatorChar, '.')
                runCatching { Class.forName(className, false, this@hasFacadeMethod.classLoader) }.getOrNull()
            }
            .any { it.declaredMethods.any { method -> method.name.matchesFacade(methodName) } }

    /** The generated class containing the given facade method. */
    private fun JvmCompilationResult.getFacadeClass(methodName: String): Class<*> {
        val clazz = compiledClassAndResourceFiles
            .asSequence()
            .filter { it.isFile && it.extension == "class" }
            .mapNotNull { classFile ->
                val className = classFile.relativeTo(outputDirectory).path
                    .removeSuffix(".class")
                    .replace(File.separatorChar, '.')
                runCatching { Class.forName(className, false, this@getFacadeClass.classLoader) }.getOrNull()
            }
            .firstOrNull { it.declaredMethods.any { method -> method.name.matchesFacade(methodName) } }
        checkNotNull(clazz) { "no generated class contains a method named '$methodName' in $outputDirectory" }
        return clazz
    }

    /**
     * Whether this JVM method name is the generated facade [methodName]. Kotlin mangles JVM names of functions that
     * have unsigned parameter types (used for boolean/char etc.) with a trailing `-<hash>`, so a plain equality match
     * would miss them.
     */
    private fun String.matchesFacade(methodName: String): Boolean =
        this == methodName || startsWith("$methodName-")

    @Test
    fun `top-level primitive JniActual generates facade with CName`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActual

                @JniActual(className = "com.example.Native", methodName = "add")
                fun add(a: Int, b: Int): Int = a + b
                """.trimIndent(),
            ),
            jniVersion = 2,
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val method = result.getFacadeMethod("test_addJniBinding")
        assertEquals("Java_com_example_Native_add", cNameValue(method))

        // entry points are generated alongside the facades when JNI > 1 (or hooks are present)
        result.getFacadeMethod("entryPointJniBinding")
        result.getFacadeMethod("exitPointJniBinding")
    }

    @Test
    fun `JObject receiver is treated as instance method`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.JObject
                import io.github.mimimishkin.jni.binding.annotation.JniActual

                @JniActual(className = "com.example.Native")
                fun JObject.value(): Int = 42
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val method = result.getFacadeMethod("test_valueJniBinding")
        assertEquals("Java_com_example_Native_value", cNameValue(method))
    }

    @Test
    fun `JniOnLoad hook compiles`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.JavaVM
                import io.github.mimimishkin.jni.binding.annotation.JniActual
                import io.github.mimimishkin.jni.binding.annotation.JniOnLoad

                @JniOnLoad
                fun onLoad(vm: JavaVM) {}

                @JniActual(className = "com.example.Native")
                fun add(a: Int, b: Int): Int = a + b
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    }

    @Test
    fun `actuals file is written with JniActualInfo`() {
        val actualsFile = File.createTempFile("actuals", ".json")
        val result = compileWithActualsFile(
            actualsFile,
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActual

                @JniActual(className = "com.example.Native", methodName = "add")
                fun add(a: Int, b: Int): Int = a + b
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertTrue(actualsFile.readText().contains("com.example.Native"))
    }

    @Test
    fun `actuals file contains exactly one entry per actual`() {
        val actualsFile = File.createTempFile("actuals", ".json")
        val result = compileWithActualsFile(
            actualsFile,
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.JniEnv
                import io.github.mimimishkin.jni.binding.annotation.JniActual

                @JniActual(className = "com.example.Native", methodName = "add")
                context(env: JniEnv)
                fun add(a: Int, b: Int): Int = a + b
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val infos = Json.decodeFromString<List<JniActualInfo>>(actualsFile.readText())
        assertEquals(1, infos.size, "expected a single entry, got: ${actualsFile.readText()}")
        assertEquals("com.example.Native", infos.single().className)
    }

    @Test
    fun `JClass receiver is treated as static method`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.JClass
                import io.github.mimimishkin.jni.binding.annotation.JniActual

                @JniActual(className = "com.example.Native")
                fun JClass.create(): Int = 0
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val method = result.getFacadeMethod("test_createJniBinding")
        assertEquals("Java_com_example_Native_create", cNameValue(method))
    }

    @Test
    fun `JniEnv context parameter is accepted`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.JniEnv
                import io.github.mimimishkin.jni.binding.annotation.JniActual

                @JniActual(className = "com.example.Native")
                context(env: JniEnv)
                fun value(): Int = 42
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val method = result.getFacadeMethod("test_valueJniBinding")
        assertEquals("Java_com_example_Native_value", cNameValue(method))
    }

    @Test
    fun `all primitive types are supported`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActual

                @JniActual(className = "com.example.Native")
                fun everything(
                    boolean: Boolean,
                    ubyte: UByte,
                    byte: Byte,
                    char: Char,
                    ushort: UShort,
                    short: Short,
                    int: Int,
                    long: Long,
                    float: Float,
                    double: Double,
                ) = int
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val method = result.getFacadeMethod("test_everythingJniBinding")
        assertEquals("Java_com_example_Native_everything", cNameValue(method))
    }

    @Test
    fun `boolean return is exposed as byte in the facade`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActual

                @JniActual(className = "com.example.Native")
                fun isOk(value: Boolean): Boolean = value
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val method = result.getFacadeMethod("test_isOkJniBinding")
        // boolean is transported as UByte: the facade parameter and return type are `byte` on the JVM
        assertEquals("byte", method.returnType.name)
        assertEquals("byte", method.parameterTypes[2].name)
    }

    @Test
    fun `JniActuals object generates facades for members`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActuals

                @JniActuals(className = "com.example.Native")
                object Native {
                    fun add(a: Int, b: Int): Int = a + b
                }
                """.trimIndent(),
            ),
            SourceFile.java("Native.java", "package com.example; public class Native {}"),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val add = result.getFacadeMethod("test_Native_addJniBinding")
        assertEquals("Java_com_example_Native_add", cNameValue(add))
    }

    @Test
    fun `overloaded JniActuals members generate distinct facades`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActuals

                @JniActuals(className = "com.example.Native")
                object Native {
                    fun add(a: Int, b: Int): Int = a + b
                    fun add(a: Float, b: Float): Float = a + b
                }
                """.trimIndent(),
            ),
            SourceFile.java("Native.java", "package com.example; public class Native {}"),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val intAdd = result.getFacadeMethod("test_Native_addJniBindingII")
        assertEquals("Java_com_example_Native_add__II", cNameValue(intAdd))
        val floatAdd = result.getFacadeMethod("test_Native_addJniBindingFF")
        assertEquals("Java_com_example_Native_add__FF", cNameValue(floatAdd))
    }

    @Test
    fun `JniActuals class with no-arg constructor is instantiated on load`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActuals

                @JniActuals(className = "com.example.Native")
                class Native {
                    fun value(): Int = 42
                }
                """.trimIndent(),
            ),
            SourceFile.java("Native.java", "package com.example; public class Native {}"),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val method = result.getFacadeMethod("test_Native_valueJniBinding")
        assertEquals("Java_com_example_Native_value", cNameValue(method))
    }

    @Test
    fun `JniActuals class with JavaVM constructor is instantiated with the vm`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.JavaVM
                import io.github.mimimishkin.jni.binding.annotation.JniActuals

                @JniActuals(className = "com.example.Native")
                class Native(private val vm: JavaVM) {
                    fun value(): Int = 42
                }
                """.trimIndent(),
            ),
            SourceFile.java("Native.java", "package com.example; public class Native {}"),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val method = result.getFacadeMethod("test_Native_valueJniBinding")
        assertEquals("Java_com_example_Native_value", cNameValue(method))
    }

    @Test
    fun `WithJvmSignature overrides the JVM parameter types`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.JObject
                import io.github.mimimishkin.jni.binding.annotation.JniActual
                import io.github.mimimishkin.jni.binding.annotation.WithJvmSignature

                @JniActual(className = "com.example.Native")
                @WithJvmSignature(parameterTypes = ["java.lang.String"], returnType = "int")
                fun length(s: JObject): Int = 42
                """.trimIndent(),
            ),
        ).compile()

        check(result.exitCode == KotlinCompilation.ExitCode.OK) { "KCT_MESSAGES_START\n" + result.messages + "\nKCT_MESSAGES_END" }
    }

    @Test
    fun `JniOnUnload hook compiles`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActual
                import io.github.mimimishkin.jni.binding.annotation.JniOnUnload

                @JniOnUnload
                fun onUnload() {}

                @JniActual(className = "com.example.Native")
                fun add(a: Int, b: Int): Int = a + b
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    }

    @Test
    fun `hooks with a JavaVM parameter compile`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.JavaVM
                import io.github.mimimishkin.jni.binding.annotation.JniActual
                import io.github.mimimishkin.jni.binding.annotation.JniOnLoad
                import io.github.mimimishkin.jni.binding.annotation.JniOnUnload

                @JniOnLoad
                fun onLoad(vm: JavaVM) {}

                @JniOnUnload
                fun onUnload(vm: JavaVM) {}

                @JniActual(className = "com.example.Native")
                fun add(a: Int, b: Int): Int = a + b
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    }

    @Test
    fun `multiple JniActuals produce separate facades`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActual

                @JniActual(className = "com.example.A")
                fun a(): Int = 1

                @JniActual(className = "com.example.B")
                fun b(): Int = 2
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val a = result.getFacadeMethod("test_aJniBinding")
        val b = result.getFacadeMethod("test_bJniBinding")
        assertEquals("Java_com_example_A_a", cNameValue(a))
        assertEquals("Java_com_example_B_b", cNameValue(b))
    }

    @Test
    fun `actuals file records needEnv and jvm types`() {
        val actualsFile = File.createTempFile("actuals", ".json")
        val result = compileWithActualsFile(
            actualsFile,
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.JObject
                import io.github.mimimishkin.jni.binding.JniEnv
                import io.github.mimimishkin.jni.binding.annotation.JniActual

                @JniActual(className = "com.example.Native")
                context(env: JniEnv)
                fun value(s: JObject): Boolean = true
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val info = Json.decodeFromString<List<JniActualInfo>>(actualsFile.readText()).single()
        assertEquals(true, info.needEnv)
        assertEquals(null, info.isStatic)
        assertEquals("com.example.Native", info.className)
        assertEquals("value", info.methodName)
        assertEquals(listOf("@NonNull java.lang.Object"), info.parameterTypes)
        assertEquals("boolean", info.returnType)
    }

    @Test
    fun `default parameters are rejected`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActual

                @JniActual(className = "com.example.Native")
                fun add(a: Int, b: Int = 1): Int = a + b
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    }

    @Test
    fun `vararg parameters are rejected`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActual

                @JniActual(className = "com.example.Native")
                fun add(vararg a: Int): Int = 42
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    }

    @Test
    fun `type parameters are rejected`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActual

                @JniActual(className = "com.example.Native")
                fun <T> add(a: T): Int = 42
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    }

    @Test
    fun `private functions are rejected`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActual

                @JniActual(className = "com.example.Native")
                private fun add(a: Int): Int = 42
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    }

    @Test
    fun `nullable primitive parameters are rejected`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActual

                @JniActual(className = "com.example.Native")
                fun add(a: Int?): Int = 42
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    }

    @Test
    fun `unsupported JVM parameter type is rejected`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActual

                @JniActual(className = "com.example.Native")
                fun length(s: String): Int = s.length
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    }

    @Test
    fun `native type without a known JVM type on a parameter is rejected`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActual
                import kotlinx.cinterop.CPointer

                @JniActual(className = "com.example.Native")
                fun length(s: CPointer<Int>): Int = 42
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(result.messages.contains("Corresponding type on JVM side of parameter 's' is unknown"))
    }

    @Test
    fun `native type without a known JVM type as a return type is rejected`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActual
                import kotlinx.cinterop.CPointer

                @JniActual(className = "com.example.Native")
                fun value(): CPointer<Int> = TODO()
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(result.messages.contains("Corresponding type on JVM side of return type is unknown"))
    }

    @Test
    fun `void as a parameter type is rejected`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActual
                import io.github.mimimishkin.jni.binding.annotation.WithJvmSignature

                @JniActual(className = "com.example.Native")
                @WithJvmSignature(parameterTypes = ["void"], returnType = "int")
                fun consume(s: Int): Int = 42
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(result.messages.contains("Corresponding type on JVM side of parameter 's' is unknown"))
    }

    @Test
    fun `WithJvmSignature parameter count mismatch is rejected`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActual
                import io.github.mimimishkin.jni.binding.annotation.WithJvmSignature

                @JniActual(className = "com.example.Native")
                @WithJvmSignature(parameterTypes = ["int", "int"], returnType = "int")
                fun add(a: Int): Int = 42
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(
            result.messages.contains(
                "Number of parameters in @WithJvmSignature does not match the number of parameters in the function",
            ),
        )
    }

    @Test
    fun `void via WithJvmType type-use annotation on a parameter is rejected`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActual
                import io.github.mimimishkin.jni.binding.annotation.WithJvmType

                @JniActual(className = "com.example.Native")
                fun consume(s: @WithJvmType("void") Int): Int = 42
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(result.messages.contains("Corresponding type on JVM side of parameter 's' is unknown"))
    }

    @Test
    fun `WithJvmSignature with fewer parameters than the function is rejected`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActual
                import io.github.mimimishkin.jni.binding.annotation.WithJvmSignature

                @JniActual(className = "com.example.Native")
                @WithJvmSignature(parameterTypes = ["int"], returnType = "int")
                fun add(a: Int, b: Int): Int = a + b
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(
            result.messages.contains(
                "Number of parameters in @WithJvmSignature does not match the number of parameters in the function",
            ),
        )
    }

    @Test
    fun `receiver pointing at a non-JVM type is rejected`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActual
                import kotlinx.cinterop.CPointer

                @JniActual(className = "com.example.Native")
                fun CPointer<Int>.value(): Int = 42
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    }

    @Test
    fun `abstract JniActuals class is rejected`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActuals

                @JniActuals(className = "com.example.Native")
                abstract class Native {
                    fun value(): Int = 42
                }
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    }

    @Test
    fun `nested JniActuals class is rejected`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActuals

                class Outer {
                    @JniActuals(className = "com.example.Native")
                    object Native {
                        fun value(): Int = 42
                    }
                }

                fun ping(): Int = 1
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    }

    @Test
    fun `hook returning a value is rejected`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniOnLoad

                @JniOnLoad
                fun onLoad(): Int = 42
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    }

    @Test
    fun `hook with more than one parameter is rejected`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniOnLoad

                @JniOnLoad
                fun onLoad(a: Int, b: Int) {}
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    }

    @Test
    fun `hook with a non-JavaVM parameter is rejected`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniOnLoad

                @JniOnLoad
                fun onLoad(x: Int) {}
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    }

    @Test
    fun `multiple on-load hooks are rejected without allowSeveralHooks`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.JavaVM
                import io.github.mimimishkin.jni.binding.annotation.JniActual
                import io.github.mimimishkin.jni.binding.annotation.JniOnLoad

                @JniOnLoad
                fun first(vm: JavaVM) {}

                @JniOnLoad
                fun second(vm: JavaVM) {}

                @JniActual(className = "com.example.Native")
                fun add(a: Int, b: Int): Int = a + b
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    }

    @Test
    fun `JniActuals without className uses the object FQN`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActuals

                @JniActuals
                object Native {
                    fun value(): Int = 42
                }
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val method = result.getFacadeMethod("test_Native_valueJniBinding")
        assertEquals("Java_test_Native_value", cNameValue(method))
    }

    @Test
    fun `all facades are generated into a single generated file`() {
        val result = compile(
            SourceFile.kotlin(
                "A.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActual

                @JniActual(className = "com.example.A")
                fun a(): Int = 1
                """.trimIndent(),
            ),
            SourceFile.kotlin(
                "B.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActual

                @JniActual(className = "com.example.B")
                fun b(): Int = 2
                """.trimIndent(),
            ),
            jniVersion = 2,
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val aClass = result.getFacadeClass("test_aJniBinding")
        val bClass = result.getFacadeClass("test_bJniBinding")
        assertEquals(aClass, bClass, "facades must be pre-declared together, in the single jni.binding.generated file")
        // the entry points share that same generated file
        assertEquals(result.getFacadeClass("entryPointJniBinding"), aClass, "entry points and facades share the generated file")
        assertEquals("Java_com_example_A_a", cNameValue(result.getFacadeMethod("test_aJniBinding")))
        assertEquals("Java_com_example_B_b", cNameValue(result.getFacadeMethod("test_bJniBinding")))
    }

    @Test
    fun `actualsFile is required`() {
        val result = KotlinCompilation().apply {
            sources = stubSources + SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActual

                @JniActual(className = "com.example.Native")
                fun add(a: Int): Int = a
                """.trimIndent(),
            )
            compilerPluginRegistrars = listOf(JniBindingProducerRegistrar())
            commandLineProcessors = listOf(JniBindingProducerCommandLineProcessor())
            inheritClassPath = true
            kotlincArguments = listOf("-Xallow-kotlin-package")
        }.compile()

        assertNotEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertTrue(result.messages.contains("actualsFile"))
    }

    @Test
    fun `type parameters in JniActuals container are rejected`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActuals

                @JniActuals(className = "com.example.Native")
                class Native<T> {
                    fun value(): Int = 42
                }
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    }

    @Test
    fun `suspend JniActual is rejected`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActual

                @JniActual(className = "com.example.Native")
                suspend fun add(a: Int): Int = a
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    }

    @Test
    fun `hook inside a regular class is rejected`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.JavaVM
                import io.github.mimimishkin.jni.binding.annotation.JniOnLoad

                class Outer {
                    @JniOnLoad
                    fun onLoad(vm: JavaVM) {}
                }
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    }

    @Test
    fun `WithJvmSignature on a non-JniActual function is rejected`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.WithJvmSignature

                @WithJvmSignature(parameterTypes = ["int"], returnType = "int")
                fun add(a: Int): Int = a
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    }

    @Test
    fun `empty JniActuals class name is rejected`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActuals

                @JniActuals(className = "")
                object Native {
                    fun value(): Int = 42
                }
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    }

    @Test
    fun `invalid JniActuals class name is rejected`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActuals

                @JniActuals(className = "com.example.Native!")
                object Native {
                    fun value(): Int = 42
                }
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    }

    @Test
    fun `methodName override is reflected in the facade CName`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActual

                @JniActual(className = "com.example.Native", methodName = "compute")
                fun nativeCompute(a: Int): Int = a
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val method = result.getFacadeMethod("test_nativeComputeJniBinding")
        assertEquals("Java_com_example_Native_compute", cNameValue(method))
    }

    @Test
    fun `unit return is exposed as void in the facade`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniActual

                @JniActual(className = "com.example.Native")
                fun ping(): Unit {}
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val method = result.getFacadeMethod("test_pingJniBinding")
        assertEquals("void", method.returnType.name)
    }

    @Test
    fun `JObject parameter is recorded with its JVM type`() {
        val actualsFile = File.createTempFile("actuals", ".json")
        val result = compileWithActualsFile(
            actualsFile,
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.JObject
                import io.github.mimimishkin.jni.binding.annotation.JniActual

                @JniActual(className = "com.example.Native")
                fun value(callback: JObject): Int = 42
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val info = Json.decodeFromString<List<JniActualInfo>>(actualsFile.readText()).single()
        assertEquals(listOf("@NonNull java.lang.Object"), info.parameterTypes)
    }

    @Test
    fun `WithJvmSignature values are recorded in the actuals file`() {
        val actualsFile = File.createTempFile("actuals", ".json")
        val result = compileWithActualsFile(
            actualsFile,
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.JObject
                import io.github.mimimishkin.jni.binding.annotation.JniActual
                import io.github.mimimishkin.jni.binding.annotation.WithJvmSignature

                @JniActual(className = "com.example.Native")
                @WithJvmSignature(parameterTypes = ["java.lang.String"], returnType = "void")
                fun value(s: JObject) {}
                """.trimIndent(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val info = Json.decodeFromString<List<JniActualInfo>>(actualsFile.readText()).single()
        assertEquals(listOf("java.lang.String"), info.parameterTypes)
        assertEquals("void", info.returnType)
    }

    @Test
    fun `multiple hooks compile when allowSeveralHooks is enabled`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.JavaVM
                import io.github.mimimishkin.jni.binding.annotation.JniActual
                import io.github.mimimishkin.jni.binding.annotation.JniOnLoad
                import io.github.mimimishkin.jni.binding.annotation.JniOnUnload

                @JniOnLoad
                fun firstLoad(vm: JavaVM) {}

                @JniOnLoad
                fun secondLoad(vm: JavaVM) {}

                @JniOnUnload
                fun firstUnload(vm: JavaVM) {}

                @JniOnUnload
                fun secondUnload(vm: JavaVM) {}

                @JniActual(className = "com.example.Native")
                fun add(a: Int, b: Int): Int = a + b
                """.trimIndent(),
            ),
            allowSeveralHooks = true,
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    }

    @Test
    fun `JniActuals class combined with hooks compiles under allowSeveralHooks`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.JavaVM
                import io.github.mimimishkin.jni.binding.annotation.JniActuals
                import io.github.mimimishkin.jni.binding.annotation.JniOnLoad

                @JniOnLoad
                fun onLoad(vm: JavaVM) {}

                @JniActuals(className = "com.example.Native")
                class Native(private val vm: JavaVM) {
                    fun value(): Int = 42
                }
                """.trimIndent(),
            ),
            SourceFile.java("Native.java", "package com.example; public class Native {}"),
            allowSeveralHooks = true,
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val method = result.getFacadeMethod("test_Native_valueJniBinding")
        assertEquals("Java_com_example_Native_value", cNameValue(method))
    }

    @Test
    fun `multiple constructable JniActuals containers are instantiated under allowSeveralHooks`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.JavaVM
                import io.github.mimimishkin.jni.binding.annotation.JniActuals

                @JniActuals(className = "com.example.A")
                class A(private val vm: JavaVM) {
                    fun value(): Int = 1
                }

                @JniActuals(className = "com.example.B")
                class B {
                    fun value(): Int = 2
                }
                """.trimIndent(),
            ),
            SourceFile.java("A.java", "package com.example; public class A {}"),
            SourceFile.java("B.java", "package com.example; public class B {}"),
            allowSeveralHooks = true,
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertEquals("Java_com_example_A_value", cNameValue(result.getFacadeMethod("test_A_valueJniBinding")))
        assertEquals("Java_com_example_B_value", cNameValue(result.getFacadeMethod("test_B_valueJniBinding")))
    }

    @Test
    fun `register natives and several hooks work together`() {
        val result = compile(
            SourceFile.kotlin(
                "Main.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.JavaVM
                import io.github.mimimishkin.jni.binding.annotation.JniActual
                import io.github.mimimishkin.jni.binding.annotation.JniOnLoad

                @JniOnLoad
                fun onLoad(vm: JavaVM) {}

                @JniActual(className = "com.example.Native")
                fun add(a: Int, b: Int): Int = a + b

                @JniActual(className = "com.example.Native")
                fun sub(a: Int, b: Int): Int = a - b
                """.trimIndent(),
            ),
            allowSeveralHooks = true,
            useRegisterNatives = true,
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        // register natives mode inlines the dispatch into staticCFunction lambdas: no facades are generated
        assertFalse(result.hasFacadeMethod("test_addJniBinding_II_I"))
        assertFalse(result.hasFacadeMethod("test_addJniBinding"))
        assertFalse(result.hasFacadeMethod("test_subJniBinding_II_I"))
        assertFalse(result.hasFacadeMethod("test_subJniBinding"))
    }
}
