package com.gallopkeyboard.ime.suggestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeWordResolverTest {

    private fun entry(word: String, freq: Int) =
        WordEntry(word, freq, word.lowercase())

    @Test
    fun `helo resolves to hello when candidate present`() {
        val candidates = listOf(
            entry("hello", 210),
            entry("help", 180),
            entry("here", 170),
        )
        assertEquals("hello", SwipeWordResolver.resolve("helo", candidates))
    }

    @Test
    fun `short path returned unchanged`() {
        val candidates = listOf(entry("hello", 210))
        assertEquals("h", SwipeWordResolver.resolve("h", candidates))
        assertEquals("", SwipeWordResolver.resolve("", candidates))
    }

    @Test
    fun `garbage path returns raw when no subsequence match`() {
        val candidates = listOf(
            entry("hello", 210),
            entry("help", 180),
        )
        assertEquals("zxq", SwipeWordResolver.resolve("zxq", candidates))
    }

    @Test
    fun `ambiguous top two returns raw path`() {
        val candidates = listOf(
            entry("the", 200),
            entry("tee", 195),
        )
        assertEquals("teh", SwipeWordResolver.resolve("teh", candidates))
    }

    @Test
    fun `clear winner returns dictionary word`() {
        val candidates = listOf(
            entry("hello", 210),
            entry("help", 50),
        )
        assertEquals("hello", SwipeWordResolver.resolve("helo", candidates))
    }

    @Test
    fun `isSubsequence handles empty and full match`() {
        assertTrue(SwipeWordResolver.isSubsequence("", "hello"))
        assertTrue(SwipeWordResolver.isSubsequence("helo", "hello"))
        assertFalse(SwipeWordResolver.isSubsequence("hxlo", "hello"))
    }

    @Test
    fun `empty candidates returns raw path`() {
        assertEquals("helo", SwipeWordResolver.resolve("helo", emptyList()))
    }
}
