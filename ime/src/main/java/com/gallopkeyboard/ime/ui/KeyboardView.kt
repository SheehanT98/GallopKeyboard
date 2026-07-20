package com.gallopkeyboard.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.gallopkeyboard.ime.haptics.HapticHelper
import com.gallopkeyboard.ime.model.AccentMap
import com.gallopkeyboard.ime.model.KeyDefinition
import com.gallopkeyboard.ime.model.KeyType
import com.gallopkeyboard.ime.model.KeyboardLayer
import com.gallopkeyboard.ime.model.KeyboardLayouts
import com.gallopkeyboard.ime.suggestion.SuggestionEngine
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Renders the keyboard rows for the currently active layer.
 *
 * Selects rows from KeyboardLayouts based on the active layer:
 * - LETTERS: layout-specific (AZERTY or QWERTY)
 * - NUMBERS: shared number/punctuation rows
 * - SYMBOLS: shared symbol rows
 *
 * On the LETTERS layer, character keys are driven by a parent-level pointer handler
 * so swipe typing can cross key boundaries without lifting the finger.
 */
@Composable
fun KeyboardView(
    layer: KeyboardLayer,
    isShifted: Boolean,
    isCapsLock: Boolean = false,
    layout: String,
    onKeyPress: (KeyDefinition) -> Unit,
    onAccentSelected: (String) -> Unit,
    onSwipeWord: (String) -> Unit = {},
    onDeleteBackwardWord: () -> Unit = {},
    onSpaceCursorDrag: (Int) -> Unit = {},
    suggestionEngine: SuggestionEngine? = null,
    hapticsEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val rows = when (layer) {
        KeyboardLayer.LETTERS -> KeyboardLayouts.lettersForLayout(layout)
        KeyboardLayer.NUMBERS -> KeyboardLayouts.numbersRows
        KeyboardLayer.SYMBOLS -> KeyboardLayouts.symbolsRows
    }

    val swipeEnabled = layer == KeyboardLayer.LETTERS
    val density = LocalDensity.current
    val view = LocalView.current
    val swipeSlopPx = with(density) { 12.dp.toPx() }
    val accentCellWidthPx = with(density) { ACCENT_CELL_WIDTH_DP.toPx() }
    val swipeController = remember(swipeSlopPx, accentCellWidthPx) {
        SwipeTypingController(swipeSlopPx, accentCellWidthPx)
    }
    swipeController.isShifted = isShifted

    var columnCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    swipeController.parentWidthPx =
        columnCoordinates?.size?.width?.toFloat() ?: view.rootView.width.toFloat()

    val keyBoundsMap = remember { mutableStateMapOf<KeyDefinition, Rect>() }
    var gestureUi by remember { mutableStateOf(GestureUiState.Empty) }

    fun syncGestureUi() {
        gestureUiStateIfChanged(gestureUi, swipeController.snapshotGestureUi())?.let { gestureUi = it }
    }

    fun refreshKeyBounds() {
        swipeController.updateKeyBounds(
            keyBoundsMap.map { (key, bounds) -> CharacterKeyBounds(key, bounds) },
        )
    }

    val currentOnKeyPress = rememberUpdatedState(onKeyPress)
    val currentOnAccentSelected = rememberUpdatedState(onAccentSelected)
    val currentOnSwipeWord = rememberUpdatedState(onSwipeWord)
    val currentSuggestionEngine = rememberUpdatedState(suggestionEngine)

    fun resolveSwipeWord(rawPath: String): String {
        if (rawPath.length < 2) return rawPath
        val engine = currentSuggestionEngine.value ?: return rawPath
        val suggestions = engine.getSuggestions(rawPath, maxResults = 1)
        return suggestions.firstOrNull() ?: rawPath
    }

    fun commitSwipeResult(result: SwipeTypingResult) {
        when (result) {
            is SwipeTypingResult.Tap -> currentOnKeyPress.value(result.key)
            is SwipeTypingResult.Accent -> currentOnAccentSelected.value(result.character)
            is SwipeTypingResult.SwipeWord -> {
                val word = resolveSwipeWord(result.word)
                currentOnSwipeWord.value("$word ")
            }
            SwipeTypingResult.None -> Unit
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 2.dp, vertical = 3.dp)
            .onGloballyPositioned { columnCoordinates = it }
            .then(
                if (swipeEnabled) {
                    Modifier.pointerInput(swipeSlopPx) {
                        coroutineScope {
                            while (isActive) {
                                awaitPointerEventScope {
                                    val downEvent = awaitPointerEvent(PointerEventPass.Initial)
                                    if (downEvent.type != PointerEventType.Press) return@awaitPointerEventScope

                                    val downChange = downEvent.changes.firstOrNull { it.pressed } ?: return@awaitPointerEventScope
                                    val downPosition = downChange.position
                                    val hitKey = swipeController.hitTest(downPosition) ?: return@awaitPointerEventScope
                                    if (hitKey.type != KeyType.CHARACTER) return@awaitPointerEventScope

                                    val displayChar = SwipeTypingController.letterChar(hitKey, isShifted)
                                    val accentChars = hitKey.accents
                                        ?: displayChar?.let(AccentMap::accentsFor)

                                    swipeController.onPointerDown(downPosition, hitKey, accentChars)
                                    syncGestureUi()
                                    if (hapticsEnabled) HapticHelper.performKeyHaptic(view)

                                    val longPressJob = launch {
                                        delay(400L)
                                        swipeController.onLongPressThreshold()
                                        syncGestureUi()
                                    }

                                    var released = false
                                    while (!released) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull() ?: continue
                                        swipeController.onPointerMove(change.position)
                                        syncGestureUi()

                                        if (swipeController.isSwipeActive || swipeController.shouldShowAccentPopup()) {
                                            change.consume()
                                        }

                                        if (event.type == PointerEventType.Release || !change.pressed) {
                                            released = true
                                        }
                                    }

                                    longPressJob.cancel()
                                    val result = swipeController.onPointerUp()
                                    syncGestureUi()
                                    if (result !is SwipeTypingResult.None) {
                                        downChange.consume()
                                    }
                                    commitSwipeResult(result)
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        rows.forEach { rowKeys ->
            KeyRow(
                keys = rowKeys,
                isShifted = isShifted,
                isCapsLock = isCapsLock,
                onKeyPress = onKeyPress,
                onAccentSelected = onAccentSelected,
                onDeleteBackwardWord = onDeleteBackwardWord,
                onSpaceCursorDrag = onSpaceCursorDrag,
                hapticsEnabled = hapticsEnabled,
                externalCharacterGestures = swipeEnabled,
                gestureUi = if (swipeEnabled) gestureUi else null,
                columnCoordinates = columnCoordinates,
                onCharacterBoundsChanged = { key, bounds ->
                    keyBoundsMap[key] = bounds
                    refreshKeyBounds()
                },
            )
        }
    }
}

/**
 * Converts a key's window bounds into the keyboard column's local coordinate space.
 */
internal fun boundsInColumn(
    keyCoordinates: LayoutCoordinates,
    columnCoordinates: LayoutCoordinates?,
): Rect? {
    val column = columnCoordinates ?: return null
    val topLeft = column.localPositionOf(keyCoordinates, Offset.Zero)
    val bottomRight = column.localPositionOf(
        keyCoordinates,
        Offset(keyCoordinates.size.width.toFloat(), keyCoordinates.size.height.toFloat()),
    )
    return Rect(topLeft, bottomRight)
}
