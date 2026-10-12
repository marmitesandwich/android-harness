package dev.harness.ui

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterPromptsTest {

    @Test
    fun `there is a large pool of distinct prompts`() {
        assertTrue(StarterPrompts.size >= 30)
        assertEquals(StarterPrompts.size, StarterPrompts.toSet().size)
        assertTrue(StarterPrompts.none { it.isBlank() })
    }

    @Test
    fun `prompts stay short enough for the empty state`() {
        val tooLong = StarterPrompts.filter { it.length > 70 }
        assertTrue("too long: $tooLong", tooLong.isEmpty())
    }

    @Test
    fun `picks three distinct prompts from the pool`() {
        val picked = pickStarters(random = Random(1))
        assertEquals(3, picked.size)
        assertEquals(3, picked.toSet().size)
        assertTrue(StarterPrompts.containsAll(picked))
    }

    @Test
    fun `different seeds give different picks and the same seed repeats`() {
        assertEquals(pickStarters(random = Random(7)), pickStarters(random = Random(7)))
        val distinct = (1..20).map { pickStarters(random = Random(it)) }.toSet()
        assertNotEquals(1, distinct.size)
    }

    @Test
    fun `asking for more than exist returns them all`() {
        assertEquals(2, pickStarters(count = 5, from = listOf("a", "b")).size)
    }
}
