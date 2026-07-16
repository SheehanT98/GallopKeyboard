package com.gallopkeyboard.ime.panel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pure state holder for typing ↔ voice panel transitions.
 *
 * Owned by [com.gallopkeyboard.ime.DictusImeService]; no Android context
 * dependencies so unit tests run without Robolectric.
 */
class PanelController {

    private val _state = MutableStateFlow(PanelState.TYPING)
    val state: StateFlow<PanelState> = _state.asStateFlow()

    fun toggle() {
        _state.value = when (_state.value) {
            PanelState.TYPING -> PanelState.VOICE
            PanelState.VOICE -> PanelState.TYPING
        }
    }

    fun showTyping() {
        _state.value = PanelState.TYPING
    }

    fun showVoice() {
        _state.value = PanelState.VOICE
    }

    fun reset() {
        _state.value = PanelState.TYPING
    }
}
