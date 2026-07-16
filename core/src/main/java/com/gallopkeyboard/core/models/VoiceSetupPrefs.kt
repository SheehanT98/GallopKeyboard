package com.gallopkeyboard.core.models

import android.content.Context

/** UX shortcuts for launcher routing — [ModelInstaller.isInstalled] is the source of truth. */
object VoiceSetupPrefs {
    private const val PREFS_NAME = "gallop_voice_setup"
    private const val KEY_SETUP_COMPLETED = "voice.setup.completed"
    private const val KEY_WHISPER_TIER = "whisper.tier"

    const val TIER_BASE = "base"
    const val TIER_SMALL = "small"

    fun isSetupCompleted(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SETUP_COMPLETED, false)

    fun markSetupCompleted(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SETUP_COMPLETED, true)
            .apply()
    }

    fun clearSetupCompleted(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SETUP_COMPLETED, false)
            .apply()
    }

    fun whisperTier(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_WHISPER_TIER, TIER_BASE) ?: TIER_BASE

    fun setWhisperTier(context: Context, tier: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_WHISPER_TIER, tier)
            .apply()
    }

    fun activeWhisperSpec(context: Context): ModelSpec =
        if (whisperTier(context) == TIER_SMALL) ModelRegistry.whisperSmall else ModelRegistry.whisperBase
}
