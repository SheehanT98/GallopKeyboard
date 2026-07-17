package com.gallopkeyboard.ime.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardStoreTest {

    @Test
    fun `empty store returns empty items`() {
        val store = ClipboardStore()
        assertTrue(store.items().isEmpty())
    }

    @Test
    fun `add 3 returns 3 in insertion order most recent first`() {
        val store = ClipboardStore()
        store.add("first")
        store.add("second")
        store.add("third")
        assertEquals(listOf("third", "second", "first"), store.items())
    }

    @Test
    fun `add 4th evicts oldest`() {
        val store = ClipboardStore(capacity = 3)
        store.add("one")
        store.add("two")
        store.add("three")
        store.add("four")
        assertEquals(listOf("four", "three", "two"), store.items())
    }

    @Test
    fun `duplicate consecutive add is no-op`() {
        val store = ClipboardStore()
        store.add("hello")
        store.add("hello")
        assertEquals(listOf("hello"), store.items())
    }

    @Test
    fun `blank and whitespace adds are no-op`() {
        val store = ClipboardStore()
        store.add("")
        store.add("   ")
        store.add("\n")
        assertTrue(store.items().isEmpty())
    }

    @Test
    fun `text over 500 chars is no-op`() {
        val store = ClipboardStore()
        store.add("a".repeat(501))
        assertTrue(store.items().isEmpty())
    }

    @Test
    fun `clear empties store`() {
        val store = ClipboardStore()
        store.add("one")
        store.add("two")
        store.clear()
        assertTrue(store.items().isEmpty())
    }
}
