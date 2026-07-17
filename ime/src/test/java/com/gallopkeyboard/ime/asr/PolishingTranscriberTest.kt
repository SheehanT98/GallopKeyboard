package com.gallopkeyboard.ime.asr

import android.content.Context
import com.gallopkeyboard.asr.parakeet.ParakeetConfig
import com.gallopkeyboard.asr.parakeet.StreamingAsrEngine
import com.gallopkeyboard.core.flags.Flags
import com.gallopkeyboard.ime.audio.AudioSession
import com.gallopkeyboard.ime.audio.AsrCoroutineDispatcher
import com.gallopkeyboard.ime.audio.RingByteBuffer
import com.gallopkeyboard.whisper.AsrPolishEngine
import com.gallopkeyboard.whisper.WhisperConfig
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import android.os.Looper
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PolishingTranscriberTest {

    private lateinit var streamingEngine: PolishFakeStreamingAsrEngine
    private lateinit var polishEngine: FakeAsrPolishEngine
    private lateinit var committer: RecordingImeTextCommitter
    private lateinit var asrDispatcher: AsrCoroutineDispatcher
    private lateinit var streaming: StreamingTranscriber
    private lateinit var lifecycle: RecordingModelLifecycleController
    private lateinit var transcriber: PolishingTranscriber

    @Before
    fun setUp() {
        streamingEngine = PolishFakeStreamingAsrEngine()
        polishEngine = FakeAsrPolishEngine()
        committer = RecordingImeTextCommitter()
        lifecycle = RecordingModelLifecycleController()
        asrDispatcher = AsrCoroutineDispatcher()
        val context: Context = androidx.test.core.app.ApplicationProvider.getApplicationContext()
        streaming = StreamingTranscriber(streamingEngine, committer, asrDispatcher, VoiceModelPromptState(), context)
        transcriber = PolishingTranscriber(streaming, polishEngine, committer, lifecycle)
        Flags.polishEnabled = true
    }

    @After
    fun tearDown() {
        Flags.polishEnabled = true
        streamingEngine.close()
        polishEngine.close()
    }

    @Test
    fun `stop with polish success replaces composing text with polished result`() = runTest {
        streamingEngine.finalizeResult = "streaming partial"
        polishEngine.result = "polished result"
        val session = sessionWithPcm()

        transcriber.onSessionStart(session)
        committer.calls.clear()
        transcriber.onSessionStop(session)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertTrue(polishEngine.transcribeCalled)
        assertTrue(committer.calls.contains(CommitterCall.CommitText("polished result")))
    }

    @Test
    fun `stop with polish timeout leaves streaming partial clearComposing called`() = runTest {
        streamingEngine.finalizeResult = "streaming partial"
        polishEngine.blockMs = PolishingTranscriber.POLISH_TIMEOUT_MS + 500
        val session = sessionWithPcm()

        transcriber.onSessionStart(session)
        committer.calls.clear()
        transcriber.onSessionStop(session)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertTrue(polishEngine.transcribeCalled)
        assertTrue(committer.calls.contains(CommitterCall.ClearComposing))
        assertFalse(committer.calls.any { it is CommitterCall.CommitText })
    }

    @Test
    fun `stop with polish exception falls back to streaming partial`() = runTest {
        streamingEngine.finalizeResult = "streaming partial"
        polishEngine.throwOnTranscribe = RuntimeException("jni failed")
        val session = sessionWithPcm()

        transcriber.onSessionStart(session)
        committer.calls.clear()
        transcriber.onSessionStop(session)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertTrue(committer.calls.contains(CommitterCall.ClearComposing))
        assertFalse(committer.calls.any { it is CommitterCall.CommitText })
    }

    @Test
    fun `cancel during recording cancels streaming polish never runs`() {
        val session = sessionWithPcm()
        transcriber.onSessionStart(session)
        transcriber.onSessionCancel(session)

        assertTrue(streamingEngine.cancelCalled)
        assertFalse(polishEngine.transcribeCalled)
        assertTrue(lifecycle.sessionStoppedCount >= 1)
    }

    @Test
    fun `session start and stop notify lifecycle manager`() {
        val session = sessionWithPcm()
        transcriber.onSessionStart(session)
        assertEquals(1, lifecycle.sessionStartedCount)

        transcriber.onSessionCancel(session)
        assertEquals(1, lifecycle.sessionStoppedCount)
    }

    @Test
    fun `polish disabled by flag streaming behavior only`() = runTest {
        Flags.polishEnabled = false
        streamingEngine.finalizeResult = "streaming only"
        val session = sessionWithPcm()

        transcriber.onSessionStart(session)
        committer.calls.clear()
        transcriber.onSessionStop(session)

        assertFalse(polishEngine.transcribeCalled)
        assertTrue(committer.calls.contains(CommitterCall.ClearComposing))
    }

    @Test
    fun `onAudioFrame delegates to streaming exactly once per call`() {
        val session = sessionWithPcm()
        transcriber.onSessionStart(session)
        streamingEngine.acceptFrameCount = 0

        val frame = ShortArray(1600) { 0 }
        transcriber.onAudioFrame(session, frame)
        transcriber.onAudioFrame(session, frame)
        drainAsrAndMain()

        assertEquals(2, streamingEngine.acceptFrameCount)
    }

    private fun drainAsrAndMain() {
        runBlocking(asrDispatcher.dispatcher) { }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun sessionWithPcm(): AudioSession {
        val buffer = RingByteBuffer(32_000)
        val frame = ShortArray(1600) { (it % 100).toShort() }
        val bytes = ByteArray(frame.size * 2)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        frame.forEach { bb.putShort(it) }
        buffer.write(bytes, 0, bytes.size)
        return AudioSession(startedAtElapsedMs = 0L, buffer = buffer)
    }
}

class FakeAsrPolishEngine : AsrPolishEngine {
    var result: String = "polished result"
    var blockMs: Long = 0
    var throwOnTranscribe: Throwable? = null
    var transcribeCalled = false

    override fun init(config: WhisperConfig) = Unit

    override suspend fun transcribe(pcm16k: ShortArray): String {
        transcribeCalled = true
        throwOnTranscribe?.let { throw it }
        if (blockMs > 0) kotlinx.coroutines.delay(blockMs)
        return result
    }

    override fun cancel() = Unit

    override fun close() = Unit
}

class RecordingModelLifecycleController : ModelLifecycleController {
    var sessionStartedCount = 0
    var sessionStoppedCount = 0

    override fun onSessionStarted() {
        sessionStartedCount++
    }

    override fun onSessionStopped() {
        sessionStoppedCount++
    }

    override fun onVoicePanelHidden() = Unit
    override fun onVoicePanelShown() = Unit
}

class PolishFakeStreamingAsrEngine : StreamingAsrEngine {
    var finalizeResult: String = "hello world."
    var cancelCalled = false
    var acceptFrameCount = 0

    override fun init(config: ParakeetConfig) = Unit

    override fun beginStream() = Unit

    override fun acceptFrame(pcm16k: ShortArray) {
        acceptFrameCount++
    }

    override fun currentPartial(): String = ""

    override fun finalize(): String = finalizeResult

    override fun cancel() {
        cancelCalled = true
    }

    override fun close() = Unit
}
