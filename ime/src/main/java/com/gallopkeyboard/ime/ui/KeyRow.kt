package com.gallopkeyboard.ime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.gallopkeyboard.ime.model.AccentMap
import com.gallopkeyboard.ime.model.KeyDefinition
import com.gallopkeyboard.ime.model.KeyType

/**
 * Renders a single horizontal row of keyboard keys.
 *
 * Each key receives a weight proportional to its widthMultiplier so that
 * wider keys (shift, space, delete) take up more horizontal space.
 */
@Composable
fun KeyRow(
    keys: List<KeyDefinition>,
    isShifted: Boolean,
    isCapsLock: Boolean = false,
    onKeyPress: (KeyDefinition) -> Unit,
    onAccentSelected: (String) -> Unit,
    hapticsEnabled: Boolean = true,
    externalCharacterGestures: Boolean = false,
    swipeController: SwipeTypingController? = null,
    columnCoordinates: LayoutCoordinates? = null,
    onCharacterBoundsChanged: (KeyDefinition, Rect) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        keys.forEach { key ->
            val displayChar = when (key.type) {
                KeyType.CHARACTER -> {
                    val baseChar = key.output.firstOrNull()
                    if (baseChar != null) {
                        if (isShifted) baseChar.uppercaseChar() else baseChar.lowercaseChar()
                    } else {
                        null
                    }
                }
                else -> null
            }
            val accentChars = key.accents ?: displayChar?.let(AccentMap::accentsFor)
            val useExternalGestures = externalCharacterGestures && key.type == KeyType.CHARACTER

            KeyButton(
                key = key,
                isShifted = isShifted,
                isCapsLock = isCapsLock,
                onPress = { onKeyPress(key) },
                accentChars = accentChars,
                onAccentSelected = onAccentSelected,
                hapticsEnabled = hapticsEnabled,
                externalGesturesEnabled = useExternalGestures,
                isExternallyPressed = swipeController?.isPressedKey(key) == true,
                isSwipeHighlighted = swipeController?.isKeyHighlighted(key) == true,
                showAccentPopupOverride = swipeController?.accentPopupKey() == key,
                highlightedAccentIndexOverride = swipeController?.highlightedAccentIndex(),
                modifier = Modifier
                    .weight(key.widthMultiplier)
                    .then(
                        if (useExternalGestures) {
                            Modifier.onGloballyPositioned { coordinates ->
                                boundsInColumn(coordinates, columnCoordinates)?.let { bounds ->
                                    onCharacterBoundsChanged(key, bounds)
                                }
                            }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}
