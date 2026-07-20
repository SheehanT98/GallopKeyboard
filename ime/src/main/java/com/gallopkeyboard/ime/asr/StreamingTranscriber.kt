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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
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
        /** Bounded pending frames; DROP_OLDEST when full (Plan 027). */
        internal const val FRAME_QUEUE_CAPACITY = 2
    }

    private val asrScope = CoroutineScope(SupervisorJob() + asrDispatcher.dispatcher)

    private var frameCount = 0
    private var lastPartial = ""

    /** Bumped on start/stop/cancel so late frame work exits silently. */
    @Volatile
    private var sessionEpoch = 0

    private data class QueuedFrame(val epoch: Int, val pcm: ShortArray)

    /**
     * Serial frame queue + single consumer (replaces per-frame [launch]).
     * Capacity [FRAME_QUEUE_CAPACITY]; when full, drop the oldest queued copy.
     */
    private val frameQueueLock = Any()
    private val frameQueue = ArrayDeque<QueuedFrame>(FRAME_QUEUE_CAPACITY)
    private val frameWake = Channel<Unit>(Channel.CONFLATED)
    private var frameConsumerJob: Job? = null

    /** Instrumentation: current queued frame copies (0..[FRAME_QUEUE_CAPACITY]). */
    internal val queuedFrameCopies = AtomicInteger(0)

    /** Instrumentation: peak [queuedFrameCopies] observed this session. */
    internal val peakQueuedFrameCopies = AtomicInteger(0)

    override fun onSessionStart(session: AudioSession) {
        sessionEpoch++
        val epoch = sessionEpoch
        frameCount = 0
        lastPartial = ""
        clearFrameQueue()
        queuedFrameCopies.set(0)
        peakQueuedFrameCopies.set(0)

        frameConsumerJob?.cancel()
        frameConsumerJob = asrScope.launch {
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
                return@launch
            } catch (e: Exception) {
                if (epoch != sessionEpoch) return@launch
                Log.e(TAG, "session start failed", e)
                clearComposingOnMain()
                showToast(R.string.asr_recognition_failed)
                return@launch
            }

            while (isActive && epoch == sessionEpoch) {
                frameWake.receive()
                while (isActive && epoch == sessionEpoch) {
                    val queued = pollFrame() ?: break
                    if (queued.epoch != epoch) continue
                    try {
                        engine.acceptFrame(queued.pcm)
                        if (epoch != sessionEpoch) continue
                        frameCount++
                        if (frameCount % PARTIAL_POLL_INTERVAL_FRAMES == 0) {
                            val partial = engine.currentPartial()
                            if (epoch != sessionEpoch) continue
                            if (partial != lastPartial) {
                                lastPartial = partial
                                setComposingOnMain(partial)
                            }
                        }
                    } catch (e: IllegalStateException) {
                        // Late frame after finalize/cancel — "No active stream" etc.
                        if (epoch != sessionEpoch) continue
                        Log.w(TAG, "frame after session end: ${e.message}")
                    } catch (e: Exception) {
                        if (epoch != sessionEpoch) continue
                        Log.e(TAG, "frame processing failed", e)
                        clearComposingOnMain()
                        showToast(R.string.asr_recognition_failed)
                    }
                }
            }
        }
    }

    override fun onAudioFrame(session: AudioSession, frame: ShortArray) {
        val epoch = sessionEpoch
        val frameCopy = frame.copyOf()
        enqueueFrame(QueuedFrame(epoch, frameCopy))
        try {
            frameWake.trySend(Unit)
        } catch (_: ClosedSendChannelException) {
            // Process death / closed wake — ignore.
        }
    }

    override suspend fun onSessionStop(session: AudioSession) {
        sessionEpoch++
        clearFrameQueue()
        frameConsumerJob?.cancel()
        frameConsumerJob = null
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
        clearFrameQueue()
        frameConsumerJob?.cancel()
        frameConsumerJob = null
        runBlocking(asrDispatcher.dispatcher) {
            try {
                engine.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "session cancel failed", e)
            }
        }
        committer.clearComposing()
    }

    private fun enqueueFrame(frame: QueuedFrame) {
        synchronized(frameQueueLock) {
            if (frameQueue.size >= FRAME_QUEUE_CAPACITY) {
                frameQueue.removeFirst()
            }
            frameQueue.addLast(frame)
            val size = frameQueue.size
            queuedFrameCopies.set(size)
            peakQueuedFrameCopies.updateAndGet { maxOf(it, size) }
        }
    }

    private fun pollFrame(): QueuedFrame? {
        synchronized(frameQueueLock) {
            val next = if (frameQueue.isEmpty()) null else frameQueue.removeFirst()
            queuedFrameCopies.set(frameQueue.size)
            return next
        }
    }

    private fun clearFrameQueue() {
        synchronized(frameQueueLock) {
            frameQueue.clear()
            queuedFrameCopies.set(0)
        }
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
