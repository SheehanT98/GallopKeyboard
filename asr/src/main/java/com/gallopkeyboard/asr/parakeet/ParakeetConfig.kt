package com.gallopkeyboard.asr.parakeet

import java.io.File

/**
 * Paths and decoding parameters for streaming Parakeet (sherpa-onnx transducer).
 */
data class ParakeetConfig(
    val encoderPath: String,
    val decoderPath: String,
    val joinerPath: String,
    val tokensPath: String,
    val numThreads: Int = 2,
) {
    companion object {
        const val MODEL_SUBDIR = "parakeet"

        fun fromModelDir(dir: File): ParakeetConfig = ParakeetConfig(
            encoderPath = File(dir, "encoder.onnx").absolutePath,
            decoderPath = File(dir, "decoder.onnx").absolutePath,
            joinerPath = File(dir, "joiner.onnx").absolutePath,
            tokensPath = File(dir, "tokens.txt").absolutePath,
        )

        fun requiredFiles(dir: File): List<File> = listOf(
            File(dir, "encoder.onnx"),
            File(dir, "decoder.onnx"),
            File(dir, "joiner.onnx"),
            File(dir, "tokens.txt"),
        )
    }

    fun missingFiles(): List<String> = buildList {
        if (!File(encoderPath).isFile) add(encoderPath)
        if (!File(decoderPath).isFile) add(decoderPath)
        if (!File(joinerPath).isFile) add(joinerPath)
        if (!File(tokensPath).isFile) add(tokensPath)
    }
}
