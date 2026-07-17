package com.gallopkeyboard.ime.panel

import com.gallopkeyboard.ime.audio.AudioSession
import com.gallopkeyboard.ime.audio.Transcriber

/**
 * Cancels an active [AudioSession] through the transcriber.
 *
 * [SmartVoiceButton] dispose must call this (not only [GestureFsm.reset]) so
 * Parakeet / hybrid ASR streams are torn down when the composable leaves
 * composition mid-recording (panel switch, IME hide).
 *
 * Idempotent: null session is a no-op; safe to call from dispose after gesture cancel.
 */
internal fun cancelActiveSession(
    transcriber: Transcriber,
    session: AudioSession?,
): AudioSession? {
    if (session != null) {
        transcriber.onSessionCancel(session)
    }
    return null
}
