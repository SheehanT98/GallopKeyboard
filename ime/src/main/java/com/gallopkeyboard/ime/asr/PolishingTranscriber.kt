package com.gallopkeyboard.ime.asr

import android.util.Log
import com.gallopkeyboard.core.flags.Flags
import com.gallopkeyboard.ime.audio.AudioSession
import com.gallopkeyboard.ime.audio.Transcriber
import com.gallopkeyboard.whisper.AsrPolishEngine
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decorates [StreamingTranscriber] with a Whisper full-buffer polish pass on stop.
 */
@Singleton
class PolishingTranscriber @Inject constructor(
    private val streaming: StreamingTranscriber,
    private val engine: AsrPolishEngine,
    private val committer: ImeTextCommitter,
) : Transcriber {

    companion object {
        private const val TAG = "PolishingTranscriber"
        const val POLISH_TIMEOUT_MS = 2000L
    }

    override fun onSessionStart(session: AudioSession) =
        streaming.onSessionStart(session)

    override fun onAudioFrame(session: AudioSession, frame: ShortArray) =
        streaming.onAudioFrame(session, frame)

    override fun onSessionCancel(session: AudioSession) =
        streaming.onSessionCancel(session)

    override suspend fun onSessionStop(session: AudioSession) {
        streaming.onSessionStop(session)
        if (!Flags.polishEnabled) return

        val pcm = session.buffer.snapshotShorts()
        val polished = try {
            withTimeout(POLISH_TIMEOUT_MS) {
                engine.transcribe(pcm)
            }
        } catch (t: TimeoutCancellationException) {
            Log.w(TAG, "polish timed out; keeping streaming partial")
            null
        } catch (t: Throwable) {
            Log.e(TAG, "polish failed", t)
            null
        }

        if (polished != null) {
            committer.commitText(polished)
        } else {
            committer.clearComposing()
        }
    }
}
