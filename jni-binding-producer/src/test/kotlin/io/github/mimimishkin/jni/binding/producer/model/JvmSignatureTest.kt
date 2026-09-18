package io.github.mimimishkin.jni.binding.producer.model

import kotlin.test.Test
import kotlin.test.assertEquals

class JvmSignatureTest {

    @Test
    fun `withoutNullabilityPrefix strips both prefixes`() {
        assertEquals("java.lang.String", ActualParameterType.Ref("@NonNull java.lang.String").withoutNullabilityPrefix)
        assertEquals("java.lang.String", ActualParameterType.Ref("@Nullable java.lang.String").withoutNullabilityPrefix)
        assertEquals("int", ActualParameterType.Int.withoutNullabilityPrefix)
        assertEquals("", ActualParameterType.Ref("").withoutNullabilityPrefix)
    }

    @Test
    fun `signatureType maps primitive types`() {
        assertEquals("Z", ActualParameterType.BooleanOut.signatureType)
        assertEquals("B", ActualParameterType.Byte.signatureType)
        assertEquals("C", ActualParameterType.Char.signatureType)
        assertEquals("S", ActualParameterType.Short.signatureType)
        assertEquals("I", ActualParameterType.Int.signatureType)
        assertEquals("J", ActualParameterType.Long.signatureType)
        assertEquals("F", ActualParameterType.Float.signatureType)
        assertEquals("D", ActualParameterType.Double.signatureType)
        assertEquals("V", ActualParameterType.Unit.signatureType)
    }

    @Test
    fun `signatureType maps class names with slashes`() {
        assertEquals("Ljava/lang/String;", ActualParameterType.Ref("java.lang.String").signatureType)
        assertEquals("Lcom/example/Foo;", ActualParameterType.Ref("com.example.Foo").signatureType)
    }

    @Test
    fun `signatureType maps arrays to leading brackets`() {
        assertEquals("[I", ActualParameterType.Ref("int[]").signatureType)
        assertEquals("[[I", ActualParameterType.Ref("int[][]").signatureType)
        assertEquals("[Ljava/lang/String;", ActualParameterType.Ref("java.lang.String[]").signatureType)
        assertEquals("[[Ljava/lang/String;", ActualParameterType.Ref("java.lang.String[][]").signatureType)
    }

    @Test
    fun `mapSignature combines parameters and return type`() {
        val parameters = listOf(
            ActualParameterType.Int,
            ActualParameterType.Long,
        )
        val returnType = ActualParameterType.BooleanOut
        assertEquals("(IJ)Z", ActualParameterType.mapSignature(parameters, returnType))
    }

    @Test
    fun `mapSignature handles void parameters`() {
        val parameters = emptyList<ActualParameterType>()
        val returnType = ActualParameterType.Unit
        assertEquals("()V", ActualParameterType.mapSignature(parameters, returnType))
    }
}