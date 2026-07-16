package com.gallopkeyboard.ime.asr

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.gallopkeyboard.asr.parakeet.StreamingAsrEngine
import com.gallopkeyboard.core.preferences.PreferenceKeys
import com.gallopkeyboard.whisper.AsrPolishEngine
import com.gallopkeyboard.whisper.WhisperConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unloads ASR engines after the voice panel has been idle for [UNLOAD_DELAY_MS]
 * unless [PreferenceKeys.MODELS_KEEP_LOADED] is enabled.
 */
@Singleton
class ModelLifecycleManager @Inject constructor(
    private val streamingEngine: StreamingAsrEngine,
    private val polishEngine: AsrPolishEngine,
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context,
) : ModelLifecycleController {

    companion object {
        private const val TAG = "ModelLifecycleManager"
        const val UNLOAD_DELAY_MS = 60_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var unloadJob: Job? = null
    private var sessionActive = false
    private var enginesUnloaded = false

    override fun onSessionStarted() {
        sessionActive = true
        unloadJob?.cancel()
        unloadJob = null
        if (enginesUnloaded) {
            ensurePolishInitialized()
            enginesUnloaded = false
            Timber.d("$TAG: engines will re-init on next transcription")
        }
    }

    override fun onSessionStopped() {
        sessionActive = false
        scheduleUnload()
    }

    override fun onVoicePanelHidden() {
        scheduleUnload()
    }

    override fun onVoicePanelShown() {
        scheduleUnload()
    }

    private fun scheduleUnload() {
        unloadJob?.cancel()
        unloadJob = scope.launch {
            if (modelsKeepLoaded()) return@launch
            delay(UNLOAD_DELAY_MS)
            if (sessionActive) return@launch
            if (modelsKeepLoaded()) return@launch
            unloadEngines()
        }
    }

    private suspend fun modelsKeepLoaded(): Boolean =
        dataStore.data.map { it[PreferenceKeys.MODELS_KEEP_LOADED] ?: false }.first()

    private fun unloadEngines() {
        try {
            streamingEngine.close()
            polishEngine.close()
            enginesUnloaded = true
            Timber.d("$TAG: unloaded streaming + polish engines after ${UNLOAD_DELAY_MS}ms idle")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: engine unload failed")
        }
    }

    private fun ensurePolishInitialized() {
        val modelDir = File(context.filesDir, "models/${WhisperConfig.MODEL_SUBDIR}")
        polishEngine.init(WhisperConfig.fromModelDir(modelDir))
    }
}
