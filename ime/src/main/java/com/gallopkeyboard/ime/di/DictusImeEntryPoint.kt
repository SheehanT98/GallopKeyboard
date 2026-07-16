package com.gallopkeyboard.ime.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.gallopkeyboard.ime.audio.AudioRecorderEngine
import com.gallopkeyboard.ime.audio.Transcriber
import com.gallopkeyboard.ime.asr.InputConnectionSupplier
import com.gallopkeyboard.ime.asr.ModelLifecycleController
import com.gallopkeyboard.ime.asr.ModelLifecycleManager
import com.gallopkeyboard.ime.asr.VoiceModelPromptState
import com.gallopkeyboard.ime.panel.PermissionRequester
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt entry point for DictusImeService.
 *
 * InputMethodService cannot use @AndroidEntryPoint directly, so we use
 * EntryPointAccessors.fromApplication() to retrieve dependencies.
 *
 * WHY dataStore here: DictusImeService needs to read the THEME preference
 * to apply the correct light/dark color scheme to the keyboard UI.
 * The DataStore singleton is provided by DataStoreModule in the app module
 * and shared via the Hilt SingletonComponent.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DictusImeEntryPoint {
    fun dataStore(): DataStore<Preferences>
    fun audioRecorderEngine(): AudioRecorderEngine
    fun transcriber(): Transcriber
    fun permissionRequester(): PermissionRequester
    fun inputConnectionSupplier(): InputConnectionSupplier
    fun voiceModelPromptState(): VoiceModelPromptState
    fun modelLifecycleManager(): ModelLifecycleController
}
