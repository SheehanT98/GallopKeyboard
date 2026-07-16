package com.gallopkeyboard.ime.audio

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StubTranscriber @Inject constructor() : Transcriber {
    override fun onSessionStart(session: AudioSession) {
        Log.d("AudioRecorder", "stub: session start")
    }

    override fun onAudioFrame(session: AudioSession, frame: ShortArray) = Unit

    override suspend fun onSessionStop(session: AudioSession) {
        val ms = session.durationMs()
        Log.d("AudioRecorder", "stub: would transcribe $ms ms of audio")
    }

    override fun onSessionCancel(session: AudioSession) {
        Log.d("AudioRecorder", "stub: session cancel")
    }
}
