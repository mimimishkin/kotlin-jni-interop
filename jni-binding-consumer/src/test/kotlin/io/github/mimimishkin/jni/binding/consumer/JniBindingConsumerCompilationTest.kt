@file:OptIn(ExperimentalCompilerApi::class)

package io.github.mimimishkin.jni.binding.consumer

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import io.github.mimimishkin.jni.binding.consumer.model.JavaType
import io.github.mimimishkin.jni.binding.consumer.model.JniExpectDeclaration
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.File
import java.net.URLClassLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end tests of the consumer plugin through the kotlin-compile-testing framework.
 *
 * The plugin checks Kotlin `@JniExpect` declarations against the per-target `actuals.json` produced by the native
 * build (`JniExpectMatcher`). These tests drive the real JVM backend so the matcher observes the actual bytecode
 * names. The `@JniExpect`/`@JniExpects` annotation classes come from the real `jni-binding-annotations` artifact on
 * the test classpath.
 */
class JniBindingConsumerCompilationTest {

    private fun compile(
        vararg sources: SourceFile,
        actualsJson: String,
        allowExtraActuals: Boolean = false,
        expectsFile: File = File.createTempFile("jni-binding-expects", ".json"),
        actualsAbsent: Boolean = false,
    ): KotlinCompilation =
        KotlinCompilation().apply {
            val actualsFile = File.createTempFile("jni-binding-actuals", ".json")
            if (!actualsAbsent) actualsFile.writeText(actualsJson) else actualsFile.delete()
            this.sources = sources.toList()
            compilerPluginRegistrars = listOf(JniBindingConsumerRegistrar())
            commandLineProcessors = listOf(JniBindingConsumerCommandLineProcessor())
            // `expectsFile` is required, so every compilation gets one, exactly as the Gradle plugin does.
            pluginOptions = listOf(
                PluginOption("jni-binding-consumer", "enabled", "true"),
                PluginOption("jni-binding-consumer", "actualsFile", "test:$actualsFile"),
                PluginOption("jni-binding-consumer", "allowExtraActuals", allowExtraActuals.toString()),
                PluginOption("jni-binding-consumer", "expectsFile", expectsFile.path),
            )
            inheritClassPath = true
        }

    private val EMPTY_ACTUALS = "[]"

    private fun readExpects(expectsFile: File): List<JniExpectDeclaration> =
        Json.decodeFromString(expectsFile.readText())

    private fun actuals(vararg actuals: String): String =
        actuals.joinToString(prefix = "[", postfix = "]", separator = ",")

    private fun parameterTypesJson(parameterTypes: String): String =
        parameterTypes.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(separator = ",", prefix = "[", postfix = "]") { "\"$it\"" }

    private fun actual(
        methodName: String,
        returnType: String = "int",
        parameterTypes: String = "",
        className: String = "test.Wrap",
        isStatic: Boolean? = false,
    ): String =
        """
        {
            "needEnv": true,
            "isStatic": ${isStatic?.toString() ?: "null"},
            "className": "$className",
            "methodName": "$methodName",
            "parameterTypes": ${parameterTypesJson(parameterTypes)},
            "returnType": "$returnType",
            "source": "native/source.kt"
        }
        """.trimIndent()

    private val WRAP_FUNCTION_SOURCE =
        SourceFile.kotlin(
            "Wrap.kt",
            """
            package test

            import io.github.mimimishkin.jni.binding.annotation.JniExpect

            class Wrap {
                @JniExpect(targets = ["test"])
                external fun foo(s: String): Int
            }
            """.trimIndent(),
        )

    @Test
    fun `function expect with matching actual passes`() {
        val result = compile(
            WRAP_FUNCTION_SOURCE,
            actualsJson = actuals(actual(methodName = "foo", parameterTypes = "@NonNull java.lang.String")),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertFalse(result.messages.contains("doesn't have a corresponding"), result.messages)
    }

    // ── Static status mismatch ───────────────────────────────────────────────

    /** A top-level `external fun` compiles to a static JVM method, so its expect is static. */
    private val STATIC_EXPECT_SOURCE =
        SourceFile.kotlin(
            "Wrap.kt",
            """
            package test

            import io.github.mimimishkin.jni.binding.annotation.JniExpect

            @JniExpect(targets = ["test"])
            external fun foo(): Int
            """.trimIndent(),
        )

    private val staticActual: String =
        actual(methodName = "foo", className = "test.WrapKt", isStatic = true)

    @Test
    fun `static expect with instance actual fails with a stasis diagnostic`() {
        val result = compile(
            STATIC_EXPECT_SOURCE,
            actualsJson = actuals(actual(methodName = "foo", className = "test.WrapKt", isStatic = false)),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(
            result.messages.contains("Static status mismatch between @JniExpect and @JniActual: expected static, actual instance."),
            result.messages,
        )
        // The stasis diagnostic already names the cause, so the generic "missing actual" must not pile on top of it.
        assertFalse(result.messages.contains("doesn't have a corresponding"), result.messages)
        // ...and the mismatched actual is claimed by this expect, not reported as a leftover one.
        assertFalse(result.messages.contains("Found extra @JniActuals"), result.messages)
    }

    @Test
    fun `instance expect with static actual fails with a stasis diagnostic`() {
        val result = compile(
            WRAP_FUNCTION_SOURCE,
            actualsJson = actuals(
                actual(methodName = "foo", parameterTypes = "@NonNull java.lang.String", isStatic = true),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(
            result.messages.contains("Static status mismatch between @JniExpect and @JniActual: expected instance, actual static."),
            result.messages,
        )
        assertFalse(result.messages.contains("doesn't have a corresponding"), result.messages)
        assertFalse(result.messages.contains("Found extra @JniActuals"), result.messages)
    }

    @Test
    fun `static expect with a same-named actual of a different signature reports the concrete cause`() {
        val result = compile(
            STATIC_EXPECT_SOURCE,
            actualsJson = actuals(
                // Same method name, but returns void instead of int: a return-type mismatch, named as such instead of
                // falling back to the generic "missing" message.
                actual(
                    methodName = "foo",
                    className = "test.WrapKt",
                    returnType = "void",
                    isStatic = true,
                ),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(
            result.messages.contains("Return type mismatch between @JniExpect and @JniActual: expected 'int', actual 'void'."),
            result.messages,
        )
        assertFalse(result.messages.contains("Static status mismatch"), result.messages)
        assertFalse(result.messages.contains("doesn't have a corresponding"), result.messages)
        assertFalse(result.messages.contains("Found extra @JniActuals"), result.messages)
    }

    // ── Concrete signature mismatches ────────────────────────────────────────

    private val TWO_PARAM_SOURCE =
        SourceFile.kotlin(
            "Wrap.kt",
            """
            package test

            import io.github.mimimishkin.jni.binding.annotation.JniExpect

            class Wrap {
                @JniExpect(targets = ["test"])
                external fun foo(count: Int, name: String): Int
            }
            """.trimIndent(),
        )

    @Test
    fun `a wrong parameter type names the parameter, its index and both types`() {
        val result = compile(
            TWO_PARAM_SOURCE,
            actualsJson = actuals(
                // The user's JInt-vs-Long case: `count: Int` on the JVM side, `long` in the native declaration.
                actual(
                    methodName = "foo",
                    parameterTypes = "long, @NonNull java.lang.String",
                    isStatic = false,
                ),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(
            result.messages.contains("Parameter type mismatch between @JniExpect and @JniActual at index 0: expected 'int', actual 'long'."),
            result.messages,
        )
        // The generic message would hide the actual cause, so it must not be reported alongside.
        assertFalse(result.messages.contains("doesn't have a corresponding"), result.messages)
        assertFalse(result.messages.contains("Found extra @JniActuals"), result.messages)
    }

    @Test
    fun `only the first wrong parameter is reported`() {
        val result = compile(
            TWO_PARAM_SOURCE,
            actualsJson = actuals(
                actual(
                    methodName = "foo",
                    parameterTypes = "long, int",
                    isStatic = false,
                ),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        // Naming one wrong parameter is more actionable than listing every one of them.
        assertTrue(
            result.messages.contains("Parameter type mismatch between @JniExpect and @JniActual at index 0: expected 'int', actual 'long'."),
            result.messages,
        )
        assertFalse(result.messages.contains("at index 1"), result.messages)
    }

    @Test
    fun `a later wrong parameter is reported by its own index`() {
        val result = compile(
            TWO_PARAM_SOURCE,
            actualsJson = actuals(
                actual(
                    methodName = "foo",
                    parameterTypes = "int, @NonNull java.lang.Object",
                    isStatic = false,
                ),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(
            result.messages.contains("Parameter type mismatch between @JniExpect and @JniActual at index 1: expected '@NonNull java.lang.String', actual '@NonNull java.lang.Object'."),
            result.messages,
        )
        assertFalse(result.messages.contains("at index 0"), result.messages)
    }

    @Test
    fun `a wrong parameter count is reported as a count`() {
        val result = compile(
            TWO_PARAM_SOURCE,
            actualsJson = actuals(
                actual(
                    methodName = "foo",
                    parameterTypes = "int",
                    isStatic = false,
                ),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(
            result.messages.contains("Parameter count mismatch between @JniExpect and @JniActual: expected 2 parameter(s), actual 1 parameter(s)."),
            result.messages,
        )
        // An arity difference leaves no single parameter to point at, so no per-parameter message.
        assertFalse(result.messages.contains("Parameter type mismatch"), result.messages)
        assertFalse(result.messages.contains("doesn't have a corresponding"), result.messages)
    }

    @Test
    fun `a return type mismatch is reported instead of the generic missing message`() {
        val result = compile(
            TWO_PARAM_SOURCE,
            actualsJson = actuals(
                actual(
                    methodName = "foo",
                    returnType = "void",
                    parameterTypes = "int, @NonNull java.lang.String",
                    isStatic = false,
                ),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(
            result.messages.contains("Return type mismatch between @JniExpect and @JniActual: expected 'int', actual 'void'."),
            result.messages,
        )
        assertFalse(result.messages.contains("doesn't have a corresponding"), result.messages)
    }

    @Test
    fun `a same-named actual that matches on a later candidate reports nothing`() {
        val result = compile(
            TWO_PARAM_SOURCE,
            actualsJson = actuals(
                // Two actuals for `foo`, the first with a wrong parameter type and the second fully matching. Only
                // the matching one may be used, and the loser must not be condemned by a signature diagnostic.
                actual(methodName = "foo", parameterTypes = "long, @NonNull java.lang.String", isStatic = false),
                actual(methodName = "foo", parameterTypes = "int, @NonNull java.lang.String", isStatic = false),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        // This is the guarantee that comparing before reporting exists for: a failing candidate must not produce a
        // diagnostic when a later candidate satisfies the expect.
        assertFalse(result.messages.contains("Parameter type mismatch"), result.messages)
        assertFalse(result.messages.contains("doesn't have a corresponding"), result.messages)
        // Unlike the single-candidate case, the unused actual really is leftover here — nothing implements it — so
        // it is reported as extra.
        assertTrue(result.messages.contains("Found extra @JniActuals for test"), result.messages)
    }

    @Test
    fun `matching static status reports no mismatch`() {
        val result = compile(
            STATIC_EXPECT_SOURCE,
            actualsJson = actuals(staticActual),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertFalse(result.messages.contains("doesn't have a corresponding"), result.messages)
        assertFalse(result.messages.contains("Static status mismatch"), result.messages)
    }

    @Test
    fun `actual without static info is never reported as a stasis mismatch`() {
        val result = compile(
            STATIC_EXPECT_SOURCE,
            actualsJson = actuals(actual(methodName = "foo", className = "test.WrapKt", isStatic = null)),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertFalse(result.messages.contains("doesn't have a corresponding"), result.messages)
        assertFalse(result.messages.contains("Static status mismatch"), result.messages)
    }

    @Test
    fun `non-external JniExpect function is an error`() {
        val result = compile(
            SourceFile.kotlin(
                "Wrap.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniExpect

                class Wrap {
                    @JniExpect(targets = ["test"]) 
                    fun foo(s: String): Int { return s.length }
                }
                """.trimIndent(),
            ),
            actualsJson = EMPTY_ACTUALS,
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(result.messages.contains("@JniExpect can only be applied to external (native) functions"), result.messages)
    }

    @Test
    fun `function expect without actual reports missing`() {
        val result = compile(
            WRAP_FUNCTION_SOURCE,
            actualsJson = EMPTY_ACTUALS,
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(result.messages.contains("doesn't have a corresponding @JniActual implementation"), result.messages)
        assertTrue(result.messages.contains("for name 'foo' in target 'test' with parameters"), result.messages)
        // Naming the task that writes the declaration is what turns this from a dead end into a next step.
        assertTrue(result.messages.contains("generateJniActuals"), result.messages)
    }

    @Test
    fun `a producer that was never built reports the expects as missing`() {
        val result = compile(WRAP_FUNCTION_SOURCE, actualsJson = EMPTY_ACTUALS, actualsAbsent = true).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(result.messages.contains("for name 'foo' in target 'test'"), result.messages)
    }

    // ── @LoadMethod ──────────────────────────────────────────────────────────

    @Test
    fun `a LoadMethod in a plain object is injected without a JniExpects container`() {
        // Whether a `@LoadMethod` gets called is a property of the function, not of what else its class declares:
        // an object whose only job is to hold the load method is the normal way to load a library bound to
        // top-level external functions, which have no container to inject into.
        val source = SourceFile.kotlin(
            "Loader.kt",
            """
            package test

            import io.github.mimimishkin.jni.binding.annotation.LoadMethod

            object Loader {
                @LoadMethod
                fun load(os: String, arch: String) {
                    loaded = "${'$'}os/${'$'}arch"
                }

                var loaded: String? = null
            }
            """.trimIndent(),
        )

        val result = compile(source, actualsJson = EMPTY_ACTUALS).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val loader = URLClassLoader(arrayOf(result.outputDirectory.toURI().toURL()), javaClass.classLoader)
        val instance = loader.loadClass("test.Loader").getField("INSTANCE").get(null)
        // Called during the object's initialization, with the host platform the plugin derives.
        assertNotNull(instance.javaClass.getMethod("getLoaded").invoke(instance))
    }

    @Test
    fun `a LoadMethod on a top-level function is injected into the file facade`() {
        // A top-level `@LoadMethod` needs no container at all: it is called from the `<clinit>` of the file facade
        // the backend generates for the file's top-level declarations. That is the only way to load a library bound
        // to top-level external functions, which have no class to inject into.
        //
        // The recorded value lives in a separate file: a top-level property initializer of this file would land in
        // the same `<clinit>`, and property initializers run *after* the load call, wiping what it recorded.
        val storage = SourceFile.kotlin(
            "Storage.kt",
            """
            package test

            object Storage {
                var loaded: String? = null
            }
            """.trimIndent(),
        )
        val source = SourceFile.kotlin(
            "Loader.kt",
            """
            package test

            import io.github.mimimishkin.jni.binding.annotation.LoadMethod

            @LoadMethod
            fun load(os: String, arch: String) {
                Storage.loaded = "${'$'}os/${'$'}arch"
            }

            fun loadedPlatform(): String? = Storage.loaded
            """.trimIndent(),
        )

        val result = compile(source, storage, actualsJson = EMPTY_ACTUALS).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val loader = URLClassLoader(arrayOf(result.outputDirectory.toURI().toURL()), javaClass.classLoader)
        // Calling a static method is an active use, which is what runs the facade's `<clinit>`.
        val loaded = loader.loadClass("test.LoaderKt").getMethod("loadedPlatform").invoke(null)
        // Called during the facade's initialization, with the host platform the plugin derives.
        assertNotNull(loaded)
    }

    @Test
    fun `a top-level LoadMethod with an unfillable parameter is reported`() {
        val source = SourceFile.kotlin(
            "Loader.kt",
            """
            package test

            import io.github.mimimishkin.jni.binding.annotation.LoadMethod

            @LoadMethod
            fun load(prefix: String) = Unit
            """.trimIndent(),
        )

        val result = compile(source, actualsJson = EMPTY_ACTUALS).compile()

        // Otherwise the function would silently never be called, which is the failure mode this diagnostic exists for.
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(
            result.messages.contains(
                "@LoadMethod function must declare only `os`, `arch`, `vendor` parameters " +
                        "(no extension/context receivers), but has: `prefix`.",
            ),
            result.messages,
        )
    }

    // ── expects.json ─────────────────────────────────────────────────────────

    @Test
    fun `the expect is recorded with the JVM names the backend emitted`() {
        val expectsFile = File.createTempFile("jni-binding-expects", ".json")
        compile(WRAP_FUNCTION_SOURCE, actualsJson = EMPTY_ACTUALS, expectsFile = expectsFile).compile()

        val expect = readExpects(expectsFile).single()
        assertEquals("test.Wrap", expect.className)
        assertEquals("foo", expect.methodName)
        assertEquals(false, expect.isStatic)
        assertEquals(listOf(JavaType("java.lang.String", nullable = false)), expect.parameterTypes)
        assertEquals(JavaType("int", nullable = false), expect.returnType)
        assertEquals(listOf("test"), expect.targets)
    }

    @Test
    fun `the contract is written even though the compilation fails on the missing actual`() {
        // This is the whole point of writing it from the matcher rather than at the end of a successful build: the
        // `generateJniActuals` task exists to bootstrap exactly the state this compilation is failing in.
        val expectsFile = File.createTempFile("jni-binding-expects", ".json")
        val result = compile(WRAP_FUNCTION_SOURCE, actualsJson = EMPTY_ACTUALS, expectsFile = expectsFile).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertEquals(1, readExpects(expectsFile).size)
    }

    @Test
    fun `a static expect is recorded as static, under the file class it compiles to`() {
        val expectsFile = File.createTempFile("jni-binding-expects", ".json")
        compile(STATIC_EXPECT_SOURCE, actualsJson = EMPTY_ACTUALS, expectsFile = expectsFile).compile()

        val expect = readExpects(expectsFile).single()
        assertEquals("test.WrapKt", expect.className)
        assertEquals(true, expect.isStatic)
    }

    @Test
    fun `the contract is reset instead of accumulating the declarations of an earlier compilation`() {
        val expectsFile = File.createTempFile("jni-binding-expects", ".json")
        compile(WRAP_FUNCTION_SOURCE, actualsJson = EMPTY_ACTUALS, expectsFile = expectsFile).compile()
        assertEquals(1, readExpects(expectsFile).size)

        // A stale declaration would generate a stub for something that no longer exists.
        compile(STATIC_EXPECT_SOURCE, actualsJson = EMPTY_ACTUALS, expectsFile = expectsFile).compile()

        assertEquals(listOf("test.WrapKt"), readExpects(expectsFile).map { it.className })
    }

    @Test
    fun `every expect of a compilation is recorded, each under its own JVM class`() {
        // A class and a top-level function in one file: the two shapes that end up as an instance and a static JVM
        // method respectively, under two different JVM classes.
        val otherSource = SourceFile.kotlin(
            "Other.kt",
            """
            package test

            import io.github.mimimishkin.jni.binding.annotation.JniExpect

            @JniExpect(targets = ["test"])
            external fun topLevel(): Int

            class Other {
                @JniExpect(targets = ["test"])
                external fun bar(): Int
            }
            """.trimIndent(),
        )
        val expectsFile = File.createTempFile("jni-binding-expects", ".json")
        compile(WRAP_FUNCTION_SOURCE, otherSource, actualsJson = EMPTY_ACTUALS, expectsFile = expectsFile).compile()

        // The order the backend hands the classes to the matcher in is not part of the contract.
        assertEquals(
            setOf("test.Wrap" to false, "test.OtherKt" to true, "test.Other" to false),
            readExpects(expectsFile).mapTo(mutableSetOf()) { it.className to it.isStatic },
        )
    }

    @Test
    fun `a compilation without the expectsFile option is rejected by the plugin itself`() {
        // The contract file is not optional: without it the compilation could neither report a missing actual
        // usefully nor feed generateJniActuals, so the option is required rather than silently ignored.
        val compilation = compile(WRAP_FUNCTION_SOURCE, actualsJson = EMPTY_ACTUALS).apply {
            pluginOptions = pluginOptions.filterNot { it.optionName == "expectsFile" }
        }

        val result = compilation.compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(result.messages.contains("Required plugin option not present"), result.messages)
        assertTrue(result.messages.contains("expectsFile"), result.messages)
    }

    @Test
    fun `extra actuals are reported once all expects are matched`() {        val result = compile(
            WRAP_FUNCTION_SOURCE,
            actualsJson = actuals(
                actual(methodName = "foo", parameterTypes = "@NonNull java.lang.String"),
                actual(methodName = "unexpected", returnType = "void"),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertTrue(result.messages.contains("Found extra @JniActuals for test"), result.messages)
        assertTrue(result.messages.contains("unexpected"), result.messages)
    }

    @Test
    fun `external function inside JniExpects class is an implicit expect`() {
        val result = compile(
            SourceFile.kotlin(
                "Wrap.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniExpects

                @JniExpects(targets = ["test"])
                class Wrap {
                    external fun bar(): Unit
                }
                """.trimIndent(),
            ),
            actualsJson = EMPTY_ACTUALS,
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(result.messages.contains("doesn't have a corresponding @JniActual implementation"), result.messages)
    }

    @Test
    fun `external function in a JniExpects-annotated file is an implicit expect`() {
        val result = compile(
            SourceFile.kotlin(
                "Top.kt",
                """
                @file:JniExpects(targets = ["test"])

                package testTop

                import io.github.mimimishkin.jni.binding.annotation.JniExpects

                external fun top(): Unit
                """.trimIndent(),
            ),
            actualsJson = EMPTY_ACTUALS,
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(result.messages.contains("doesn't have a corresponding @JniActual implementation"), result.messages)
    }

    @Test
    fun `JniExpect on a property is an expect on the getter`() {
        val result = compile(
            SourceFile.kotlin(
                "Wrap.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniExpect

                class Wrap {
                    @JniExpect(targets = ["test"]) 
                    val x: Int
                        external get
                }
                """.trimIndent(),
            ),
            actualsJson = EMPTY_ACTUALS,
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(result.messages.contains("doesn't have a corresponding @JniActual implementation"), result.messages)
        assertFalse(result.messages.contains("@JniExpect can only be applied to external (native) functions"), result.messages)
    }

    @Test
    fun `JniExpect on a property passes once both accessors are external and have actuals`() {
        val result = compile(
            SourceFile.kotlin(
                "Wrap.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniExpect

                class Wrap {
                    @JniExpect(targets = ["test"])
                    var x: Int
                        external get
                        external set
                }
                """.trimIndent(),
            ),
            actualsJson = actuals(
                actual(methodName = "getX"),
                actual(methodName = "setX", parameterTypes = "int", returnType = "void"),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertFalse(result.messages.contains("doesn't have a corresponding"), result.messages)
        assertFalse(result.messages.contains("@JniExpect can only be applied to external (native) functions"), result.messages)
    }

    @Test
    fun `JniExpect on a property fails when the setter has no actual`() {
        val result = compile(
            SourceFile.kotlin(
                "Wrap.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniExpect

                class Wrap {
                    @JniExpect(targets = ["test"])
                    var x: Int
                        external get
                        external set
                }
                """.trimIndent(),
            ),
            actualsJson = actuals(actual(methodName = "getX")),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(result.messages.contains("doesn't have a corresponding @JniActual implementation"), result.messages)
        assertFalse(result.messages.contains("@JniExpect can only be applied to external (native) functions"), result.messages)
    }

    @Test
    fun `getter-targeting JniExpect requires only the getter`() {
        val result = compile(
            SourceFile.kotlin(
                "Wrap.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniExpect

                class Wrap {
                    @get:JniExpect(targets = ["test"]) 
                    val x: Int
                        external get
                }
                """.trimIndent(),
            ),
            actualsJson = actuals(actual(methodName = "getX")),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertFalse(result.messages.contains("doesn't have a corresponding"), result.messages)
        assertFalse(result.messages.contains("@JniExpect can only be applied to external (native) functions"), result.messages)
    }

    @Test
    fun `setter-targeting JniExpect requires only the setter`() {
        val result = compile(
            SourceFile.kotlin(
                "Wrap.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniExpect

                class Wrap {
                    @set:JniExpect(targets = ["test"]) 
                    var x: Int = 0
                        external set
                }
                """.trimIndent(),
            ),
            actualsJson = actuals(
                actual(methodName = "setX", parameterTypes = "int", returnType = "void"),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertFalse(result.messages.contains("doesn't have a corresponding"), result.messages)
        assertFalse(result.messages.contains("@JniExpect can only be applied to external (native) functions"), result.messages)
    }

    @Test
    fun `JniExpect on a property with non-external accessors is an error`() {
        val result = compile(
            SourceFile.kotlin(
                "Wrap.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniExpect

                class Wrap {
                    @JniExpect(targets = ["test"])
                    var x: Int = 0
                }
                """.trimIndent(),
            ),
            actualsJson = EMPTY_ACTUALS,
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(result.messages.contains("@JniExpect can only be applied to external (native) functions"), result.messages)
    }

    @Test
    fun `getter-targeted JniExpect on a non-external getter is an error`() {
        val result = compile(
            SourceFile.kotlin(
                "Wrap.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniExpect

                class Wrap {
                    @get:JniExpect(targets = ["test"])
                    val x: Int = 0
                }
                """.trimIndent(),
            ),
            actualsJson = EMPTY_ACTUALS,
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(result.messages.contains("@JniExpect can only be applied to external (native) functions"), result.messages)
    }

    @Test
    fun `setter-targeted JniExpect on a non-external setter is an error`() {
        val result = compile(
            SourceFile.kotlin(
                "Wrap.kt",
                """
                package test

                import io.github.mimimishkin.jni.binding.annotation.JniExpect

                class Wrap {
                    @set:JniExpect(targets = ["test"])
                    var x: Int = 0
                }
                """.trimIndent(),
            ),
            actualsJson = EMPTY_ACTUALS,
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(result.messages.contains("@JniExpect can only be applied to external (native) functions"), result.messages)
    }

    private fun compileMulti(
        vararg sources: SourceFile,
        actualsByTarget: Map<String, String>,
        allowExtraActuals: Boolean = false,
    ): KotlinCompilation =
        KotlinCompilation().apply {
            this.sources = sources.toList()
            compilerPluginRegistrars = listOf(JniBindingConsumerRegistrar())
            commandLineProcessors = listOf(JniBindingConsumerCommandLineProcessor())
            pluginOptions = buildList {
                add(PluginOption("jni-binding-consumer", "enabled", "true"))
                for ((target, json) in actualsByTarget) {
                    val file = File.createTempFile("jni-binding-actuals-$target", ".json")
                    file.writeText(json)
                    add(PluginOption("jni-binding-consumer", "actualsFile", "$target:$file"))
                }
                add(PluginOption("jni-binding-consumer", "allowExtraActuals", allowExtraActuals.toString()))
                // `expectsFile` is required, so every compilation gets one, exactly as the Gradle plugin does.
                add(
                    PluginOption(
                        "jni-binding-consumer",
                        "expectsFile",
                        File.createTempFile("jni-binding-expects", ".json").path,
                    )
                )
            }
            inheritClassPath = true
        }

    private val MULTI_TARGET_SOURCE =
        SourceFile.kotlin(
            "Wrap.kt",
            """
            package test

            import io.github.mimimishkin.jni.binding.annotation.JniExpect

            class Wrap {
                @JniExpect(targets = ["linuxX64", "mingwX64"])
                external fun foo(s: String): Int
            }
            """.trimIndent(),
        )

    private fun fooActuals(): String =
        actuals(actual(methodName = "foo", parameterTypes = "@NonNull java.lang.String"))

    // ── Multi-target: functions ──────────────────────────────────────────────

    @Test
    fun `function expect with multiple targets passes when all targets have actuals`() {
        val result = compileMulti(
            MULTI_TARGET_SOURCE,
            actualsByTarget = mapOf(
                "linuxX64" to fooActuals(),
                "mingwX64" to fooActuals(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertFalse(result.messages.contains("doesn't have a corresponding"), result.messages)
    }

    @Test
    fun `function expect with multiple targets reports missing only for targets without actuals`() {
        val result = compileMulti(
            MULTI_TARGET_SOURCE,
            actualsByTarget = mapOf(
                "linuxX64" to fooActuals(),
                "mingwX64" to EMPTY_ACTUALS,
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertFalse(result.messages.contains("for name 'foo' in target 'linuxX64'"), result.messages)
        assertTrue(result.messages.contains("for name 'foo' in target 'mingwX64'"), result.messages)
    }

    @Test
    fun `function expect targeting one target reports extra actuals for the other`() {
        val source = SourceFile.kotlin(
            "Wrap.kt",
            """
            package test

            import io.github.mimimishkin.jni.binding.annotation.JniExpect

            class Wrap {
                @JniExpect(targets = ["linuxX64"])
                external fun foo(s: String): Int
            }
            """.trimIndent(),
        )
        val result = compileMulti(
            source,
            actualsByTarget = mapOf(
                "linuxX64" to fooActuals(),
                "mingwX64" to fooActuals(),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertTrue(result.messages.contains("Found extra @JniActuals for mingwX64"), result.messages)
        assertFalse(result.messages.contains("Found extra @JniActuals for linuxX64"), result.messages)
    }

    @Test
    fun `function expect with empty targets applies to every known target`() {
        val source = SourceFile.kotlin(
            "Wrap.kt",
            """
            package test

            import io.github.mimimishkin.jni.binding.annotation.JniExpect

            class Wrap {
                @JniExpect(targets = [])
                external fun foo(s: String): Int
            }
            """.trimIndent(),
        )
        val result = compileMulti(
            source,
            actualsByTarget = mapOf(
                "linuxX64" to fooActuals(),
                "mingwX64" to EMPTY_ACTUALS,
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertFalse(result.messages.contains("for name 'foo' in target 'linuxX64'"), result.messages)
        assertTrue(result.messages.contains("for name 'foo' in target 'mingwX64'"), result.messages)
    }

    @Test
    fun `separate expects targeting different targets pass independently`() {
        val source = SourceFile.kotlin(
            "Wrap.kt",
            """
            package test

            import io.github.mimimishkin.jni.binding.annotation.JniExpect

            class Wrap {
                @JniExpect(targets = ["linuxX64"])
                external fun foo(s: String): Int

                @JniExpect(targets = ["mingwX64"])
                external fun bar(s: String): Int
            }
            """.trimIndent(),
        )
        val result = compileMulti(
            source,
            actualsByTarget = mapOf(
                "linuxX64" to actuals(actual(methodName = "foo", parameterTypes = "@NonNull java.lang.String")),
                "mingwX64" to actuals(actual(methodName = "bar", parameterTypes = "@NonNull java.lang.String")),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertFalse(result.messages.contains("doesn't have a corresponding"), result.messages)
    }

    // ── Multi-target: implicit expects ───────────────────────────────────────

    @Test
    fun `JniExpects class with multiple targets produces implicit expects for each`() {
        val source = SourceFile.kotlin(
            "Wrap.kt",
            """
            package test

            import io.github.mimimishkin.jni.binding.annotation.JniExpects

            @JniExpects(targets = ["linuxX64", "mingwX64"])
            class Wrap {
                external fun bar(): Unit
            }
            """.trimIndent(),
        )
        val result = compileMulti(
            source,
            actualsByTarget = mapOf(
                "linuxX64" to actuals(actual(methodName = "bar", returnType = "void")),
                "mingwX64" to EMPTY_ACTUALS,
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertFalse(result.messages.contains("for name 'bar' in target 'linuxX64'"), result.messages)
        assertTrue(result.messages.contains("for name 'bar' in target 'mingwX64'"), result.messages)
    }

    @Test
    fun `JniExpects class with empty targets applies to every known target`() {
        val source = SourceFile.kotlin(
            "Wrap.kt",
            """
            package test

            import io.github.mimimishkin.jni.binding.annotation.JniExpects

            @JniExpects(targets = [])
            class Wrap {
                external fun bar(): Unit
            }
            """.trimIndent(),
        )
        val result = compileMulti(
            source,
            actualsByTarget = mapOf(
                "linuxX64" to actuals(actual(methodName = "bar", returnType = "void")),
                "mingwX64" to EMPTY_ACTUALS,
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertFalse(result.messages.contains("for name 'bar' in target 'linuxX64'"), result.messages)
        assertTrue(result.messages.contains("for name 'bar' in target 'mingwX64'"), result.messages)
    }

    // ── Multi-target: properties ─────────────────────────────────────────────

    @Test
    fun `property expect with multiple targets passes when both have actuals`() {
        val source = SourceFile.kotlin(
            "Wrap.kt",
            """
            package test

            import io.github.mimimishkin.jni.binding.annotation.JniExpect

            class Wrap {
                @JniExpect(targets = ["linuxX64", "mingwX64"])
                var x: Int
                    external get
                    external set
            }
            """.trimIndent(),
        )
        val result = compileMulti(
            source,
            actualsByTarget = mapOf(
                "linuxX64" to actuals(actual(methodName = "getX"), actual(methodName = "setX", parameterTypes = "int", returnType = "void")),
                "mingwX64" to actuals(actual(methodName = "getX"), actual(methodName = "setX", parameterTypes = "int", returnType = "void")),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertFalse(result.messages.contains("doesn't have a corresponding"), result.messages)
    }

    @Test
    fun `property expect with multiple targets reports missing for targets without setter`() {
        val source = SourceFile.kotlin(
            "Wrap.kt",
            """
            package test

            import io.github.mimimishkin.jni.binding.annotation.JniExpect

            class Wrap {
                @JniExpect(targets = ["linuxX64", "mingwX64"])
                var x: Int
                    external get
                    external set
            }
            """.trimIndent(),
        )
        val result = compileMulti(
            source,
            actualsByTarget = mapOf(
                "linuxX64" to actuals(actual(methodName = "getX"), actual(methodName = "setX", parameterTypes = "int", returnType = "void")),
                "mingwX64" to actuals(actual(methodName = "getX")),
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(result.messages.contains("for name 'setX' in target 'mingwX64'"), result.messages)
        assertFalse(result.messages.contains("for name 'setX' in target 'linuxX64'"), result.messages)
    }

    @Test
    fun `setter-targeting JniExpect on property with multiple targets requires setter actuals for each`() {
        val source = SourceFile.kotlin(
            "Wrap.kt",
            """
            package test

            import io.github.mimimishkin.jni.binding.annotation.JniExpect

            class Wrap {
                @set:JniExpect(targets = ["linuxX64", "mingwX64"])
                var x: Int = 0
                    external set
            }
            """.trimIndent(),
        )
        val result = compileMulti(
            source,
            actualsByTarget = mapOf(
                "linuxX64" to actuals(actual(methodName = "setX", parameterTypes = "int", returnType = "void")),
                "mingwX64" to EMPTY_ACTUALS,
            ),
        ).compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertFalse(result.messages.contains("for name 'setX' in target 'linuxX64'"), result.messages)
        assertTrue(result.messages.contains("for name 'setX' in target 'mingwX64'"), result.messages)
    }
}