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

class PromptHistoryTest {

    private fun history(vararg prompts: String) = prompts.fold(emptyList<PastPrompt>()) { h, p -> addToHistory(h, p) }

    @Test
    fun `a repeat bumps its count and moves to the front`() {
        assertEquals(
            listOf(PastPrompt("a b", 2), PastPrompt("c d", 1)),
            history("a b", "c d", "a b"),
        )
    }

    @Test
    fun `blank prompts and curated examples are not kept`() {
        assertEquals(emptyList<PastPrompt>(), history("   ", StarterPrompts.first()))
    }

    @Test
    fun `over the cap the least-used oldest entry goes, not the newest`() {
        val habit = history("my habit", "my habit", "my habit")
        val full = (1..MAX_PROMPT_HISTORY - 1).fold(habit) { h, i -> addToHistory(h, "one-off $i") }
        assertEquals(MAX_PROMPT_HISTORY, full.size)

        val next = addToHistory(full, "newcomer")
        assertEquals(MAX_PROMPT_HISTORY, next.size)
        assertEquals("newcomer", next.first().text)
        assertTrue(next.any { it.text == "my habit" && it.count == 3 })
        assertTrue("oldest one-off should be dropped", next.none { it.text == "one-off 1" })
    }

    @Test
    fun `one of the three picks is a past prompt when history exists`() {
        repeat(20) { seed ->
            val picked = pickStarters(history = history("my own question"), random = Random(seed))
            assertEquals(3, picked.size)
            assertEquals(3, picked.toSet().size)
            assertTrue(picked.contains("my own question"))
        }
    }

    @Test
    fun `frequent prompts are picked more often`() {
        val past = listOf(PastPrompt("often", 9), PastPrompt("rarely", 1))
        val counts = (1..2000).map { seed -> pickStarters(history = past, random = Random(seed)) }
            .let { picks -> picks.count { "often" in it } to picks.count { "rarely" in it } }
        assertTrue("often=${counts.first} rarely=${counts.second}", counts.first > counts.second * 4)
        assertTrue(counts.second > 0)
    }

    @Test
    fun `with no usable history all picks are curated`() {
        val past = listOf(PastPrompt("x".repeat(200), 5), PastPrompt(StarterPrompts.first(), 5), PastPrompt("zero", 0))
        val picked = pickStarters(history = past, random = Random(3))
        assertTrue(StarterPrompts.containsAll(picked))
        assertEquals(3, picked.size)
    }
}
