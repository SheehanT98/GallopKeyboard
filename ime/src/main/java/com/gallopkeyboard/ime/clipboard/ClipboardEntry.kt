package com.gallopkeyboard.ime.clipboard

/**
 * A clipboard item shown in the panel or strip.
 *
 * @param id Stable identifier for pin/unpin (UUID for pinned; text hash for recent).
 * @param text Plain-text clip content.
 * @param pinned When true, persisted via [PinnedClipboardStore].
 * @param updatedAt Epoch millis when the entry was last seen or pinned.
 */
data class ClipboardEntry(
    val id: String,
    val text: String,
    val pinned: Boolean,
    val updatedAt: Long,
)
