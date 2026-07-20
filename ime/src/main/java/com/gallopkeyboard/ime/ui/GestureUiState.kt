package com.gallopkeyboard.ime.ui

import com.gallopkeyboard.ime.model.KeyDefinition

/**
 * Compose-visible swipe / accent UI state derived from [SwipeTypingController].
 *
 * Updated only when membership or accent selection changes so MOVE events on the
 * same key path do not force a full keyboard recomposition.
 */
data class GestureUiState(
    val highlightedKeys: Set<KeyDefinition> = emptySet(),
    val pressedKey: KeyDefinition? = null,
    val accentPopupKey: KeyDefinition? = null,
    val highlightedAccentIndex: Int? = null,
) {
    companion object {
        val Empty = GestureUiState()
    }
}

/**
 * Returns [next] when it differs from [current]; null means skip a Compose state write.
 */
internal fun gestureUiStateIfChanged(current: GestureUiState, next: GestureUiState): GestureUiState? =
    if (current == next) null else next
