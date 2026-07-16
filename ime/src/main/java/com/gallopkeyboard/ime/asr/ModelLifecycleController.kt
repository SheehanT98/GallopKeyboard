package com.gallopkeyboard.ime.asr

/**
 * Hooks for scheduling ASR engine unload when the voice panel is idle.
 */
interface ModelLifecycleController {
    fun onSessionStarted()
    fun onSessionStopped()
    fun onVoicePanelHidden()
    fun onVoicePanelShown()
}
