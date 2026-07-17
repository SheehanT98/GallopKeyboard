package com.gallopkeyboard.ime.clipboard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory ring buffer of the last [capacity] plain-text clipboard entries.
 *
 * Most recent item first. No persistence — history is lost when the IME process dies.
 */
class ClipboardStore(private val capacity: Int = 10) {

    companion object {
        const val MAX_TEXT_LENGTH = 500
    }

    private val ring = ArrayDeque<String>(capacity)
    private val _items = MutableStateFlow<List<String>>(emptyList())
    val itemsFlow: StateFlow<List<String>> = _items.asStateFlow()

    fun add(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        if (trimmed.length > MAX_TEXT_LENGTH) return
        if (ring.firstOrNull() == trimmed) return

        if (ring.size >= capacity) {
            ring.removeLast()
        }
        ring.addFirst(trimmed)
        _items.value = ring.toList()
    }

    fun items(): List<String> = ring.toList()

    fun clear() {
        ring.clear()
        _items.value = emptyList()
    }
}
