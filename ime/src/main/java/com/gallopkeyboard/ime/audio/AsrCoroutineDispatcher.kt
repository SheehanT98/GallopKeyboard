package com.gallopkeyboard.ime.audio

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-thread dispatcher for streaming ASR inference ([acceptFrame], partial decode).
 * Separate from [RecorderCoroutineDispatcher] so AudioRecord I/O is not starved by ONNX decode.
 */
@Singleton
class AsrCoroutineDispatcher @Inject constructor() {

    val dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AsrEngine")
    }.asCoroutineDispatcher()
}
