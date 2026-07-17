package com.gallopkeyboard.ime.clipboard

import android.util.Base64

/**
 * Codec for pinned clipboard entries stored in DataStore as a string set.
 *
 * Each entry is encoded as `id|updatedAt|base64(text)` so special characters
 * in clip text are safe. Pinned text is stored on-device in plaintext —
 * suitable for emails and frequent phrases; the user chooses what to pin.
 */
internal object ClipboardEntryCodec {

    private const val FIELD_SEPARATOR = '|'

    fun encode(entries: List<ClipboardEntry>): Set<String> =
        entries
            .filter { it.pinned }
            .map { entry ->
                listOf(
                    entry.id,
                    entry.updatedAt.toString(),
                    Base64.encodeToString(entry.text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP),
                ).joinToString(FIELD_SEPARATOR.toString())
            }
            .toSet()

    fun decode(encoded: Set<String>?): List<ClipboardEntry> {
        if (encoded.isNullOrEmpty()) return emptyList()
        return encoded.mapNotNull { line ->
            val parts = line.split(FIELD_SEPARATOR, limit = 3)
            if (parts.size != 3) return@mapNotNull null
            val text = try {
                String(Base64.decode(parts[2], Base64.NO_WRAP), Charsets.UTF_8)
            } catch (_: Exception) {
                return@mapNotNull null
            }
            ClipboardEntry(
                id = parts[0],
                text = text,
                pinned = true,
                updatedAt = parts[1].toLongOrNull() ?: return@mapNotNull null,
            )
        }
    }
}
