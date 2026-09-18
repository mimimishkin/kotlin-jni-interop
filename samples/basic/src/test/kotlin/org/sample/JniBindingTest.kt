package org.sample

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

class JniBindingTest {
    @Test
    fun `add works`() {
        with(Главный) {
            assertEquals(5, 2 add 3)
        }
    }

    @Test
    fun `Float add works`() {
        with(Главный) {
            assertEquals(2.5f, 1f add 1.5f)
        }
    }

    @Test
    fun `sumArray works`() {
        with(Главный) {
            assertEquals(6L, byteArrayOf(1, 2, 3).sumArray())
            assertEquals(0L, (null as ByteArray?).sumArray())
        }
    }

    @Test
    fun `sumArray2 works`() {
        with(Главный) {
            assertEquals(5L, arrayOf(1.5f, 1.5f, 2f).sumArray2())
        }
    }

    @Test
    fun `editPrivateFinalField works`() {
        Главный.editPrivateFinalField("New")
        assertEquals("New (from Native)", Главный.getPrivateFinalField())
    }

    @Test
    fun `nested sayHello works`() {
        val buffer = ByteArrayOutputStream()
        Главный.Nested().sayHello(PrintStream(buffer, true))
        assertEquals("Hello from Kotlin/Native!", buffer.toString().trim())
    }

    @Test
    fun `outerFun works`() {
        assert(outerFun().startsWith("Hello from "))
    }
}
