package com.gallopkeyboard.ime.panel

import com.gallopkeyboard.ime.audio.AudioSession
import com.gallopkeyboard.ime.audio.RingByteBuffer
import com.gallopkeyboard.ime.audio.Transcriber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSessionCleanupTest {

    @Test
    fun `cancelActiveSession invokes transcriber and returns null`() {
        val cancelled = mutableListOf<AudioSession>()
        val transcriber = object : Transcriber {
            override fun onSessionStart(session: AudioSession) = Unit
            override fun onAudioFrame(session: AudioSession, frame: ShortArray) = Unit
            override suspend fun onSessionStop(session: AudioSession) = Unit
            override fun onSessionCancel(session: AudioSession) {
                cancelled += session
            }
        }
        val session = AudioSession(startedAtElapsedMs = 0L, buffer = RingByteBuffer(1024))

        val result = cancelActiveSession(transcriber, session)

        assertNull(result)
        assertEquals(listOf(session), cancelled)
    }

    @Test
    fun `cancelActiveSession is no-op for null session`() {
        var cancelCount = 0
        val transcriber = object : Transcriber {
            override fun onSessionStart(session: AudioSession) = Unit
            override fun onAudioFrame(session: AudioSession, frame: ShortArray) = Unit
            override suspend fun onSessionStop(session: AudioSession) = Unit
            override fun onSessionCancel(session: AudioSession) {
                cancelCount++
            }
        }

        val result = cancelActiveSession(transcriber, null)

        assertNull(result)
        assertEquals(0, cancelCount)
    }

    @Test
    fun `cancelActiveSession is idempotent when called twice`() {
        var cancelCount = 0
        val transcriber = object : Transcriber {
            override fun onSessionStart(session: AudioSession) = Unit
            override fun onAudioFrame(session: AudioSession, frame: ShortArray) = Unit
            override suspend fun onSessionStop(session: AudioSession) = Unit
            override fun onSessionCancel(session: AudioSession) {
                cancelCount++
            }
        }
        val session = AudioSession(startedAtElapsedMs = 0L, buffer = RingByteBuffer(1024))

        cancelActiveSession(transcriber, session)
        cancelActiveSession(transcriber, null)

        assertEquals(1, cancelCount)
    }
}
