package com.gallopkeyboard.ime.asr

import android.content.Context
import com.gallopkeyboard.asr.parakeet.AsrModelMissingException
import com.gallopkeyboard.asr.parakeet.ParakeetConfig
import com.gallopkeyboard.asr.parakeet.StreamingAsrEngine
import com.gallopkeyboard.core.flags.Flags
import com.gallopkeyboard.ime.audio.AsrCoroutineDispatcher
import com.gallopkeyboard.ime.audio.AudioSession
import com.gallopkeyboard.ime.audio.RingByteBuffer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class StreamingTranscriberTest {

    private lateinit var engine: FakeStreamingAsrEngine
    private lateinit var committer: RecordingImeTextCommitter
    private lateinit var asrDispatcher: AsrCoroutineDispatcher
    private lateinit var context: Context
    private lateinit var transcriber: StreamingTranscriber

    @Before
    fun setUp() {
        engine = FakeStreamingAsrEngine()
        committer = RecordingImeTextCommitter()
        asrDispatcher = AsrCoroutineDispatcher()
        context = androidx.test.core.app.ApplicationProvider.getApplicationContext()
        transcriber = StreamingTranscriber(engine, committer, asrDispatcher, VoiceModelPromptState(), context)
        Flags.polishEnabled = false
    }

    @After
    fun tearDown() {
        Flags.polishEnabled = false
        engine.close()
    }

    @Test
    fun `session start commits empty composing region once`() {
        val session = newSession()
        transcriber.onSessionStart(session)
        drainAsrAndMain()

        assertEquals(listOf(CommitterCall.SetComposing("")), committer.calls)
    }

    @Test
    fun `ten frames deliver two partial updates`() {
        engine.partialsOnPoll = listOf("hel", "hello")
        val session = newSession()
        transcriber.onSessionStart(session)
        drainAsrAndMain()
        committer.calls.clear()

        repeat(10) {
            transcriber.onAudioFrame(session, ShortArray(1600))
        }
        drainAsrAndMain()

        assertEquals(
            listOf(
                CommitterCall.SetComposing("hel"),
                CommitterCall.SetComposing("hello"),
            ),
            committer.calls,
        )
    }

    @Test
    fun `duplicate partials are not re-committed`() {
        engine.partialsOnPoll = listOf("hello", "hello", "hello world")
        val session = newSession()
        transcriber.onSessionStart(session)
        drainAsrAndMain()
        committer.calls.clear()

        repeat(15) {
            transcriber.onAudioFrame(session, ShortArray(1600))
        }
        drainAsrAndMain()

        assertEquals(
            listOf(
                CommitterCall.SetComposing("hello"),
                CommitterCall.SetComposing("hello world"),
            ),
            committer.calls,
        )
    }

    @Test
    fun `acceptFrame runs on ASR dispatcher thread`() {
        val latch = CountDownLatch(1)
        engine.onAcceptFrame = { latch.countDown() }
        val session = newSession()
        transcriber.onSessionStart(session)
        drainAsrAndMain()

        transcriber.onAudioFrame(session, ShortArray(1600))
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(engine.acceptFrameThreadName!!.startsWith("AsrEngine"))
        assertNotEquals("main", engine.acceptFrameThreadName)
    }

    @Test
    fun `rapid frames do not throw and composing eventually updates on main`() {
        engine.partialsOnPoll = listOf("hello")
        val session = newSession()
        transcriber.onSessionStart(session)
        drainAsrAndMain()
        committer.calls.clear()

        repeat(50) {
            transcriber.onAudioFrame(session, ShortArray(1600))
        }
        drainAsrAndMain()

        assertTrue(committer.calls.any { it == CommitterCall.SetComposing("hello") })
    }

    @Test
    fun `session stop finalizes and sets composing text`() = runTest {
        engine.finalizeResult = "hello world."
        val session = newSession()
        transcriber.onSessionStart(session)
        drainAsrAndMain()
        committer.calls.clear()

        transcriber.onSessionStop(session)
        drainAsrAndMain()

        assertTrue(engine.finalizeCalled)
        assertTrue(committer.calls.any { it == CommitterCall.SetComposing("hello world.") })
    }

    @Test
    fun `session cancel clears composing`() {
        val session = newSession()
        transcriber.onSessionStart(session)
        drainAsrAndMain()
        committer.calls.clear()

        transcriber.onSessionCancel(session)

        assertTrue(engine.cancelCalled)
        assertEquals(listOf(CommitterCall.ClearComposing), committer.calls)
    }

    @Test
    fun `model missing on start shows toast path without composing`() {
        engine.failBeginWith = AsrModelMissingException(listOf("/missing/encoder.onnx"))
        val session = newSession()
        transcriber.onSessionStart(session)

        drainAsrAndMain()
        assertTrue(committer.calls.isEmpty())
        val toast = ShadowToast.getTextOfLatestToast().toString()
        assertTrue(toast.contains("Voice models"))
        assertTrue(toast.contains("Download"))
    }

    @Test
    fun `polish flag off clears composing after stop`() = runTest {
        engine.finalizeResult = "done"
        Flags.polishEnabled = false
        val session = newSession()
        transcriber.onSessionStart(session)
        drainAsrAndMain()
        committer.calls.clear()

        transcriber.onSessionStop(session)
        drainAsrAndMain()

        assertTrue(committer.calls.contains(CommitterCall.ClearComposing))
    }

    @Test
    fun `polish flag on leaves composing after stop`() = runTest {
        engine.finalizeResult = "done"
        Flags.polishEnabled = true
        val session = newSession()
        transcriber.onSessionStart(session)
        drainAsrAndMain()
        committer.calls.clear()

        transcriber.onSessionStop(session)
        drainAsrAndMain()

        assertFalse(committer.calls.contains(CommitterCall.ClearComposing))
    }

    @Test
    fun `late frame after stop does not toast recognition failed`() = runTest {
        engine.finalizeResult = "done"
        Flags.polishEnabled = true
        engine.throwOnAcceptAfterFinalize = true
        val session = newSession()
        transcriber.onSessionStart(session)
        drainAsrAndMain()

        transcriber.onSessionStop(session)
        drainAsrAndMain()
        ShadowToast.reset()
        committer.calls.clear()

        // Frame job launched after stop — sessionEpoch advanced; must not toast.
        transcriber.onAudioFrame(session, ShortArray(1600))
        drainAsrAndMain()

        assertEquals(0, ShadowToast.shownToastCount())
        assertFalse(committer.calls.contains(CommitterCall.ClearComposing))
    }

    private fun drainAsrAndMain() {
        runBlocking(asrDispatcher.dispatcher) { }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun newSession(): AudioSession =
        AudioSession(
            startedAtElapsedMs = 0L,
            buffer = RingByteBuffer(32_000),
        )
}

sealed class CommitterCall {
    data class SetComposing(val text: String) : CommitterCall()
    data object ClearComposing : CommitterCall()
    data class CommitFinal(val text: String) : CommitterCall()
    data class CommitText(val text: String) : CommitterCall()
}

class RecordingImeTextCommitter : ImeTextCommitter({ null }) {
    val calls = mutableListOf<CommitterCall>()

    override fun setComposing(text: String) {
        calls.add(CommitterCall.SetComposing(text))
    }

    override fun commitFinal(text: String) {
        calls.add(CommitterCall.CommitFinal(text))
    }

    override fun commitText(text: String) {
        calls.add(CommitterCall.CommitText(text))
    }

    override fun clearComposing() {
        calls.add(CommitterCall.ClearComposing)
    }
}

class FakeStreamingAsrEngine : StreamingAsrEngine {
    var partialsOnPoll: List<String> = listOf("", "", "hello", "hello world", "hello world", "hello world.")
    var finalizeResult: String = "hello world."
    var failBeginWith: Exception? = null
    var finalizeCalled = false
    var cancelCalled = false
    var acceptFrameThreadName: String? = null
    var onAcceptFrame: (() -> Unit)? = null
    var throwOnAcceptAfterFinalize = false

    private var pollIndex = 0
    private var initialized = false
    private var streamActive = false

    override fun init(config: ParakeetConfig) {
        initialized = true
    }

    override fun beginStream() {
        failBeginWith?.let { throw it }
        pollIndex = 0
        streamActive = true
    }

    override fun acceptFrame(pcm16k: ShortArray) {
        acceptFrameThreadName = Thread.currentThread().name
        if (throwOnAcceptAfterFinalize && !streamActive) {
            throw IllegalStateException("No active stream — call beginStream() first")
        }
        onAcceptFrame?.invoke()
    }

    override fun currentPartial(): String {
        val value = partialsOnPoll.getOrElse(pollIndex) { partialsOnPoll.last() }
        pollIndex++
        return value
    }

    override fun finalize(): String {
        finalizeCalled = true
        streamActive = false
        return finalizeResult
    }

    override fun cancel() {
        cancelCalled = true
        streamActive = false
    }

    override fun close() = Unit
}
