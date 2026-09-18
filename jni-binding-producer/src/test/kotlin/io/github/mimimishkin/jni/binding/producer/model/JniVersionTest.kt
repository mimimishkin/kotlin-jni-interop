package io.github.mimimishkin.jni.binding.producer.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JniVersionTest {

    @Test
    fun `fromMajor maps known major versions`() {
        assertEquals(JniVersion.V1_1, JniVersion.fromMajor(1))
        assertEquals(JniVersion.V1_2, JniVersion.fromMajor(2))
        assertEquals(JniVersion.V1_2, JniVersion.fromMajor(3))
        assertEquals(JniVersion.V1_4, JniVersion.fromMajor(4))
        assertEquals(JniVersion.V1_4, JniVersion.fromMajor(5))
        assertEquals(JniVersion.V1_6, JniVersion.fromMajor(6))
        assertEquals(JniVersion.V1_6, JniVersion.fromMajor(7))
        assertEquals(JniVersion.V1_8, JniVersion.fromMajor(8))
        assertEquals(JniVersion.V9, JniVersion.fromMajor(9))
        assertEquals(JniVersion.V10, JniVersion.fromMajor(10))
        assertEquals(JniVersion.V19, JniVersion.fromMajor(19))
        assertEquals(JniVersion.V20, JniVersion.fromMajor(20))
        assertEquals(JniVersion.V21, JniVersion.fromMajor(21))
        assertEquals(JniVersion.V21, JniVersion.fromMajor(23))
        assertEquals(JniVersion.V24, JniVersion.fromMajor(24))
        assertEquals(JniVersion.V24, JniVersion.fromMajor(25))
        assertEquals(JniVersion.V24, JniVersion.fromMajor(26))
        assertEquals(JniVersion.V24, JniVersion.fromMajor(27))
    }

    @Test
    fun `fromMajor rejects non-positive versions`() {
        assertFailsWith<IllegalArgumentException> { JniVersion.fromMajor(0) }
        assertFailsWith<IllegalArgumentException> { JniVersion.fromMajor(-1) }
        assertFailsWith<IllegalArgumentException> { JniVersion.fromMajor(Int.MIN_VALUE) }
    }
}
