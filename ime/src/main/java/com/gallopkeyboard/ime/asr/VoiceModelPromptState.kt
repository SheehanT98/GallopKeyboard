package com.gallopkeyboard.ime.asr

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Shared IME state for prompting the user to set up voice models. */
@Singleton
class VoiceModelPromptState @Inject constructor() {
    private val _showSetupBanner = MutableStateFlow(false)
    val showSetupBanner: StateFlow<Boolean> = _showSetupBanner.asStateFlow()

    fun showBanner() {
        _showSetupBanner.value = true
    }

    fun dismissBanner() {
        _showSetupBanner.value = false
    }
}
