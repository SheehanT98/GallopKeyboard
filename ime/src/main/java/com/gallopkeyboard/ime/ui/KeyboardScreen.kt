package com.gallopkeyboard.ime.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import com.gallopkeyboard.core.theme.DictusTheme
import com.gallopkeyboard.core.theme.ThemeMode
import com.gallopkeyboard.ime.R
import com.gallopkeyboard.ime.model.KeyDefinition
import com.gallopkeyboard.ime.clipboard.ClipboardStore
import com.gallopkeyboard.ime.model.KeyboardLayer
import com.gallopkeyboard.ime.model.KeyType
import com.gallopkeyboard.ime.panel.ClipboardStrip
import timber.log.Timber

/**
 * Root composable for the Dictus keyboard.
 *
 * Manages all keyboard state (layer, shift, caps lock) and
 * routes key press events to the appropriate InputConnection callbacks
 * provided by DictusImeService.
 *
 * Total height: 36.dp (suggestion bar, when visible) + 46.dp (mic row) + 264.dp (keyboard) = 346.dp max.
 * When no suggestions are available, the suggestion bar is hidden: 310.dp total.
 *
 * The emoji picker state is hoisted to DictusImeService so that back key
 * dismissal can be handled via onKeyDown override (BackHandler does not work
 * in IME context).
 */
@Composable
fun KeyboardScreen(
    onCommitText: (String) -> Unit,
    onDeleteBackward: () -> Unit,
    onSendReturn: () -> Unit,
    onSwitchKeyboard: () -> Unit,
    onMicTap: () -> Unit = {},
    onVoicePanelToggle: (() -> Unit)? = null,
    isEmojiPickerOpen: Boolean = false,
    onEmojiToggle: () -> Unit = {},
    onEmojiSelected: (String) -> Unit = {},
    currentWord: String = "",
    suggestions: List<String> = emptyList(),
    onSuggestionSelected: (String) -> Unit = {},
    onCurrentWordSelected: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.DARK,
    initialLayer: KeyboardLayer = KeyboardLayer.LETTERS,
    hapticsEnabled: Boolean = true,
    keyboardLayout: String = "azerty",  // NEW — AZERTY/QWERTY from DataStore
    clipboardItems: List<String> = emptyList(),
    clipboardStore: ClipboardStore? = null,
) {
    // Keyboard state — initialLayer drives the starting layer from the KEYBOARD_MODE preference.
    // remember(initialLayer) ensures recomposition resets the layer if the preference changes.
    var currentLayer by remember(initialLayer) { mutableStateOf(initialLayer) }
    var isShifted by remember { mutableStateOf(false) }
    var isCapsLock by remember { mutableStateOf(false) }
    // keyboardLayout comes from DataStore via DictusImeService.
    // remember(keyboardLayout) resets the state when the preference changes in Settings,
    // so the user sees the new layout immediately without restarting the keyboard.
    var currentLayout by remember(keyboardLayout) { mutableStateOf(keyboardLayout) }

    // Track last tap time for double-tap shift detection
    var lastShiftTapTime by remember { mutableStateOf(0L) }

    DictusTheme(themeMode = themeMode) {
        if (isEmojiPickerOpen) {
            EmojiPickerScreen(
                onEmojiSelected = { emoji ->
                    onEmojiSelected(emoji)
                    // Stay in emoji picker for rapid emoji entry (don't auto-dismiss)
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
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Suggestion bar (36.dp) — always visible with 3 slots and separators
                    SuggestionBar(
                        currentWord = currentWord,
                        suggestions = suggestions,
                        onSuggestionSelected = onSuggestionSelected,
                        onCurrentWordSelected = onCurrentWordSelected,
                    )

                    // Mic button row above keyboard (46.dp)
                    MicButtonRow(
                        onSwitchKeyboard = onSwitchKeyboard,
                        onMicTap = onMicTap,
                        isRecording = false,
                    )

                    // Keyboard area (264.dp) for 48.dp keys with existing row spacing.
                    Column(modifier = Modifier.height(264.dp)) {
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
                            onKeyPress = { key ->
                                handleKeyPress(
                                    key = key,
                                    isShifted = isShifted,
                                    isCapsLock = isCapsLock,
                                    currentLayer = currentLayer,
                                    lastShiftTapTime = lastShiftTapTime,
                                    onCommitText = onCommitText,
                                    onDeleteBackward = onDeleteBackward,
                                    onSendReturn = onSendReturn,
                                    onEmojiToggle = onEmojiToggle,
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
                                        // Turn off shift after typing a character (unless caps lock)
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

                // Bottom-right voice panel toggle — overlays empty key corner (HANDOFF UX).
                if (onVoicePanelToggle != null) {
                    IconButton(
                        onClick = onVoicePanelToggle,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = stringResource(R.string.panel_toggle_voice),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Handles a key press based on its type.
 *
 * This is extracted as a top-level function (not a lambda) to keep
 * KeyboardScreen composable lean and testable.
 */
private fun handleKeyPress(
    key: KeyDefinition,
    isShifted: Boolean,
    isCapsLock: Boolean,
    currentLayer: KeyboardLayer,
    lastShiftTapTime: Long,
    onCommitText: (String) -> Unit,
    onDeleteBackward: () -> Unit,
    onSendReturn: () -> Unit,
    onEmojiToggle: () -> Unit = {},
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
        }
        KeyType.SPACE -> {
            onCommitText(" ")
        }
        KeyType.RETURN -> {
            onSendReturn()
        }
        KeyType.DELETE -> {
            onDeleteBackward()
        }
        KeyType.SHIFT -> {
            val now = System.currentTimeMillis()
            if (now - lastShiftTapTime < 300) {
                // Double-tap: toggle caps lock
                onShiftChanged(!isCapsLock, !isCapsLock, now)
            } else {
                // Single tap: toggle shift
                onShiftChanged(!isShifted, false, now)
            }
        }
        KeyType.LAYER_SWITCH -> {
            val newLayer = when (key.label) {
                "ABC" -> KeyboardLayer.LETTERS
                "?123" -> KeyboardLayer.NUMBERS
                "123" -> KeyboardLayer.NUMBERS
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
            Timber.d("Mic not yet implemented")
        }
        KeyType.ACCENT_ADAPTIVE -> {
            // Commit the apostrophe character
            onCommitText(key.output)
        }
        KeyType.KEYBOARD_SWITCH -> {
            // Handled by the service directly
            Timber.d("Keyboard switch requested")
        }
    }
}
