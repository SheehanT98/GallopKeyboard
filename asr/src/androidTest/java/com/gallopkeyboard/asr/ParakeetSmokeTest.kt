package com.gallopkeyboard.asr

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gallopkeyboard.asr.parakeet.ParakeetConfig
import com.gallopkeyboard.asr.parakeet.ParakeetEngine
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Optional on-device smoke test — requires sideloaded Parakeet streaming models.
 * Run with `RUN_ASR_SMOKE=1` and models under `filesDir/models/parakeet/`.
 */
@RunWith(AndroidJUnit4::class)
class ParakeetSmokeTest {

    @Test
    fun referenceWavProducesHelloWorld() {
        assumeTrue("Set RUN_ASR_SMOKE=1 to run", System.getenv("RUN_ASR_SMOKE") == "1")

        val context = ApplicationProvider.getApplicationContext<Context>()
        val modelDir = File(context.filesDir, "models/${ParakeetConfig.MODEL_SUBDIR}")
        assumeTrue("Parakeet models missing", ParakeetConfig.requiredFiles(modelDir).all { it.isFile })

        val engine = ParakeetEngine(context)
        engine.init(ParakeetConfig.fromModelDir(modelDir))
        engine.beginStream()

        val pcm = loadReferencePcm(context)
        val frameSize = 1600
        var offset = 0
        while (offset < pcm.size) {
            val end = minOf(offset + frameSize, pcm.size)
            val chunk = pcm.copyOfRange(offset, end)
            val frame = if (chunk.size == frameSize) {
                chunk
            } else {
                ShortArray(frameSize).also { chunk.copyInto(it) }
            }
            engine.acceptFrame(frame)
            offset += frameSize
        }

        val transcript = engine.finalize().lowercase().replace(Regex("\\s+"), " ")
        engine.close()

        assert(transcript.contains("hello")) { "expected 'hello' in \"$transcript\"" }
        assert(transcript.contains("world")) { "expected 'world' in \"$transcript\"" }
    }

    private fun loadReferencePcm(context: Context): ShortArray {
        context.assets.open("reference-hello-world.wav").use { input ->
            val header = ByteArray(44)
            require(input.read(header) == 44) { "invalid wav header" }
            val dataSize = ByteBuffer.wrap(header, 40, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val pcmBytes = ByteArray(dataSize)
            var read = 0
            while (read < dataSize) {
                val n = input.read(pcmBytes, read, dataSize - read)
                require(n > 0) { "unexpected EOF in wav data" }
                read += n
            }
            val shorts = ShortArray(dataSize / 2)
            ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
            return shorts
        }
    }
}
