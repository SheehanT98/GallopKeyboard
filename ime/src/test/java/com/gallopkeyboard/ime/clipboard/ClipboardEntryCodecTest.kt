package com.gallopkeyboard.ime.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClipboardEntryCodecTest {

    @Test
    fun `encode decode round trip`() {
        val entries = listOf(
            ClipboardEntry("id-1", "hello", pinned = true, updatedAt = 100L),
            ClipboardEntry("id-2", "world", pinned = true, updatedAt = 200L),
        )
        val decoded = ClipboardEntryCodec.decode(ClipboardEntryCodec.encode(entries))
        assertEquals(2, decoded.size)
        assertEquals("hello", decoded.first { it.id == "id-1" }.text)
        assertEquals("world", decoded.first { it.id == "id-2" }.text)
        assertTrue(decoded.all { it.pinned })
    }

    @Test
    fun `encode skips unpinned entries`() {
        val entries = listOf(
            ClipboardEntry("id-1", "hello", pinned = false, updatedAt = 100L),
        )
        assertTrue(ClipboardEntryCodec.encode(entries).isEmpty())
    }

    @Test
    fun `decode blank returns empty`() {
        assertTrue(ClipboardEntryCodec.decode(null).isEmpty())
        assertTrue(ClipboardEntryCodec.decode(emptySet()).isEmpty())
    }

    @Test
    fun `decode invalid line is skipped`() {
        val decoded = ClipboardEntryCodec.decode(setOf("bad-line"))
        assertTrue(decoded.isEmpty())
    }
}
