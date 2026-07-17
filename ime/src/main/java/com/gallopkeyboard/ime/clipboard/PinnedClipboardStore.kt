package com.gallopkeyboard.ime.clipboard

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.gallopkeyboard.core.preferences.PreferenceKeys
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Persists user-pinned clipboard clips via DataStore.
 *
 * Pinned text is stored on-device in plaintext — suitable for emails and
 * frequent phrases; the user chooses what to pin.
 */
class PinnedClipboardStore(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {

    private val _entries = MutableStateFlow<List<ClipboardEntry>>(emptyList())
    val entriesFlow: StateFlow<List<ClipboardEntry>> = _entries.asStateFlow()

    init {
        scope.launch {
            dataStore.data
                .map { prefs ->
                    ClipboardEntryCodec.decode(prefs[PreferenceKeys.PINNED_CLIPBOARD_ENTRIES])
                }
                .collect { decoded ->
                    _entries.value = decoded.sortedByDescending { it.updatedAt }
                }
        }
    }

    fun pin(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        if (trimmed.length > ClipboardStore.MAX_TEXT_LENGTH) return

        val now = System.currentTimeMillis()
        val existing = _entries.value.find { it.text == trimmed }
        val updated = if (existing != null) {
            _entries.value.map {
                if (it.id == existing.id) it.copy(updatedAt = now) else it
            }
        } else {
            listOf(
                ClipboardEntry(
                    id = UUID.randomUUID().toString(),
                    text = trimmed,
                    pinned = true,
                    updatedAt = now,
                ),
            ) + _entries.value
        }
        persist(updated)
    }

    fun unpin(id: String) {
        val updated = _entries.value.filterNot { it.id == id }
        persist(updated)
    }

    fun togglePin(text: String) {
        val trimmed = text.trim()
        val existing = _entries.value.find { it.text == trimmed }
        if (existing != null) {
            unpin(existing.id)
        } else {
            pin(trimmed)
        }
    }

    fun isPinned(text: String): Boolean =
        _entries.value.any { it.text == text.trim() }

    private fun persist(entries: List<ClipboardEntry>) {
        val sorted = entries.sortedByDescending { it.updatedAt }
        _entries.value = sorted
        scope.launch {
            dataStore.edit { prefs ->
                prefs[PreferenceKeys.PINNED_CLIPBOARD_ENTRIES] =
                    ClipboardEntryCodec.encode(sorted)
            }
        }
    }
}
