@file:OptIn(ExperimentalCompilerApi::class)

package io.github.mimimishkin.jni.binding.consumer

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import io.github.mimimishkin.jni.binding.consumer.model.JavaType
import io.github.mimimishkin.jni.binding.consumer.model.JniExpectDeclaration
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    ): KotlinCompilation =
        KotlinCompilation().apply {
            val actualsFile = File.createTempFile("jni-binding-actuals", ".json")
            actualsFile.writeText(actualsJson)
            this.sources = sources.toList()
            compilerPluginRegistrars = listOf(JniBindingConsumerRegistrar())
            commandLineProcessors = listOf(JniBindingConsumerCommandLineProcessor())
            pluginOptions = listOf(
                PluginOption("jni-binding-consumer", "enabled", "true"),
                PluginOption("jni-binding-consumer", "actualsFile", "test:$actualsFile"),
                PluginOption("jni-binding-consumer", "allowExtraActuals", allowExtraActuals.toString()),
            )
            inheritClassPath = true
        }

    private val EMPTY_ACTUALS = "[]"

    private fun actuals(vararg actuals: String): String =
        actuals.joinToString(prefix = "[", postfix = "]", separator = ",")

    /** Renders the comma-separated [parameterTypes] of [actual] as the JSON array `actuals.json` expects. */
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
    }

    @Test
    fun `extra actuals are reported once all expects are matched`() {
        val result = compile(
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