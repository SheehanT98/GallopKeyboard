package com.gallopkeyboard.ime.panel

import com.gallopkeyboard.ime.audio.AudioSession
import com.gallopkeyboard.ime.audio.Transcriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * IME-process scope for voice stop/polish work that must outlive
 * [SmartVoiceButton] composition (panel switch / IME hide mid-polish).
 *
 * Not cancelled when the composable's [androidx.compose.runtime.rememberCoroutineScope]
 * is disposed. Cancelled only with process death.
 */
internal val voiceStopScope: CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

/**
 * Cancels an active [AudioSession] through the transcriber.
 *
 * [SmartVoiceButton] dispose must call this (not only [GestureFsm.reset]) so
 * Parakeet / hybrid ASR streams are torn down when the composable leaves
 * composition mid-recording (panel switch, IME hide).
 *
 * Idempotent: null session is a no-op; safe to call from dispose after gesture cancel.
 *
 * Do **not** call this for a session already handed to [Transcriber.onSessionStop]
 * (see [shouldCancelRecordingOnDispose]).
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

/**
 * Dispose policy for voice sessions:
 * - Still recording → cancel the session.
 * - Stop/polish in flight (recording already cleared) → keep the stopping job.
 */
internal fun shouldCancelRecordingOnDispose(
    recordingSessionActive: Boolean,
    stoppingJobActive: Boolean,
): Boolean = recordingSessionActive && !stoppingJobActive
