package io.github.mimimishkin.jni.binding.plugin.consumer

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The generated text is the product here: it lands in the user's source tree, and its whole purpose is to be compiled
 * and then filled in. So the assertions are on the exact output, and on the promise that re-running over a file the
 * generator itself wrote - or over a file the user edited - changes nothing that was already there.
 */
class JniActualsStubWriterTest {

    private val sourceRoot: File = createTempDirectory("jni-actuals-stub-writer").toFile()
    private val writer = JniActualsStubWriter(sourceRoot)

    @AfterTest
    fun deleteGeneratedTree() {
        sourceRoot.deleteRecursively()
    }

    @Test
    fun `writes one file per class, in the package of that class`() {
        val result = writer.write(
            listOf(
                expect("com.example.Native", "value", isStatic = false, returnType = "boolean"),
                expect("com.example.Other", "close", isStatic = false, returnType = "void"),
            )
        )

        assertEquals(2, result.files.size)
        assertEquals(2, result.written)
        assertEquals(
            "com${File.separator}example${File.separator}Native.kt",
            result.files[0].path.removePrefix(sourceRoot.path + File.separator),
        )
        assertEquals(
            "com${File.separator}example${File.separator}Other.kt",
            result.files[1].path.removePrefix(sourceRoot.path + File.separator),
        )
    }

    @Test
    fun `a static expect becomes a JClass extension and an instance one a JObject extension`() {
        writer.write(
            listOf(
                expect("com.example.Native", "of", isStatic = true, returnType = "com.example.Native"),
                expect("com.example.Native", "value", isStatic = false, returnType = "boolean"),
            )
        )

        val text = generated("com.example.Native")
        assertTrue("fun JClass.of(): JObject = TODO()" in text, text)
        assertTrue("fun JObject.value(): JBoolean = TODO()" in text, text)
    }

    @Test
    fun `writes the names, the signature and a TODO body`() {
        writer.write(listOf(expect("com.example.Native", "value", isStatic = false, returnType = "boolean")))

        assertEquals(
            """
            package com.example

            import io.github.mimimishkin.jni.binding.JBoolean
            import io.github.mimimishkin.jni.binding.JObject
            import io.github.mimimishkin.jni.binding.JniEnv
            import io.github.mimimishkin.jni.binding.annotation.JniActual
            import io.github.mimimishkin.jni.binding.annotation.WithJvmSignature

            @JniActual(className = "com.example.Native", methodName = "value")
            @WithJvmSignature(
                parameterTypes = [],
                returnType = "boolean",
            )
            context(env: JniEnv)
            fun JObject.value(): JBoolean = TODO()

            """.trimIndent(),
            generated("com.example.Native"),
        )
    }

    @Test
    fun `a stub asks for a JniEnv, which is what its body needs to reach the JVM`() {
        // Building the `JString` behind a `java.lang.String` return value needs an environment, and the producer
        // accepts exactly this one context parameter. Without it the body cannot be filled in as written.
        writer.write(listOf(expect("com.example.Native", "greet", isStatic = true, returnType = "java.lang.String")))

        val text = generated("com.example.Native")
        assertTrue("import io.github.mimimishkin.jni.binding.JniEnv" in text, text)
        assertTrue("context(env: JniEnv)" in text, text)
        // Between the annotations that carry the binding and the declaration they belong to.
        val annotations = Regex("""@WithJvmSignature\([^)]*\)""").find(text)!!.range.last + 1
        assertTrue("context(env: JniEnv)" in text.substring(annotations), text)
    }

    @Test
    fun `maps a JVM type to the native type that carries it`() {
        val types = mapOf(
            "void" to "Unit",
            "boolean" to "JBoolean",
            "byte" to "JByte",
            "char" to "JChar",
            "short" to "JShort",
            "int" to "JInt",
            "long" to "JLong",
            "float" to "JFloat",
            "double" to "JDouble",
            "java.lang.String" to "JString",
            "java.lang.Object" to "JObject",
            "boolean[]" to "JBooleanArray",
            "int[]" to "JIntArray",
            "double[]" to "JDoubleArray",
            // Any other array, and any multi-dimensional one, is an object rather than a primitive array.
            "com.example.Thing[]" to "JObjectArray",
            "int[][]" to "JObjectArray",
            // Anything else is passed along as a pointer.
            "com.example.Thing" to "JObject",
        )

        for ((jvmType, nativeType) in types) {
            assertEquals(nativeType, JniActualsStubWriter.kotlinTypeOf(jvmType), jvmType)
        }
    }

    @Test
    fun `a Unit-returning expect imports nothing for its return type`() {
        writer.write(listOf(expect("com.example.Native", "close", isStatic = false, returnType = "void")))

        val text = generated("com.example.Native")
        assertTrue("fun JObject.close(): Unit = TODO()" in text, text)
        assertTrue("binding.Unit" !in text, text)
    }

    @Test
    fun `a class of a JDK package is declared in it`() {
        // The JDK's own classes are the most common thing to bind, and `java.lang` is an ordinary Kotlin package
        // name, however much of it reads like a keyword.
        writer.write(listOf(expect("java.lang.Math", "max", isStatic = true, parameters = listOf("int", "int"), returnType = "int")))

        val text = generated("java.lang.Math")
        assertTrue("package java.lang" in text, text)
        assertTrue("fun JClass.max(p0: JInt, p1: JInt): JInt = TODO()" in text, text)
    }

    @Test
    fun `a nested class gets a file name that can be written to`() {
        writer.write(listOf(expect("com.example.Outer${'$'}Inner", "value", isStatic = false, returnType = "int")))

        assertTrue(sourceRoot.resolve("com/example/Outer_Inner.kt").isFile)
    }

    @Test
    fun `a method name that is not a Kotlin identifier is declared under a mangled one`() {
        writer.write(listOf(expect("com.example.Native", "get-value", isStatic = false, returnType = "int")))

        val text = generated("com.example.Native")
        assertTrue("fun JObject.`get-value`()..." !in text, text)
        assertTrue("fun JObject.get_value(): JInt = TODO()" in text, text)
        // The JVM name is the one the producer binds, and it travels in the annotation, not in the declaration's name.
        assertTrue("""methodName = "get-value")""" in text, text)
    }

    @Test
    fun `a method name that is a hard keyword is escaped rather than mangled`() {
        writer.write(listOf(expect("com.example.Native", "object", isStatic = false, returnType = "int")))

        assertTrue("fun JObject.`object`(): JInt = TODO()" in generated("com.example.Native"))
    }

    @Test
    fun `a declaration that the file already has is not written again`() {
        val first = expect("com.example.Native", "value", isStatic = false, returnType = "boolean")
        writer.write(listOf(first))
        val afterFirstRun = generated("com.example.Native")

        val result = writer.write(listOf(first))

        assertTrue(result.files.isEmpty())
        assertEquals(0, result.written)
        assertEquals(1, result.alreadyDeclared)
        assertEquals(afterFirstRun, generated("com.example.Native"))
    }

    @Test
    fun `adds to a file the user wrote, keeping its content and adding only what is missing`() {
        sourceRoot.resolve("com/example").mkdirs()
        val file = sourceRoot.resolve("com/example/Native.kt")
        file.writeText(
            """
            package com.example

            import io.github.mimimishkin.jni.binding.JInt
            import io.github.mimimishkin.jni.binding.JObject

            @JniActual(className = "com.example.Native", methodName = "value")
            @WithJvmSignature(
                parameterTypes = [],
                returnType = "boolean",
            )
            fun JObject.value(): JBoolean = TODO()

            fun helper(): Int = 42

            """.trimIndent()
        )

        writer.write(listOf(expect("com.example.Native", "value", isStatic = false, returnType = "boolean")))

        // `value` is already declared, so the only thing left to do is nothing at all.
        val text = file.readText()
        assertEquals(1, Regex("""methodName = "value"""").findAll(text).count(), text)
        assertTrue("fun helper(): Int = 42" in text, text)
    }

    @Test
    fun `merges the imports of an appended stub into the existing import block, without duplicating any`() {
        sourceRoot.resolve("com/example").mkdirs()
        val file = sourceRoot.resolve("com/example/Native.kt")
        file.writeText(
            """
            package com.example

            import io.github.mimimishkin.jni.binding.JInt
            import io.github.mimimishkin.jni.binding.JObject

            fun helper(): JInt = 42

            """.trimIndent()
        )

        writer.write(
            listOf(
                expect("com.example.Native", "value", isStatic = false, returnType = "boolean"),
                expect("com.example.Native", "width", isStatic = false, returnType = "int"),
            )
        )

        val lines = file.readLines()
        val imports = lines.filter { it.startsWith("import ") }
        assertEquals(imports.distinct(), imports, "imports must not be duplicated: $imports")
        // `JObject` was imported already; `JBoolean` and the annotations are new.
        assertTrue("import io.github.mimimishkin.jni.binding.JBoolean" in imports, file.readText())
        assertTrue("import io.github.mimimishkin.jni.binding.JObject" in imports, file.readText())
        assertTrue("import io.github.mimimishkin.jni.binding.annotation.JniActual" in imports, file.readText())
        // The user's own declaration is still there, above the appended ones.
        assertTrue("fun helper(): JInt = 42" in file.readText())
        assertTrue("fun JObject.value(): JBoolean = TODO()" in file.readText())
    }

    @Test
    fun `puts the imports into a file that has none right after the package directive`() {
        sourceRoot.resolve("com/example").mkdirs()
        val file = sourceRoot.resolve("com/example/Native.kt")
        file.writeText(
            """
            package com.example

            fun helper(): Int = 42

            """.trimIndent()
        )

        writer.write(listOf(expect("com.example.Native", "value", isStatic = false, returnType = "boolean")))

        val lines = file.readLines()
        val packageIndex = lines.indexOfFirst { it.startsWith("package ") }
        assertTrue(lines[packageIndex + 1].isBlank(), lines.toString())
        assertTrue(lines[packageIndex + 2].startsWith("import "), lines.toString())
        assertTrue("fun helper(): Int = 42" in file.readText())
    }

    @Test
    fun `a stub never collides with a function the file already declares`() {
        sourceRoot.resolve("com/example").mkdirs()
        val file = sourceRoot.resolve("com/example/Native.kt")
        file.writeText(
            """
            package com.example

            fun JObject.value(): JBoolean = 42

            """.trimIndent()
        )

        writer.write(listOf(expect("com.example.Native", "value", isStatic = false, returnType = "boolean")))

        val text = file.readText()
        assertEquals(2, Regex("fun JObject").findAll(text).count(), text)
        assertTrue("fun JObject.value2(): JBoolean = TODO()" in text, text)
    }

    @Test
    fun `writes a file for a class of the default package at the root of the source tree`() {
        writer.write(listOf(expect("Native", "value", isStatic = false, returnType = "boolean")))

        val text = sourceRoot.resolve("Native.kt").readText()
        assertTrue("package " !in text, text)
        assertTrue("fun JObject.value(): JBoolean = TODO()" in text, text)
    }

    private fun expect(
        className: String,
        methodName: String,
        isStatic: Boolean? = null,
        parameters: List<String> = emptyList(),
        returnType: String = "void",
    ) = JniFunctionContract(className, methodName, isStatic, parameters, returnType)

    private fun generated(className: String): String {
        val simpleName = className.substringAfterLast('.').replace('$', '_')
        val packageName = className.substringBeforeLast('.', "").replace('.', '/')
        val file = if (packageName.isEmpty()) {
            sourceRoot.resolve("$simpleName.kt")
        } else {
            sourceRoot.resolve("$packageName/$simpleName.kt")
        }
        assertTrue(file.isFile, "$file was not written")
        return file.readText()
    }
}
