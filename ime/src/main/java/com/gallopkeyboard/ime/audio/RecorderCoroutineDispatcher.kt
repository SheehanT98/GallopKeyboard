package com.gallopkeyboard.ime.audio

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-thread dispatcher for the [AudioRecord] read loop.
 */
@Singleton
class RecorderCoroutineDispatcher @Inject constructor() {

    val dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AudioRecorder")
    }.asCoroutineDispatcher()
}
