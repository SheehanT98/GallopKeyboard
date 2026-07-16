package com.gallopkeyboard.core.models

/**
 * Static registry of GallopKeyboard voice model files.
 *
 * URLs and SHA-256 values are pinned to specific releases; see [docs/models.md].
 * Parakeet streaming zipformer int8 (2023-06-26) + Whisper GGML English models.
 */
object ModelRegistry {
    private const val HF_ZIPFORMER =
        "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26/resolve/main"
    private const val HF_WHISPER =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main"

    /** Streaming Parakeet transducer files under `models/parakeet/`. */
    val parakeetBundle: List<ModelSpec> = listOf(
        ModelSpec(
            id = "parakeet-encoder",
            url = "$HF_ZIPFORMER/encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx",
            sha256 = "5022b2eca5b19d1bc104fcf33e26bc32604b7df553cd2e1f62e31dc7b05e9c87",
            sizeBytes = 70_108_816L,
            relPath = "models/parakeet/encoder.onnx",
        ),
        ModelSpec(
            id = "parakeet-decoder",
            url = "$HF_ZIPFORMER/decoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx",
            sha256 = "780c63ee94c7cfa314211172e5d09b406c0da2beab5c40ea2f54cc95670b76a5",
            sizeBytes = 540_688L,
            relPath = "models/parakeet/decoder.onnx",
        ),
        ModelSpec(
            id = "parakeet-joiner",
            url = "$HF_ZIPFORMER/joiner-epoch-99-avg-1-chunk-16-left-128.int8.onnx",
            sha256 = "abd5e30f3f16fc510605c6029dba33f10e4386bd75c5bdc30cf94076864db10d",
            sizeBytes = 259_416L,
            relPath = "models/parakeet/joiner.onnx",
        ),
        ModelSpec(
            id = "parakeet-tokens",
            url = "$HF_ZIPFORMER/tokens.txt",
            sha256 = "49e3c2646595fd907228b3c6787069658f67b17377c60aeb8619c4551b2316fb",
            sizeBytes = 5_048L,
            relPath = "models/parakeet/tokens.txt",
        ),
    )

    /** Default Whisper polish tier (~140 MB). Saved as `base.en.gguf`. */
    val whisperBase: ModelSpec = ModelSpec(
        id = "whisper-base",
        url = "$HF_WHISPER/ggml-base.en.bin",
        sha256 = "a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002",
        sizeBytes = 147_964_211L,
        relPath = "models/whisper/base.en.gguf",
    )

    /** Optional higher-accuracy Whisper tier (~470 MB). */
    val whisperSmall: ModelSpec = ModelSpec(
        id = "whisper-small",
        url = "$HF_WHISPER/ggml-small.en.bin",
        sha256 = "c6138d6d58ecc8322097e0f987c32f1be8bb0a18532a3f88f734d1bbf9c41e5d",
        sizeBytes = 487_614_201L,
        relPath = "models/whisper/small.en.gguf",
    )

    /** Parakeet + Whisper base bundle for first-launch onboarding. */
    val defaultVoiceBundle: List<ModelSpec> = parakeetBundle + whisperBase

    /** Approximate total download size for the default bundle (bytes). */
    fun defaultBundleSizeBytes(): Long = defaultVoiceBundle.sumOf { it.sizeBytes }

    /** All specs tracked in the models settings screen. */
    val allSpecs: List<ModelSpec> = parakeetBundle + listOf(whisperBase, whisperSmall)

    fun findById(id: String): ModelSpec? = allSpecs.find { it.id == id }
}
