package com.gallopkeyboard.ime

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.EntryPointAccessors
import com.gallopkeyboard.core.log.CrashHandler
import com.gallopkeyboard.core.models.ModelInstaller
import com.gallopkeyboard.core.models.ModelRegistry
import com.gallopkeyboard.core.preferences.PreferenceKeys
import com.gallopkeyboard.core.theme.ThemeMode
import com.gallopkeyboard.ime.di.DictusImeEntryPoint
import com.gallopkeyboard.ime.suggestion.DictionaryEngine
import com.gallopkeyboard.ime.suggestion.SuggestionEngine
import com.gallopkeyboard.ime.ui.KeyboardScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.gallopkeyboard.ime.panel.PanelController
import com.gallopkeyboard.ime.panel.PanelHost
import com.gallopkeyboard.ime.panel.PanelState
import com.gallopkeyboard.ime.panel.VoicePanelDependencies
import com.gallopkeyboard.ime.clipboard.ClipboardStore
import com.gallopkeyboard.ime.clipboard.ClipboardWatcher
import com.gallopkeyboard.ime.clipboard.PinnedClipboardStore
import com.gallopkeyboard.ime.model.KeyboardLayer

/**
 * Main IME service for GallopKeyboard.
 *
 * Extends LifecycleInputMethodService to get Compose lifecycle wiring.
 * Uses Hilt EntryPointAccessors (not @AndroidEntryPoint) because
 * InputMethodService is not supported by Hilt's standard injection.
 *
 * Voice dictation uses the hybrid PanelHost path (SmartVoiceButton +
 * PolishingTranscriber). This service does not bind DictationService —
 * that foreground service remains for companion-app test recording only.
 */
class DictusImeService : LifecycleInputMethodService() {

    private val entryPoint: DictusImeEntryPoint by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            DictusImeEntryPoint::class.java,
        )
    }

    private val bindingScope = MainScope()

    private val panelController = PanelController()

    private val clipboardStore = ClipboardStore()
    private val pinnedClipboardStore: PinnedClipboardStore by lazy {
        PinnedClipboardStore(entryPoint.dataStore(), bindingScope)
    }
    private lateinit var clipboardWatcher: ClipboardWatcher

    // Emoji picker visibility state, hoisted here so back key can dismiss it via onKeyDown.
    // BackHandler (Compose) does not work in IME context -- back key is not dispatched through
    // the Compose back handler stack in an InputMethodService.
    private val _isEmojiPickerOpen = MutableStateFlow(false)

    // Whether the built-in suggestion bar is enabled. Observed from DataStore
    // so the user can toggle it in settings without restarting the IME.
    private val _suggestionsEnabled = MutableStateFlow(false)

    // Production suggestion engine: loads AOSP FR/EN dictionary from assets on
    // Dispatchers.IO, performs accent-insensitive prefix matching with frequency
    // ranking, and boosts personal dictionary words. Until dictionary loads
    // (~500ms), returns empty suggestions gracefully.
    private val suggestionEngine: SuggestionEngine by lazy {
        DictionaryEngine(
            context = applicationContext,
            dataStore = entryPoint.dataStore(),
            coroutineScope = bindingScope,
        )
    }
    private val _currentWord = MutableStateFlow("")
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        Timber.d("DictusImeService created")
        clipboardWatcher = ClipboardWatcher(applicationContext, clipboardStore)
        clipboardWatcher.start()

        val installer = ModelInstaller(applicationContext)
        if (!installer.areFilesPresent(ModelRegistry.defaultVoiceBundle)) {
            entryPoint.voiceModelPromptState().showBanner()
        }
        bindingScope.launch(Dispatchers.IO) {
            val corrupt = installer.verifyInstalledIfDue()
            if (corrupt) {
                withContext(Dispatchers.Main) {
                    entryPoint.voiceModelPromptState().showBanner()
                }
            }
        }

        // Observe suggestions toggle from DataStore (defaults to false — bar hidden in v1 UX)
        bindingScope.launch {
            entryPoint.dataStore().data
                .map { it[PreferenceKeys.SUGGESTIONS_ENABLED] ?: false }
                .collect { enabled -> _suggestionsEnabled.value = enabled }
        }
    }

    override fun onDestroy() {
        if (::clipboardWatcher.isInitialized) {
            clipboardWatcher.stop()
        }
        bindingScope.cancel()
        super.onDestroy()
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        entryPoint.inputConnectionSupplier().supplier = { currentInputConnection }
        refreshClipboardStrip()
        if (!restarting) {
            panelController.reset()
        }
    }

    override fun onWindowShown() {
        super.onWindowShown()
        // Android 12+ often skips OnPrimaryClipChangedListener in the IME process.
        // Refresh whenever the keyboard window appears so the strip stays current.
        refreshClipboardStrip()
    }

    private fun refreshClipboardStrip() {
        if (::clipboardWatcher.isInitialized) {
            clipboardWatcher.refreshFromPrimaryClip()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        entryPoint.inputConnectionSupplier().supplier = { null }
        panelController.reset()
        super.onFinishInputView(finishingInput)
    }

    /**
     * Intercepts KEYCODE_BACK to dismiss the emoji picker when it is open.
     *
     * WHY onKeyDown instead of BackHandler (Compose): In an InputMethodService,
     * the back key is routed through the View/Window system, not through the
     * Compose navigation back stack. BackHandler silently does nothing in this context.
     * Overriding onKeyDown is the correct approach for IME services.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (_isEmojiPickerOpen.value) {
                _isEmojiPickerOpen.value = false
                return true
            }
            if (panelController.state.value == PanelState.CLIPBOARD) {
                panelController.showTyping()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Called by the system when the selection or cursor position in the editor changes.
     *
     * Used to extract the current word being typed and update suggestions in real time.
     * We read up to 50 characters before the cursor and split on whitespace/newline to
     * isolate the last word fragment, then feed it to the SuggestionEngine.
     */
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd,
            newSelStart, newSelEnd,
            candidatesStart, candidatesEnd,
        )
        val ic = currentInputConnection ?: return
        val beforeCursor = ic.getTextBeforeCursor(50, 0)?.toString() ?: ""
        val currentWord = beforeCursor.split(" ", "\n").lastOrNull() ?: ""

        _currentWord.value = currentWord
        _suggestions.value = if (_suggestionsEnabled.value) {
            suggestionEngine.getSuggestions(currentWord)
        } else {
            emptyList()
        }
    }

    @Composable
    override fun KeyboardContent() {
        val isEmojiPickerOpen by _isEmojiPickerOpen.collectAsState()

        // Read theme preference from DataStore and map to ThemeMode.
        // The entryPoint provides DataStore access via Hilt SingletonComponent.
        val themeKey by entryPoint.dataStore().data
            .map { it[PreferenceKeys.THEME] ?: "light" }
            .collectAsState(initial = "light")
        val themeMode = when (themeKey) {
            "dark" -> ThemeMode.DARK
            "auto" -> ThemeMode.AUTO
            else -> ThemeMode.LIGHT
        }

        // Read keyboard mode preference to set the initial layer when the keyboard opens.
        // "123" starts in the NUMBERS layer; everything else defaults to LETTERS.
        val keyboardModeKey by entryPoint.dataStore().data
            .map { it[PreferenceKeys.KEYBOARD_MODE] ?: "abc" }
            .collectAsState(initial = "abc")
        val initialLayer = if (keyboardModeKey == "123") KeyboardLayer.NUMBERS else KeyboardLayer.LETTERS

        // Read haptics enabled preference to conditionally suppress key vibration.
        val hapticsEnabled by entryPoint.dataStore().data
            .map { it[PreferenceKeys.HAPTICS_ENABLED] ?: true }
            .collectAsState(initial = true)

        // Read keyboard layout preference (AZERTY vs QWERTY).
        // WHY collectAsState: This is a Compose composable, so we use the DataStore Flow
        // + collectAsState() pattern (not coroutine-scope collect). When the user toggles
        // the layout in Settings, DataStore emits a new value, Compose recomposes, and
        // KeyboardScreen receives the updated layout immediately.
        val keyboardLayout by entryPoint.dataStore().data
            .map { it[PreferenceKeys.KEYBOARD_LAYOUT] ?: "qwerty" }
            .collectAsState(initial = "qwerty")

        val clipboardItems by clipboardStore.itemsFlow.collectAsState()
        val pinnedClipboardEntries by pinnedClipboardStore.entriesFlow.collectAsState()

        PanelHost(
            controller = panelController,
            themeMode = themeMode,
            voiceDependencies = {
                VoicePanelDependencies(
                    audioRecorderEngine = entryPoint.audioRecorderEngine(),
                    transcriber = entryPoint.transcriber(),
                    permissionRequester = entryPoint.permissionRequester(),
                    promptState = entryPoint.voiceModelPromptState(),
                    modelLifecycleManager = entryPoint.modelLifecycleManager(),
                )
            },
            pinnedClipboardEntries = pinnedClipboardEntries,
            recentClipboardTexts = clipboardItems,
            onClipboardInsert = { text -> commitText(text) },
            onClipboardTogglePin = { text -> pinnedClipboardStore.togglePin(text) },
            isClipboardPinned = { text -> pinnedClipboardStore.isPinned(text) },
        ) {
            KeyboardScreen(
                onCommitText = { text -> commitText(text) },
                onDeleteBackward = { deleteBackward() },
                onSendReturn = { sendReturnKey() },
                onVoicePanelToggle = panelController::showVoice,
                onClipboardPanelToggle = panelController::showClipboard,
                onMicTap = panelController::showVoice,
                isEmojiPickerOpen = isEmojiPickerOpen,
                onEmojiToggle = { _isEmojiPickerOpen.value = !_isEmojiPickerOpen.value },
                onEmojiSelected = { emoji -> commitText(emoji) },
                themeMode = themeMode,
                initialLayer = initialLayer,
                hapticsEnabled = hapticsEnabled,
                keyboardLayout = keyboardLayout,
                clipboardItems = clipboardItems,
                clipboardStore = clipboardStore,
            )
        }
    }

    /**
     * Commits text to the currently focused editor field.
     */
    fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    /**
     * Deletes one character before the cursor.
     */
    fun deleteBackward() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    /**
     * Sends an Enter/Return key event to the editor.
     */
    fun sendReturnKey() {
        currentInputConnection?.sendKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER),
        )
        currentInputConnection?.sendKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER),
        )
    }
}
