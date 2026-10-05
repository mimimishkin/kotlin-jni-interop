package org.sample

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun `hello library platform-specific function works`() {
        val osName = System.getProperty("os.name").lowercase()
        val archName = System.getProperty("os.arch").lowercase()
        val result = when {
            osName.contains("win") -> windowsHello()
            osName.contains("mac") -> macosArm64Hello()
            archName in listOf("aarch64", "arm64") -> linuxArm64Hello()
            else -> linuxX64Hello()
        }
        assertTrue(result.startsWith("Hello from "), result)
    }
}
