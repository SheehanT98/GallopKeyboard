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
import com.gallopkeyboard.ime.audio.AsrCoroutineDispatcher
import com.gallopkeyboard.ime.audio.AudioSession
import com.gallopkeyboard.ime.audio.Transcriber
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamingTranscriber @Inject constructor(
    private val engine: StreamingAsrEngine,
    private val committer: ImeTextCommitter,
    private val asrDispatcher: AsrCoroutineDispatcher,
    private val promptState: VoiceModelPromptState,
    @ApplicationContext private val context: Context,
) : Transcriber {

    companion object {
        private const val TAG = "StreamingTranscriber"
        private const val PARTIAL_POLL_INTERVAL_FRAMES = 5
        private const val POLISH_DEFER_MS = 50L
    }

    private val asrScope = CoroutineScope(SupervisorJob() + asrDispatcher.dispatcher)

    private var frameCount = 0
    private var lastPartial = ""

    /** Bumped on start/stop/cancel so late frame jobs exit silently. */
    @Volatile
    private var sessionEpoch = 0

    override fun onSessionStart(session: AudioSession) {
        sessionEpoch++
        val epoch = sessionEpoch
        frameCount = 0
        lastPartial = ""
        asrScope.launch {
            if (epoch != sessionEpoch) return@launch
            try {
                engine.beginStream()
                if (epoch != sessionEpoch) return@launch
                promptState.dismissBanner()
                setComposingOnMain("")
            } catch (e: AsrModelMissingException) {
                Log.w(TAG, "models missing: ${e.files}")
                promptState.showBanner()
                showToast(R.string.asr_models_missing)
            } catch (e: Exception) {
                if (epoch != sessionEpoch) return@launch
                Log.e(TAG, "session start failed", e)
                clearComposingOnMain()
                showToast(R.string.asr_recognition_failed)
            }
        }
    }

    override fun onAudioFrame(session: AudioSession, frame: ShortArray) {
        val epoch = sessionEpoch
        val frameCopy = frame.copyOf()
        asrScope.launch {
            if (epoch != sessionEpoch) return@launch
            try {
                engine.acceptFrame(frameCopy)
                if (epoch != sessionEpoch) return@launch
                frameCount++
                if (frameCount % PARTIAL_POLL_INTERVAL_FRAMES == 0) {
                    val partial = engine.currentPartial()
                    if (epoch != sessionEpoch) return@launch
                    if (partial != lastPartial) {
                        lastPartial = partial
                        setComposingOnMain(partial)
                    }
                }
            } catch (e: IllegalStateException) {
                // Late frame after finalize/cancel — "No active stream" etc.
                if (epoch != sessionEpoch) return@launch
                Log.w(TAG, "frame after session end: ${e.message}")
            } catch (e: Exception) {
                if (epoch != sessionEpoch) return@launch
                Log.e(TAG, "frame processing failed", e)
                clearComposingOnMain()
                showToast(R.string.asr_recognition_failed)
            }
        }
    }

    override suspend fun onSessionStop(session: AudioSession) {
        sessionEpoch++
        withContext(asrDispatcher.dispatcher) {
            try {
                val finalText = engine.finalize()
                setComposingOnMain(finalText)
            } catch (e: Exception) {
                Log.e(TAG, "session stop failed", e)
                clearComposingOnMain()
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
        sessionEpoch++
        runBlocking(asrDispatcher.dispatcher) {
            try {
                engine.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "session cancel failed", e)
            }
        }
        committer.clearComposing()
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun setComposingOnMain(text: String) {
        mainHandler.post { committer.setComposing(text) }
    }

    private fun clearComposingOnMain() {
        mainHandler.post { committer.clearComposing() }
    }

    private fun showToast(messageRes: Int) {
        mainHandler.post {
            val toast = Toast.makeText(context, messageRes, Toast.LENGTH_LONG)
            toast.show()
        }
    }
}
