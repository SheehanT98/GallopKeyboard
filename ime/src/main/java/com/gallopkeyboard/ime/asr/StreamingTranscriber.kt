package com.gallopkeyboard.ime.asr

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.gallopkeyboard.asr.parakeet.AsrModelMissingException
import com.gallopkeyboard.asr.parakeet.StreamingAsrEngine
import com.gallopkeyboard.core.flags.Flags
import com.gallopkeyboard.ime.R
import com.gallopkeyboard.ime.audio.AudioSession
import com.gallopkeyboard.ime.audio.RecorderCoroutineDispatcher
import com.gallopkeyboard.ime.audio.Transcriber
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamingTranscriber @Inject constructor(
    private val engine: StreamingAsrEngine,
    private val committer: ImeTextCommitter,
    private val dispatcher: RecorderCoroutineDispatcher,
    private val promptState: VoiceModelPromptState,
    @ApplicationContext private val context: Context,
) : Transcriber {

    companion object {
        private const val TAG = "StreamingTranscriber"
        private const val PARTIAL_POLL_INTERVAL_FRAMES = 5
        private const val POLISH_DEFER_MS = 50L
    }

    private var frameCount = 0
    private var lastPartial = ""

    override fun onSessionStart(session: AudioSession) {
        frameCount = 0
        lastPartial = ""
        runBlocking(dispatcher.dispatcher) {
            try {
                engine.beginStream()
                committer.setComposing("")
            } catch (e: AsrModelMissingException) {
                Log.w(TAG, "models missing: ${e.files}")
                promptState.showBanner()
                showToast(R.string.asr_models_missing)
            } catch (e: Exception) {
                Log.e(TAG, "session start failed", e)
                committer.clearComposing()
                showToast(R.string.asr_recognition_failed)
            }
        }
    }

    override fun onAudioFrame(session: AudioSession, frame: ShortArray) {
        try {
            engine.acceptFrame(frame)
            frameCount++
            if (frameCount % PARTIAL_POLL_INTERVAL_FRAMES == 0) {
                val partial = engine.currentPartial()
                if (partial != lastPartial) {
                    lastPartial = partial
                    committer.setComposing(partial)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "frame processing failed", e)
            committer.clearComposing()
            showToast(R.string.asr_recognition_failed)
        }
    }

    override suspend fun onSessionStop(session: AudioSession) {
        withContext(dispatcher.dispatcher) {
            try {
                val finalText = engine.finalize()
                committer.setComposing(finalText)
            } catch (e: Exception) {
                Log.e(TAG, "session stop failed", e)
                committer.clearComposing()
                showToast(R.string.asr_recognition_failed)
                return@withContext
            }
        }
        if (!Flags.polishEnabled) {
            delay(POLISH_DEFER_MS)
            committer.clearComposing()
        }
    }

    override fun onSessionCancel(session: AudioSession) {
        runBlocking(dispatcher.dispatcher) {
            try {
                engine.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "session cancel failed", e)
            }
        }
        committer.clearComposing()
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun showToast(messageRes: Int) {
        mainHandler.post {
            val toast = Toast.makeText(context, messageRes, Toast.LENGTH_LONG)
            toast.show()
        }
    }
}
