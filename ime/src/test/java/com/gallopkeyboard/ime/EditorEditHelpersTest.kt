package com.gallopkeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorEditHelpersTest {

    @Test
    fun `countCharsToDeleteForWord trims trailing spaces then deletes word`() {
        // "hello world " → delete "world " (6 UTF-16 units)
        assertEquals(6, countCharsToDeleteForWord("hello world "))
    }

    @Test
    fun `countCharsToDeleteForWord deletes last word without trailing space`() {
        assertEquals(5, countCharsToDeleteForWord("hello world"))
    }

    @Test
    fun `countCharsToDeleteForWord deletes only trailing whitespace when no word left`() {
        assertEquals(3, countCharsToDeleteForWord("   "))
    }

    @Test
    fun `countCharsToDeleteForWord returns zero for empty`() {
        assertEquals(0, countCharsToDeleteForWord(""))
    }

    @Test
    fun `countCharsToDeleteForWord uses UTF-16 length for surrogate emoji word`() {
        // Grinning face U+1F600 is one code point / two UTF-16 units: "\uD83D\uDE00"
        val emoji = "\uD83D\uDE00"
        val before = "hi $emoji"
        // Deletes the emoji word only (2 UTF-16 units), matching deleteSurroundingText.
        assertEquals(2, countCharsToDeleteForWord(before))
    }

    @Test
    fun `deleteMode stays CHAR for early repeats under 900ms`() {
        assertEquals(DeleteRepeatMode.CHAR, deleteMode(0, 0L))
        assertEquals(DeleteRepeatMode.CHAR, deleteMode(7, 899L))
    }

    @Test
    fun `deleteMode switches to WORD after 8 deletes`() {
        assertEquals(DeleteRepeatMode.WORD, deleteMode(8, 100L))
    }

    @Test
    fun `deleteMode switches to WORD after 900ms hold`() {
        assertEquals(DeleteRepeatMode.WORD, deleteMode(1, 900L))
    }

    @Test
    fun `cursorOffsetAfterDrag clamps to document bounds`() {
        assertEquals(5, cursorOffsetAfterDrag(beforeLen = 5, afterLen = 3, deltaChars = 0))
        assertEquals(8, cursorOffsetAfterDrag(beforeLen = 5, afterLen = 3, deltaChars = 10))
        assertEquals(0, cursorOffsetAfterDrag(beforeLen = 5, afterLen = 3, deltaChars = -20))
        assertEquals(7, cursorOffsetAfterDrag(beforeLen = 5, afterLen = 3, deltaChars = 2))
        assertEquals(3, cursorOffsetAfterDrag(beforeLen = 5, afterLen = 3, deltaChars = -2))
    }
}
