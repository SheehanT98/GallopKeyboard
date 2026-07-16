package com.gallopkeyboard.ime.audio

import android.os.SystemClock

/**
 * In-memory recording session handed to [Transcriber] on stop.
 */
data class AudioSession(
    val startedAtElapsedMs: Long,
    var stoppedAtElapsedMs: Long? = null,
    val buffer: RingByteBuffer,
) {
    fun durationMs(): Long {
        val end = stoppedAtElapsedMs ?: SystemClock.elapsedRealtime()
        return (end - startedAtElapsedMs).coerceAtLeast(0L)
    }
}
