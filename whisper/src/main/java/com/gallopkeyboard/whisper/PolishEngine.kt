package com.gallopkeyboard.whisper

import android.util.Log
import kotlinx.coroutines.runBlocking

/**
 * Thin wrapper over Dictus [WhisperContext] for the IME polish pass on stop.
 *
 * [WhisperContext.transcribeData] is suspend (single-thread dispatcher inside
 * whisper.cpp) — callers must not wrap [transcribe] in a blocking dispatcher.
 */
interface AsrPolishEngine : AutoCloseable {
    fun init(config: WhisperConfig)

    /** Full-buffer transcription of 16 kHz mono PCM shorts. */
    suspend fun transcribe(pcm16k: ShortArray): String

    /** Best-effort cancel; JNI whisper_full_cancel is not exposed in v1. */
    fun cancel()
}

class PolishEngine : AsrPolishEngine {

    companion object {
        private const val TAG = "WhisperPolish"
    }

    private var config: WhisperConfig? = null
    private var whisperContext: WhisperContext? = null
    @Volatile
    private var cancelled = false

    override fun init(config: WhisperConfig) {
        this.config = config
    }

    override suspend fun transcribe(pcm16k: ShortArray): String {
        cancelled = false
        val cfg = config ?: throw IllegalStateException("PolishEngine not initialized")
        if (cfg.missingFiles().isNotEmpty()) {
            throw java.io.IOException("Whisper model missing: ${cfg.modelPath}")
        }

        val ctx = ensureContext(cfg)
        val samples = pcmToFloat(pcm16k)
        Log.d(TAG, "polish transcribe: ${samples.size} samples, threads=${cfg.nThreads}")
        val raw = ctx.transcribeData(samples, cfg.language)
        if (cancelled) {
            Log.w(TAG, "polish cancelled after native return (JNI is cancellation-unaware)")
        }
        return raw.trim()
    }

    override fun cancel() {
        cancelled = true
        // whisper_full_cancel not exposed by WhisperLib in v1 — native work may continue.
    }

    override fun close() {
        runBlocking {
            whisperContext?.release()
        }
        whisperContext = null
        config = null
    }

    private suspend fun ensureContext(cfg: WhisperConfig): WhisperContext {
        val existing = whisperContext
        if (existing != null && existing.isValid()) return existing

        val loaded = WhisperContext.createFromFile(cfg.modelPath)
            ?: throw java.io.IOException("Failed to load Whisper model at ${cfg.modelPath}")
        whisperContext = loaded
        return loaded
    }

    private fun pcmToFloat(pcm16k: ShortArray): FloatArray =
        FloatArray(pcm16k.size) { i -> pcm16k[i] / 32768.0f }
}
