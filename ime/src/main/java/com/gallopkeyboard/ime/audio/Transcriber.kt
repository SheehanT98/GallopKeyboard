package com.gallopkeyboard.ime.audio

/**
 * Seam between the audio pipeline and ASR (Plans 006/007 implement this).
 */
interface Transcriber {
    /** Called when a recording session begins (tap confirmed or hold threshold). */
    fun onSessionStart(session: AudioSession)

    /** Called every ~100 ms with new PCM frames while recording. */
    fun onAudioFrame(session: AudioSession, frame: ShortArray)

    /** Called on stop. Producers commit final text here. */
    suspend fun onSessionStop(session: AudioSession)

    /** Called on gesture cancel. */
    fun onSessionCancel(session: AudioSession)
}
