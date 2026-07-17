package com.gallopkeyboard.ime.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gallopkeyboard.core.theme.DictusTheme
import com.gallopkeyboard.core.theme.ThemeMode
import com.gallopkeyboard.ime.clipboard.ClipboardStore
import com.gallopkeyboard.ime.audio.AudioRecorderEngine
import com.gallopkeyboard.ime.audio.Transcriber
import com.gallopkeyboard.ime.model.KeyDefinition
import com.gallopkeyboard.ime.model.KeyType
import com.gallopkeyboard.ime.model.KeyboardLayer
import com.gallopkeyboard.ime.panel.ClipboardStrip
import com.gallopkeyboard.ime.panel.PermissionRequester
import timber.log.Timber

/**
 * Root composable for the Gallop Keyboard typing panel.
 *
 * Manages keyboard state (layer, shift, caps lock) and routes key presses
 * to InputConnection callbacks from [com.gallopkeyboard.ime.DictusImeService].
 */
@Composable
fun KeyboardScreen(
    onCommitText: (String) -> Unit,
    onDeleteBackward: () -> Unit,
    onSendReturn: () -> Unit,
    onVoicePanelToggle: () -> Unit,
    onClipboardPanelToggle: () -> Unit = {},
    audioRecorderEngine: AudioRecorderEngine,
    transcriber: Transcriber,
    permissionRequester: PermissionRequester,
    isEmojiPickerOpen: Boolean = false,
    onEmojiToggle: () -> Unit = {},
    onEmojiSelected: (String) -> Unit = {},
    themeMode: ThemeMode = ThemeMode.LIGHT,
    initialLayer: KeyboardLayer = KeyboardLayer.LETTERS,
    hapticsEnabled: Boolean = true,
    keyboardLayout: String = "qwerty",
    clipboardItems: List<String> = emptyList(),
    clipboardStore: ClipboardStore? = null,
) {
    var currentLayer by remember(initialLayer) { mutableStateOf(initialLayer) }
    var isShifted by remember { mutableStateOf(false) }
    var isCapsLock by remember { mutableStateOf(false) }
    var currentLayout by remember(keyboardLayout) { mutableStateOf(keyboardLayout) }
    var lastShiftTapTime by remember { mutableStateOf(0L) }
    var lastSpaceTapTime by remember { mutableStateOf(0L) }

    DictusTheme(themeMode = themeMode) {
        if (isEmojiPickerOpen) {
            EmojiPickerScreen(
                onEmojiSelected = { emoji ->
                    onEmojiSelected(emoji)
                },
                onReturnToKeyboard = onEmojiToggle,
                onDeleteBackward = onDeleteBackward,
                isDarkTheme = when (themeMode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.AUTO -> isSystemInDarkTheme()
                },
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                MicButtonRow(
                    onVoicePanelToggle = onVoicePanelToggle,
                    audioRecorderEngine = audioRecorderEngine,
                    transcriber = transcriber,
                    permissionRequester = permissionRequester,
                )

                Column(modifier = Modifier.height(310.dp)) {
                    if (clipboardStore != null && clipboardItems.isNotEmpty()) {
                        ClipboardStrip(
                            items = clipboardItems,
                            store = clipboardStore,
                            onInsert = onCommitText,
                        )
                    }
                    KeyboardView(
                        layer = currentLayer,
                        isShifted = isShifted,
                        isCapsLock = isCapsLock,
                        layout = currentLayout,
                        hapticsEnabled = hapticsEnabled,
                        onSwipeWord = { word ->
                            onCommitText(word)
                            if (isShifted && !isCapsLock) {
                                isShifted = false
                            }
                            Timber.d("Swipe word committed: %s", word)
                        },
                        onKeyPress = { key ->
                            handleKeyPress(
                                key = key,
                                isShifted = isShifted,
                                isCapsLock = isCapsLock,
                                currentLayer = currentLayer,
                                lastShiftTapTime = lastShiftTapTime,
                                lastSpaceTapTime = lastSpaceTapTime,
                                onCommitText = onCommitText,
                                onDeleteBackward = onDeleteBackward,
                                onSendReturn = onSendReturn,
                                onEmojiToggle = onEmojiToggle,
                                onVoicePanelToggle = onVoicePanelToggle,
                                onClipboardPanelToggle = onClipboardPanelToggle,
                                onSpaceTapTime = { lastSpaceTapTime = it },
                                onShiftChanged = { shifted, caps, tapTime ->
                                    isShifted = shifted
                                    isCapsLock = caps
                                    lastShiftTapTime = tapTime
                                    Timber.d("Shift toggled: %s, CapsLock: %s", shifted, caps)
                                },
                                onLayerChanged = { layer ->
                                    currentLayer = layer
                                    Timber.d("Layer switched to: %s", layer)
                                },
                                onAutoUnshift = {
                                    if (isShifted && !isCapsLock) {
                                        isShifted = false
                                    }
                                },
                            )
                        },
                        onAccentSelected = { accent ->
                            onCommitText(accent)
                            if (isShifted && !isCapsLock) {
                                isShifted = false
                            }
                            Timber.d("Accent selected: %s", accent)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Handles a key press based on its type.
 */
internal fun handleKeyPress(
    key: KeyDefinition,
    isShifted: Boolean,
    isCapsLock: Boolean,
    currentLayer: KeyboardLayer,
    lastShiftTapTime: Long,
    lastSpaceTapTime: Long,
    onCommitText: (String) -> Unit,
    onDeleteBackward: () -> Unit,
    onSendReturn: () -> Unit,
    onEmojiToggle: () -> Unit = {},
    onVoicePanelToggle: () -> Unit = {},
    onClipboardPanelToggle: () -> Unit = {},
    onSpaceTapTime: (Long) -> Unit = {},
    onShiftChanged: (shifted: Boolean, caps: Boolean, tapTime: Long) -> Unit,
    onLayerChanged: (KeyboardLayer) -> Unit,
    onAutoUnshift: () -> Unit,
) {
    Timber.d("Key pressed: %s", key.label)

    when (key.type) {
        KeyType.CHARACTER -> {
            val output = if (isShifted) key.output.uppercase() else key.output
            onCommitText(output)
            onAutoUnshift()
            onSpaceTapTime(0L)
        }
        KeyType.SPACE -> {
            val now = System.currentTimeMillis()
            if (lastSpaceTapTime > 0L && now - lastSpaceTapTime < 350L) {
                // Double-tap space → period + space (classic phone keyboard).
                onDeleteBackward()
                onCommitText(". ")
                onSpaceTapTime(0L)
            } else {
                onCommitText(" ")
                onSpaceTapTime(now)
            }
        }
        KeyType.RETURN -> {
            onSendReturn()
            onSpaceTapTime(0L)
        }
        KeyType.DELETE -> {
            onDeleteBackward()
            onSpaceTapTime(0L)
        }
        KeyType.SHIFT -> {
            val now = System.currentTimeMillis()
            if (now - lastShiftTapTime < 300) {
                onShiftChanged(!isCapsLock, !isCapsLock, now)
            } else {
                onShiftChanged(!isShifted, false, now)
            }
        }
        KeyType.LAYER_SWITCH -> {
            val newLayer = when (key.label) {
                "ABC" -> KeyboardLayer.LETTERS
                "?123", "123" -> KeyboardLayer.NUMBERS
                "#+=" -> KeyboardLayer.SYMBOLS
                else -> KeyboardLayer.LETTERS
            }
            onLayerChanged(newLayer)
        }
        KeyType.EMOJI -> {
            onEmojiToggle()
            Timber.d("Emoji picker toggled")
        }
        KeyType.MIC -> {
            onVoicePanelToggle()
            Timber.d("Mic key tapped — opening voice panel")
        }
        KeyType.CLIPBOARD -> {
            onClipboardPanelToggle()
            Timber.d("Clipboard key tapped")
        }
        KeyType.ACCENT_ADAPTIVE -> {
            onCommitText(key.output)
            onSpaceTapTime(0L)
        }
        KeyType.KEYBOARD_SWITCH -> {
            Timber.d("Keyboard switch requested")
        }
    }
}
