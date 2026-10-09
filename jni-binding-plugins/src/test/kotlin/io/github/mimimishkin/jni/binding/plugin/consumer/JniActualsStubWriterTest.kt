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
    fun `writes one file per class, then the shared type file last`() {
        val result = writer.write(
            listOf(
                expect("com.example.Native", "value", isStatic = false, returnType = "boolean"),
                expect("com.example.Other", "close", isStatic = false, returnType = "void"),
            )
        )

        assertEquals(3, result.files.size)
        assertEquals(2, result.written)
        assertEquals(
            "com${File.separator}example${File.separator}Native.kt",
            result.files[0].path.removePrefix(sourceRoot.path + File.separator),
        )
        assertEquals(
            "com${File.separator}example${File.separator}Other.kt",
            result.files[1].path.removePrefix(sourceRoot.path + File.separator),
        )
        assertEquals(
            "io${File.separator}github${File.separator}mimimishkin${File.separator}jni${File.separator}binding${File.separator}generated${File.separator}JniTypes.kt",
            result.files[2].path.removePrefix(sourceRoot.path + File.separator),
        )
    }

    @Test
    fun `a static expect becomes a JClass extension and an instance one the owner's own type`() {
        writer.write(
            listOf(
                expect("com.example.Native", "of", isStatic = true, returnType = "com.example.Native"),
                expect("com.example.Native", "value", isStatic = false, returnType = "boolean"),
            )
        )

        val text = generated("com.example.Native")
        assertTrue("public fun JClass.of(): JComExampleNative = TODO()" in text, text)
        assertTrue("public fun JComExampleNative.`value`(): JBoolean = TODO()" in text, text)
    }

    @Test
    fun `writes the names, the receiver type and a TODO body`() {
        writer.write(listOf(expect("com.example.Native", "value", isStatic = false, returnType = "boolean")))

        // Everything here is kotlinpoet's rendering: the imports it collects and sorts, the escaping of a name that is
        // a keyword (`value`), the layout of an annotation's arguments, the explicit `public` of a top-level
        // declaration, and the expression body a `return` is folded into. The receiver's type is the generated
        // typealias, which is how the stub carries the JVM name the producer reads back.
        assertEquals(
            """
            package com.example

            import io.github.mimimishkin.jni.binding.JBoolean
            import io.github.mimimishkin.jni.binding.JniEnv
            import io.github.mimimishkin.jni.binding.`annotation`.JniActual
            import io.github.mimimishkin.jni.binding.generated.JComExampleNative

            @JniActual(
                className = "com.example.Native",
                methodName = "value",
            )
            context(env: JniEnv)
            public fun JComExampleNative.`value`(): JBoolean = TODO()

            """.trimIndent(),
            generated("com.example.Native"),
        )
    }

    @Test
    fun `the shared type file declares a typealias and its opaque per class`() {
        writer.write(listOf(expect("com.example.Native", "value", isStatic = false, returnType = "boolean")))

        assertEquals(
            """
            package io.github.mimimishkin.jni.binding.generated

            import io.github.mimimishkin.jni.binding.JRef
            import io.github.mimimishkin.jni.binding._jobject
            import io.github.mimimishkin.jni.binding.`annotation`.WithJvmType
            import kotlinx.cinterop.NativePtr

            public typealias JComExampleNative = @WithJvmType("com.example.Native") JRef<_j_com_example_native>

            public open class _j_com_example_native(
                rawPtr: NativePtr,
            ) : _jobject(rawPtr)

            """.trimIndent(),
            generatedTypes(),
        )
    }

    @Test
    fun `an opaque type extends the generated opaque of its superclass`() {
        writer.write(
            listOf(
                expect(
                    "com.example.Child",
                    "value",
                    isStatic = false,
                    returnType = "boolean",
                    superClasses = mapOf("com.example.Child" to "com.example.Base", "com.example.Base" to "java.lang.Object"),
                ),
            )
        )

        val text = generatedTypes()
        // The parent of `Child` exists as a generated opaque, because the smart cast the caller writes needs it.
        assertTrue("public typealias JComExampleBase = @WithJvmType(\"com.example.Base\") JRef<_j_com_example_base>" in text, text)
        assertTrue("public open class _j_com_example_child(" in text, text)
        assertTrue(") : _j_com_example_base(rawPtr)" in text, text)
        // The chain ends at a well-known opaque, which is not redeclared.
        assertTrue(") : _jobject(rawPtr)" in text, text)
        assertTrue("typealias JObject" !in text, text)
    }

    @Test
    fun `a reference array gets a JObjectArray typealias annotated with the JVM array name`() {
        writer.write(listOf(expect("com.example.Native", "fill", isStatic = false, parameters = listOf("com.example.Thing[]"))))

        val text = generatedTypes()
        assertTrue("@WithJvmType(\"com.example.Thing[]\")" in text, text)
        assertTrue("public typealias JComExampleThingArray = @WithJvmType(\"com.example.Thing[]\") JObjectArray" in text, text)
        assertTrue("import io.github.mimimishkin.jni.binding.JObjectArray" in text, text)
    }

    @Test
    fun `a generated name is the JVM name in PascalCase under a J prefix`() {
        writer.write(
            listOf(
                expect(
                    "com.example.my.app.Native",
                    "use",
                    isStatic = false,
                    parameters = listOf("com.example.my.app.Item"),
                )
            )
        )

        val text = generatedTypes()
        assertTrue(
            "public typealias JComExampleMyAppNative = @WithJvmType(\"com.example.my.app.Native\") JRef<_j_com_example_my_app_native>" in text,
            text,
        )
        assertTrue(
            "public typealias JComExampleMyAppItem = @WithJvmType(\"com.example.my.app.Item\") JRef<_j_com_example_my_app_item>" in text,
            text,
        )
    }

    @Test
    fun `an opaque type is the typealias name in snake case`() {
        writer.write(listOf(expect("com.example.MainKt", "use", isStatic = false, returnType = "int")))

        val text = generatedTypes()
        assertTrue(
            "public typealias JComExampleMainKt = @WithJvmType(\"com.example.MainKt\") JRef<_j_com_example_main_kt>" in text,
            text,
        )
    }

    @Test
    fun `a well-known package is left out of a generated name`() {
        writer.write(
            listOf(
                expect(
                    "com.example.Native",
                    "use",
                    isStatic = false,
                    parameters = listOf("java.util.List", "kotlin.uuid.Uuid", "java.io.File"),
                )
            )
        )

        val text = generatedTypes()
        assertTrue("public typealias JList = @WithJvmType(\"java.util.List\") JRef<_j_list>" in text, text)
        assertTrue("public typealias JUuid = @WithJvmType(\"kotlin.uuid.Uuid\") JRef<_j_uuid>" in text, text)
        assertTrue("public typealias JFile = @WithJvmType(\"java.io.File\") JRef<_j_file>" in text, text)
    }

    @Test
    fun `a boxed-primitive array is named with a Ref before Array`() {
        writer.write(
            listOf(
                expect(
                    "com.example.Native",
                    "use",
                    isStatic = false,
                    parameters = listOf("java.lang.Integer[]", "java.lang.Boolean[]"),
                )
            )
        )

        val text = generatedTypes()
        assertTrue(
            "public typealias JIntegerRefArray = @WithJvmType(\"java.lang.Integer[]\") JObjectArray" in text,
            text,
        )
        assertTrue(
            "public typealias JBooleanRefArray = @WithJvmType(\"java.lang.Boolean[]\") JObjectArray" in text,
            text,
        )
    }

    @Test
    fun `a reference array appends Array to its element's name`() {
        writer.write(
            listOf(expect("com.example.Native", "use", isStatic = false, parameters = listOf("com.example.my.app.Item[]")))
        )

        val text = generatedTypes()
        assertTrue(
            "public typealias JComExampleMyAppItemArray = @WithJvmType(\"com.example.my.app.Item[]\") JObjectArray" in text,
            text,
        )
    }

    @Test
    fun `a well-known JVM type is never redeclared`() {
        writer.write(listOf(expect("com.example.Native", "greet", isStatic = true, parameters = listOf("java.lang.String"), returnType = "java.lang.String")))

        val text = generatedTypes()
        assertTrue("typealias JString" !in text, text)
    }

    @Test
    fun `a stub asks for a JniEnv, which is what its body needs to reach the JVM`() {
        // Building the `JString` behind a `java.lang.String` return value needs an environment, and the producer
        // accepts exactly this one context parameter. Without it the body cannot be filled in as written.
        writer.write(listOf(expect("com.example.Native", "greet", isStatic = true, returnType = "java.lang.String")))

        val text = generated("com.example.Native")
        assertTrue("import io.github.mimimishkin.jni.binding.JniEnv" in text, text)
        assertTrue("context(env: JniEnv)" in text, text)
        assertTrue("public fun JClass.greet(): JString = TODO()" in text, text)
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
            assertEquals(nativeType, JniActualsStubWriter.kotlinTypeOf(jvmType).simpleName, jvmType)
        }
    }

    @Test
    fun `a Unit-returning expect imports nothing for its return type`() {
        writer.write(listOf(expect("com.example.Native", "close", isStatic = false, returnType = "void")))

        val text = generated("com.example.Native")
        assertTrue("public fun JComExampleNative.close(): Unit = TODO()" in text, text)
        assertTrue("binding.Unit" !in text, text)
    }

    @Test
    fun `a class of a JDK package is declared in it`() {
        // The JDK's own classes are the most common thing to bind, and `java.lang` is an ordinary Kotlin package
        // name, however much of it reads like a keyword.
        writer.write(listOf(expect("java.lang.Math", "max", isStatic = true, parameters = listOf("int", "int"), returnType = "int")))

        val text = generated("java.lang.Math")
        assertTrue("package java.lang" in text, text)
        assertTrue("public fun JClass.max(p0: JInt, p1: JInt): JInt = TODO()" in text, text)
    }

    @Test
    fun `a nested class gets a file name that can be written to`() {
        writer.write(listOf(expect("com.example.Outer${'$'}Inner", "value", isStatic = false, returnType = "int")))

        assertTrue(sourceRoot.resolve("com/example/Outer_Inner.kt").isFile)
    }

    @Test
    fun `a method name that is not a Kotlin identifier is declared escaped`() {
        writer.write(listOf(expect("com.example.Native", "get-value", isStatic = false, returnType = "int")))

        // kotlinpoet escapes rather than mangles, so the declaration keeps the JVM name the user will recognize - and
        // stays a name no other JVM method can collide with.
        val text = generated("com.example.Native")
        assertTrue("public fun JComExampleNative.`get-value`(): JInt = TODO()" in text, text)
        assertTrue("get_value" !in text, text)
        // The JVM name is what the producer binds, and it travels in the annotation as well.
        assertTrue("""methodName = "get-value",""" in text, text)
    }

    @Test
    fun `a method name that is a keyword is escaped`() {
        writer.write(listOf(expect("com.example.Native", "object", isStatic = false, returnType = "int")))

        assertTrue("public fun JComExampleNative.`object`(): JInt = TODO()" in generated("com.example.Native"))
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

            import io.github.mimimishkin.jni.binding.JBoolean
            import io.github.mimimishkin.jni.binding.JObject

            @JniActual(className = "com.example.Native", methodName = "value")
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

            import io.github.mimimishkin.jni.binding.JObject

            fun helper(): Int = 42

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
        // `JObject` was imported already (the user's own file used it); the rest are new.
        assertTrue("import io.github.mimimishkin.jni.binding.JBoolean" in imports, file.readText())
        assertTrue("import io.github.mimimishkin.jni.binding.JObject" in imports, file.readText())
        assertTrue("import io.github.mimimishkin.jni.binding.`annotation`.JniActual" in imports, file.readText())
        // The user's own declaration is still there, above the appended ones.
        assertTrue("fun helper(): Int = 42" in file.readText())
        assertTrue("public fun JComExampleNative.`value`(): JBoolean = TODO()" in file.readText())
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
        assertEquals(2, Regex("""\bfun\b""").findAll(text).count(), text)
        assertTrue("public fun JComExampleNative.value2(): JBoolean = TODO()" in text, text)
    }

    @Test
    fun `writes a file for a class of the default package at the root of the source tree`() {
        writer.write(listOf(expect("Native", "value", isStatic = false, returnType = "boolean")))

        val text = sourceRoot.resolve("Native.kt").readText()
        assertTrue("package " !in text, text)
        assertTrue("public fun JNative.`value`(): JBoolean = TODO()" in text, text)
    }

    @Test
    fun `a critical expect declares a length and a pointer per array, and nothing else`() {
        // A critical native only ever gets primitives, and an array arrives as a `(length, pointer)` pair - so this
        // stub is the one shape that brings `kotlinx.cinterop` into a generated file.
        writer.write(
            listOf(
                expect("com.example.Native", "sum", isStatic = true, parameters = listOf("int[]", "long"), returnType = "long", isCritical = true),
                expect("com.example.Native", "isAllPositive", isStatic = true, parameters = listOf("boolean[]"), returnType = "boolean", isCritical = true),
            )
        )

        val text = generated("com.example.Native")
        // A signature past kotlinpoet's column limit is wrapped onto one parameter per line, the way any other
        // kotlinpoet-generated code is.
        assertTrue(
            """
            @JniActual(
                className = "com.example.Native",
                methodName = "sum",
            )
            @CriticalNative
            public fun sum(
                p0Length: Int,
                p0: CArrayPointer<IntVar>,
                p1: Long,
            ): Long = TODO()
            """.trimIndent() in text,
            text,
        )
        // A `jboolean` is an unsigned byte, which is what `UByteVar` says.
        assertTrue("public fun isAllPositive(p0Length: Int, p0: CArrayPointer<UByteVar>): Boolean = TODO()" in text, text)
        // The language prelude needs no import of its own.
        assertTrue("import kotlin." !in text, text)
    }

    @Test
    fun `maps a critical array to the cinterop variable type that carries it`() {
        val types = mapOf(
            "boolean[]" to "UByteVar",
            "byte[]" to "ByteVar",
            "char[]" to "UShortVar",
            "short[]" to "ShortVar",
            "int[]" to "IntVar",
            "long[]" to "LongVar",
            "float[]" to "FloatVar",
            "double[]" to "DoubleVar",
        )

        for ((jvmType, varType) in types) {
            assertEquals(varType, JniActualsStubWriter.criticalVarType(jvmType)?.simpleName, jvmType)
        }
        assertEquals(null, JniActualsStubWriter.criticalVarType("java.lang.Object[]"), "not a primitive array")
    }

    @Test
    fun `a stub names its parameters after the ones the expect declared`() {
        writer.write(
            listOf(
                expect(
                    "com.example.Native",
                    "max",
                    isStatic = true,
                    parameters = listOf("int", "int"),
                    parameterNames = listOf("a", "b"),
                    returnType = "int",
                )
            )
        )

        assertTrue("public fun JClass.max(a: JInt, b: JInt): JInt = TODO()" in generated("com.example.Native"))
    }

    @Test
    fun `an extension receiver is declared as a parameter named receiver`() {
        // A receiver is not a value parameter of the expect, but it is one of the JVM method's arguments - so the stub
        // declares it as an ordinary parameter, and it is the one argument whose name cannot come from the source.
        writer.write(
            listOf(
                expect(
                    "com.example.NativeKt",
                    "times",
                    isStatic = true,
                    parameters = listOf("int", "float"),
                    parameterNames = listOf("receiver", "times"),
                    returnType = "int",
                )
            )
        )

        // kotlinpoet escapes `receiver`, a soft keyword, but the declared identifier is still `receiver`.
        assertTrue("public fun JClass.times(`receiver`: JInt, times: JFloat): JInt = TODO()" in generated("com.example.NativeKt"), generated("com.example.NativeKt"))
    }

    @Test
    fun `a critical native names its parameters too`() {
        writer.write(
            listOf(
                expect(
                    "com.example.Native",
                    "sum",
                    isStatic = true,
                    parameters = listOf("int[]", "long"),
                    parameterNames = listOf("values", "count"),
                    returnType = "long",
                    isCritical = true,
                )
            )
        )

        val text = generated("com.example.Native")
        // The array is still split into a length and a pointer, now under the array parameter's own name.
        assertTrue("valuesLength: Int" in text, text)
        assertTrue("values: CArrayPointer<IntVar>" in text, text)
        assertTrue("count: Long" in text, text)
    }

    private fun expect(
        className: String,
        methodName: String,
        isStatic: Boolean? = null,
        parameters: List<String> = emptyList(),
        parameterNames: List<String> = emptyList(),
        returnType: String = "void",
        isCritical: Boolean? = null,
        superClasses: Map<String, String?> = emptyMap(),
    ) = JniFunctionContract(
        className,
        methodName,
        isStatic,
        parameters,
        returnType,
        isCritical = isCritical,
        superClasses = superClasses,
        parameterNames = parameterNames,
    )

    private fun generatedTypes(): String {
        val file = sourceRoot.resolve("io/github/mimimishkin/jni/binding/generated/JniTypes.kt")
        assertTrue(file.isFile, "$file was not written")
        return file.readText()
    }

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
