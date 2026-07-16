package com.gallopkeyboard.asr.parakeet

/**
 * Minimal streaming ASR seam — implementations wrap sherpa-onnx (or fakes in tests).
 */
interface StreamingAsrEngine : AutoCloseable {
    fun init(config: ParakeetConfig)
    fun beginStream()
    fun acceptFrame(pcm16k: ShortArray)
    fun currentPartial(): String
    fun finalize(): String
    fun cancel()
}
