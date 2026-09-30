package io.github.mimimishkin.jni.binding.plugin.consumer

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The contract files are the only channel between the consumer compiler and this task, and the two are written by
 * different modules, so what matters here is that one shape reads both and that it agrees with the consumer on what
 * counts as implemented.
 */
class JniFunctionContractTest {

    /**
     * An entry of `expects.json` as the consumer actually writes it, with the fields this side does not use.
     */
    private val expectsJson = """
        [
          {
            "className": "java.lang.Math",
            "methodName": "max",
            "isStatic": true,
            "parameterTypes": ["int", "int"],
            "returnType": "int",
            "targets": ["mingwX64"],
            "signatureKey": "java.lang.Math#max(II)I"
          },
          {
            "className": "com.example.Native",
            "methodName": "value",
            "parameterTypes": ["@Nullable java.lang.String"],
            "returnType": "void"
          }
        ]
    """.trimIndent()

    @Test
    fun `reads an expects file, ignoring the fields only the consumer knows about`() {
        val contracts = decode(expectsJson)

        assertEquals(2, contracts.size)
        val max = contracts[0]
        assertEquals("java.lang.Math", max.className)
        assertEquals("max", max.methodName)
        assertEquals(true, max.isStatic)
        assertEquals(listOf("int", "int"), max.parameterTypes)
        assertEquals("int", max.returnType)
        assertEquals(listOf("mingwX64"), max.targets)
    }

    @Test
    fun `reads an actuals file, ignoring the fields only the producer knows about`() {
        val actualsJson = """
            [
              {
                "className": "com.example.Native",
                "methodName": "value",
                "isStatic": false,
                "parameterTypes": ["java.lang.String"],
                "returnType": "void",
                "needEnv": false,
                "source": "Main.kt"
              }
            ]
        """.trimIndent()

        val actual = decode(actualsJson).single()
        assertEquals(false, actual.isStatic)
        assertEquals(listOf("java.lang.String"), actual.parameterTypes)
        // The producer's own fields are part of the shape, not merely tolerated.
        assertEquals(false, actual.needEnv)
        assertEquals("Main.kt", actual.source)
    }

    @Test
    fun `an expects file leaves the producer's fields absent rather than defaulted`() {
        val expect = decode(expectsJson).first()

        assertEquals(null, expect.needEnv)
        assertEquals(null, expect.source)
    }

    @Test
    fun `drops the nullability prefix from the types it matches and renders`() {
        val contracts = decode(expectsJson)
        val value = contracts[1]

        assertEquals(listOf("java.lang.String"), value.parameterTypeNames)
        assertEquals("void", value.returnTypeName)
    }

    @Test
    fun `an actual with the same names and signature implements the expect`() {
        val expect = expect("com.example.Native", "value", parameters = listOf("@Nullable java.lang.String"))

        assertTrue(expect.isImplementedBy(actual(expect)))
    }

    @Test
    fun `an actual with no receiver at all implements either an instance or a static expect`() {
        val instance = expect("com.example.Native", "value", isStatic = false)
        val statics = expect("com.example.Native", "value", isStatic = true)

        assertTrue(instance.isImplementedBy(actual(instance, isStatic = null)))
        assertTrue(statics.isImplementedBy(actual(statics, isStatic = null)))
        assertFalse(instance.isImplementedBy(actual(instance, isStatic = true)))
        assertFalse(statics.isImplementedBy(actual(statics, isStatic = false)))
    }

    @Test
    fun `a differing name, receiver, parameter list or return type does not implement the expect`() {
        val expect = expect("com.example.Native", "value", isStatic = false, parameters = listOf("int"), returnType = "int")

        assertFalse(expect.isImplementedBy(actual(expect, className = "com.example.Other")))
        assertFalse(expect.isImplementedBy(actual(expect, methodName = "other")))
        assertFalse(expect.isImplementedBy(actual(expect, isStatic = true)))
        assertFalse(expect.isImplementedBy(actual(expect, parameters = listOf("long"))))
        assertFalse(expect.isImplementedBy(actual(expect, parameters = listOf("int", "int"))))
        assertFalse(expect.isImplementedBy(actual(expect, returnType = "long")))
    }

    @Test
    fun `nullability annotations on either side do not affect the match`() {
        val expect = expect("com.example.Native", "value", parameters = listOf("@NonNull java.lang.String"), returnType = "@NonNull void")

        assertTrue(expect.isImplementedBy(actual(expect, parameters = listOf("java.lang.String"), returnType = "void")))
        assertTrue(expect.isImplementedBy(actual(expect, parameters = listOf("@Nullable java.lang.String"), returnType = "@Nullable void")))
    }

    // The fields each side writes are part of the shape, so decoding has to accept both files as they are.
    private fun decode(json: String): List<JniFunctionContract> =
        Json.decodeFromString(json)

    private fun expect(
        className: String,
        methodName: String,
        isStatic: Boolean? = null,
        parameters: List<String> = emptyList(),
        returnType: String = "void",
    ) = JniFunctionContract(className, methodName, isStatic, parameters, returnType)

    private fun actual(
        of: JniFunctionContract,
        className: String = of.className,
        methodName: String = of.methodName,
        isStatic: Boolean? = of.isStatic,
        parameters: List<String> = of.parameterTypeNames,
        returnType: String = of.returnTypeName,
    ) = JniFunctionContract(className, methodName, isStatic, parameters, returnType)
}
