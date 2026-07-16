package com.gallopkeyboard.whisper

import java.io.File

/**
 * Paths and decoding parameters for Whisper polish (non-streaming full-buffer pass).
 */
data class WhisperConfig(
    val modelPath: String,
    val language: String = "en",
    val initialPrompt: String = "",
    val maxTokens: Int = 0,
    val nThreads: Int = Runtime.getRuntime().availableProcessors().coerceAtMost(4),
) {
    companion object {
        const val MODEL_SUBDIR = "whisper"
        const val DEFAULT_MODEL_FILE = "base.en.gguf"

        /** Preferred model filenames in order (first existing file wins). */
        private val MODEL_CANDIDATES = listOf(
            "base.en.gguf",
            "small.en.gguf",
            "ggml-base.en.bin",
            "ggml-base.bin",
        )

        fun fromModelDir(dir: File): WhisperConfig {
            val modelFile = MODEL_CANDIDATES
                .map { File(dir, it) }
                .firstOrNull { it.isFile }
                ?: File(dir, DEFAULT_MODEL_FILE)
            return WhisperConfig(modelPath = modelFile.absolutePath)
        }

        fun requiredFiles(dir: File): List<File> =
            MODEL_CANDIDATES.map { File(dir, it) }
    }

    fun missingFiles(): List<String> =
        if (File(modelPath).isFile) emptyList() else listOf(modelPath)
}
