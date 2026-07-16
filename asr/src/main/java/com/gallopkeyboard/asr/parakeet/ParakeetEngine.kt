package com.gallopkeyboard.asr.parakeet

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File

/**
 * Kotlin wrapper around sherpa-onnx [OnlineRecognizer] for streaming Parakeet transducer models.
 *
 * Not thread-safe — all calls must run on the recorder dispatcher in the IME layer.
 */
class ParakeetEngine(
    private val context: Context,
) : StreamingAsrEngine {

    companion object {
        private const val TAG = "ParakeetEngine"
        private const val SAMPLE_RATE = 16_000
    }

    private var config: ParakeetConfig? = null
    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private val floatScratch = FloatArray(1600)

    override fun init(config: ParakeetConfig) {
        val missing = config.missingFiles()
        if (missing.isNotEmpty()) {
            throw AsrModelMissingException(missing)
        }

        if (recognizer != null && this.config == config) return

        closeRecognizer()
        this.config = config

        val modelConfig = OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = config.encoderPath,
                decoder = config.decoderPath,
                joiner = config.joinerPath,
            ),
            tokens = config.tokensPath,
            numThreads = config.numThreads,
            provider = "cpu",
            modelType = "zipformer",
        )

        val recognizerConfig = OnlineRecognizerConfig(
            modelConfig = modelConfig,
            decodingMethod = "greedy_search",
            enableEndpoint = true,
        )

        recognizer = OnlineRecognizer(assetManager = null, config = recognizerConfig)
        Log.d(TAG, "initialized streaming Parakeet from ${File(config.encoderPath).parent}")
    }

    override fun beginStream() {
        ensureRecognizer()
        releaseStream()
        stream = recognizer!!.createStream()
        Log.d(TAG, "stream started on ${Thread.currentThread().name}")
    }

    override fun acceptFrame(pcm16k: ShortArray) {
        val r = recognizer ?: throw IllegalStateException("ParakeetEngine not initialized")
        val s = stream ?: throw IllegalStateException("No active stream — call beginStream() first")

        val len = pcm16k.size
        val samples = if (len <= floatScratch.size) {
            for (i in 0 until len) {
                floatScratch[i] = pcm16k[i] / 32768.0f
            }
            if (len == floatScratch.size) floatScratch else floatScratch.copyOf(len)
        } else {
            FloatArray(len) { i -> pcm16k[i] / 32768.0f }
        }
        s.acceptWaveform(samples, SAMPLE_RATE)

        while (r.isReady(s)) {
            r.decode(s)
        }
    }

    override fun currentPartial(): String {
        val r = recognizer ?: return ""
        val s = stream ?: return ""
        return r.getResult(s).text.trim()
    }

    override fun finalize(): String {
        val r = recognizer ?: return ""
        val s = stream ?: return ""

        s.inputFinished()
        while (r.isReady(s)) {
            r.decode(s)
        }
        val text = r.getResult(s).text.trim()
        releaseStream()
        Log.d(TAG, "finalize: \"$text\"")
        return text
    }

    override fun cancel() {
        releaseStream()
        Log.d(TAG, "stream cancelled")
    }

    override fun close() {
        cancel()
        closeRecognizer()
    }

    private fun ensureRecognizer() {
        if (recognizer == null) {
            val modelDir = File(context.filesDir, "models/${ParakeetConfig.MODEL_SUBDIR}")
            init(ParakeetConfig.fromModelDir(modelDir))
        }
    }

    private fun releaseStream() {
        stream?.release()
        stream = null
    }

    private fun closeRecognizer() {
        releaseStream()
        recognizer?.release()
        recognizer = null
        config = null
    }
}
