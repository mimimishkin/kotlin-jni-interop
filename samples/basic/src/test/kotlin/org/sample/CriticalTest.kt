package org.sample

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CriticalTest {

    @Test
    fun `sum works`() {
        assertEquals(15L, sum(intArrayOf(1, 2, 3, 4, 5)))
    }

    @Test
    fun `sum of an empty array is zero`() {
        assertEquals(0L, sum(intArrayOf()))
    }

    @Test
    fun `sum widens to long`() {
        val values = IntArray(1024) { it }
        assertEquals((0 until 1024).sumOf { it.toLong() }, sum(values))
    }

    @Test
    fun `dot works`() {
        assertEquals(32.0, dot(doubleArrayOf(1.0, 2.0, 3.0), doubleArrayOf(4.0, 5.0, 6.0)))
    }

    @Test
    fun `dot of two empty arrays is zero`() {
        assertEquals(0.0, dot(doubleArrayOf(), doubleArrayOf()))
    }

    @Test
    fun `isAllPositive works`() {
        assertTrue(isAllPositive(intArrayOf(1, 2, 3)))
        assertFalse(isAllPositive(intArrayOf(1, 0, 3)))
        assertFalse(isAllPositive(intArrayOf(1, -2, 3)))
    }

    @Test
    fun `fillTwice writes back through the array`() {
        val values = longArrayOf(1L, 2L, 3L)
        fillTwice(values)
        assertEquals(listOf(2L, 4L, 6L), values.toList())
    }

    /**
     * Calling the same critical method repeatedly must leave no critical region open: a leaked one pins the caller's
     * array for the rest of the process. Reusing one array across the calls is what makes a leak observable.
     */
    @Test
    fun `repeated calls do not leak critical regions`() {
        val values = IntArray(64) { 1 }
        repeat(10000) {
            assertEquals(64L, sum(values))
        }
    }
}