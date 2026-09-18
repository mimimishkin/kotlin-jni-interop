package org.windowsregistry

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.SAME_THREAD)
class RegistryTest {

    @Test
    fun `create, write and read string values`() {
        freshKey().use { key ->
            assertTrue(key.setString("Name", "Jon Doe"))
            assertTrue(key.setString("Path", "%USERPROFILE%", expand = true))

            assertEquals("Jon Doe", key.getString("Name"))
            assertEquals("%USERPROFILE%", key.getString("Path"))
            assertEquals(Registry.ValueType.SZ, key.valueType("Name"))
            assertEquals(Registry.ValueType.EXPAND_SZ, key.valueType("Path"))
            assertNull(key.getString("Missing"))
        }
    }

    @Test
    fun `read and write int and long values`() {
        freshKey().use { key ->
            assertTrue(key.setInt("Int", -42))
            assertTrue(key.setLong("Long", 123456789012345L))

            assertEquals(-42, key.getInt("Int"))
            assertEquals(123456789012345L, key.getLong("Long"))
            assertEquals(Registry.ValueType.DWORD, key.valueType("Int"))
            assertEquals(Registry.ValueType.QWORD, key.valueType("Long"))
            assertEquals(0, key.getInt("Missing"))
            assertEquals(0L, key.getLong("Missing"))
        }
    }

    @Test
    fun `read and write binary values`() {
        freshKey().use { key ->
            val payload = byteArrayOf(0x00, 0x01, 0x7F, -1, 0x42)
            assertTrue(key.setBinary("Blob", payload))
            assertEquals(Registry.ValueType.BINARY, key.valueType("Blob"))
            assertContentEquals(payload, key.getBinary("Blob"))
            assertNull(key.getBinary("Missing"))
        }
    }

    @Test
    fun `enumerate subkeys and values`() {
        freshKey().use { key ->
            key["Apples"] = "1"
            key["Zebras"] = "2"

            Registry.create(Registry.RootKey.CURRENT_USER, "$TEST_KEY\\Nested").close()

            assertEquals(listOf("Apples", "Zebras"), key.values().sorted())
            assertEquals(listOf("Nested"), key.subKeys())
        }
    }

    @Test
    fun `delete value and key`() {
        Registry.delete(Registry.RootKey.CURRENT_USER, TEST_KEY)
        Registry.create(Registry.RootKey.CURRENT_USER, TEST_KEY).use { key ->
            key["ToDelete"] = "1"
            assertTrue(key.deleteValue("ToDelete"))
            assertFalse(key.deleteValue("ToDelete"))
        }

        Registry.create(Registry.RootKey.CURRENT_USER, "$TEST_KEY\\Nested").use { }

        assertTrue(Registry.delete(Registry.RootKey.CURRENT_USER, TEST_KEY))
        assertNull(Registry.open(Registry.RootKey.CURRENT_USER, TEST_KEY))
    }

    private fun freshKey(): Registry.Key {
        Registry.delete(Registry.RootKey.CURRENT_USER, TEST_KEY)
        return Registry.create(Registry.RootKey.CURRENT_USER, TEST_KEY)
    }

    companion object {
        private const val TEST_KEY = "Software\\JniBindingWindowsRegistrySample"
    }
}