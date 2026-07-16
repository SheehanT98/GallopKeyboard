package com.gallopkeyboard.ime.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

class AudioRecorderException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

@Singleton
class AudioRecorderEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recorderDispatcher: RecorderCoroutineDispatcher,
) {

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION
        private const val MIN_BUFFER_BYTES = 3200 // 200 ms at 16 kHz mono PCM16
        private const val FRAME_SHORTS = 1600 // 100 ms at 16 kHz

        fun checkPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    /**
     * Cold flow that owns [AudioRecord] for its lifetime.
     * Collect on [RecorderCoroutineDispatcher] (via [flowOn]); never on Main.
     */
    fun start(): Flow<ShortArray> = flow {
        if (!checkPermission(context)) {
            throw AudioRecorderException("RECORD_AUDIO permission not granted")
        }

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize <= 0) {
            throw AudioRecorderException(
                "AudioRecord.getMinBufferSize returned $minBufferSize — mic may be unavailable",
            )
        }

        val bufferSizeInBytes = maxOf(minBufferSize, MIN_BUFFER_BYTES)
        val recorder = AudioRecord(
            AUDIO_SOURCE,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSizeInBytes,
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw AudioRecorderException("AudioRecord failed to initialize (state=${recorder.state})")
        }

        try {
            recorder.startRecording()
            val frame = ShortArray(FRAME_SHORTS)
            val readBuffer = ShortArray(FRAME_SHORTS)
            var frameOffset = 0

            while (currentCoroutineContext().isActive) {
                val read = recorder.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)
                if (read < 0) {
                    throw AudioRecorderException("AudioRecord.read returned $read")
                }
                if (read == 0) continue

                var srcOffset = 0
                while (srcOffset < read) {
                    val toCopy = minOf(read - srcOffset, FRAME_SHORTS - frameOffset)
                    System.arraycopy(readBuffer, srcOffset, frame, frameOffset, toCopy)
                    frameOffset += toCopy
                    srcOffset += toCopy
                    if (frameOffset == FRAME_SHORTS) {
                        emit(frame.copyOf())
                        frameOffset = 0
                    }
                }
            }
        } catch (e: AudioRecorderException) {
            throw e
        } catch (e: Exception) {
            throw AudioRecorderException("AudioRecord loop failed", e)
        } finally {
            try {
                recorder.stop()
            } catch (_: IllegalStateException) {
                // Already stopped or never started cleanly.
            }
            recorder.release()
            Log.d("AudioRecorder", "AudioRecord released")
        }
    }.flowOn(recorderDispatcher.dispatcher)
}
