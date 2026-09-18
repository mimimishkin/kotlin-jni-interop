@file:OptIn(ExperimentalCompilerApi::class)

package io.github.mimimishkin.jni.binding.consumer

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import io.github.mimimishkin.jni.binding.consumer.JniBindingConsumerCommandLineProcessor
import io.github.mimimishkin.jni.binding.consumer.JniBindingConsumerRegistrar
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

    private fun actual(
        methodName: String,
        returnType: String = "int",
        parameterTypes: String = "",
        className: String = "test.Wrap",
    ): String =
        """
        {
            "needEnv": true,
            "isStatic": false,
            "className": "$className",
            "methodName": "$methodName",
            "parameterTypes": [${if (parameterTypes.isEmpty()) "" else "\"$parameterTypes\""}],
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