package com.gallopkeyboard.ime.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipePathHelperTest {

    @Test
    fun `dedupeConsecutive removes only consecutive duplicates`() {
        assertEquals(
            listOf('h', 'e', 'l', 'o'),
            SwipePathHelper.dedupeConsecutive(listOf('h', 'e', 'l', 'l', 'o')),
        )
    }

    @Test
    fun `dedupeConsecutive keeps non-consecutive repeats`() {
        assertEquals(
            listOf('a', 'b', 'a'),
            SwipePathHelper.dedupeConsecutive(listOf('a', 'b', 'a')),
        )
    }

    @Test
    fun `pathToWord ignores non-letters and dedupes`() {
        assertEquals(
            "helo",
            SwipePathHelper.pathToWord(listOf('h', 'e', 'l', 'l', 'o', '1', ' ')),
        )
    }

    @Test
    fun `pathToWord returns empty for blank path`() {
        assertEquals("", SwipePathHelper.pathToWord(emptyList()))
        assertEquals("", SwipePathHelper.pathToWord(listOf('1', ' ')))
    }
}
