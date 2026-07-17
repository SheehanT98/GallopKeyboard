package com.gallopkeyboard.ime.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.gallopkeyboard.ime.model.KeyDefinition
import com.gallopkeyboard.ime.model.KeyType

/**
 * Hit-testable bounds for a single character key, stored in the keyboard column's
 * local coordinate space.
 */
data class CharacterKeyBounds(
    val key: KeyDefinition,
    val bounds: Rect,
)

/**
 * Tracks an in-progress swipe over letter keys on the LETTERS layer.
 *
 * Gesture resolution order on finger up:
 * 1. Accent selected during long-press popup
 * 2. Swipe word when movement exceeded slop
 * 3. Single-key tap otherwise
 */
class SwipeTypingController(
    private val swipeSlopPx: Float,
) {
    private val pathKeys = mutableListOf<KeyDefinition>()
    private var downPosition: Offset = Offset.Zero
    private var downKey: KeyDefinition? = null
    private var swipeActive = false
    private var accentPopupVisible = false
    private var accentTrackingActive = false
    private var highlightedAccentIndex: Int? = null

    var keyBounds: List<CharacterKeyBounds> = emptyList()
        private set

    var isShifted: Boolean = false

    var accentChars: List<String> = emptyList()
        private set

    val isSwipeActive: Boolean
        get() = swipeActive

    fun updateKeyBounds(bounds: List<CharacterKeyBounds>) {
        keyBounds = bounds
    }

    fun reset() {
        pathKeys.clear()
        downKey = null
        swipeActive = false
        accentPopupVisible = false
        accentTrackingActive = false
        highlightedAccentIndex = null
        accentChars = emptyList()
    }

    fun onPointerDown(position: Offset, hitKey: KeyDefinition, accents: List<String>?) {
        reset()
        downPosition = position
        downKey = hitKey
        accentChars = accents.orEmpty()
        appendKey(hitKey)
    }

    fun onPointerMove(position: Offset) {
        if (!swipeActive && !accentPopupVisible) {
            val distance = (position - downPosition).getDistance()
            if (distance >= swipeSlopPx) {
                swipeActive = true
                accentTrackingActive = false
                highlightedAccentIndex = null
            }
        }

        if (swipeActive) {
            hitTest(position)?.let(::appendKey)
            return
        }

        if (accentPopupVisible) {
            if (!accentTrackingActive) {
                val distance = (position - downPosition).getDistance()
                accentTrackingActive = distance >= swipeSlopPx
            }
            highlightedAccentIndex = if (accentTrackingActive) {
                resolveAccentIndex(position.x, downKey)
            } else {
                null
            }
        }
    }

    fun onLongPressThreshold() {
        if (!swipeActive && downKey != null && accentChars.isNotEmpty()) {
            accentPopupVisible = true
        }
    }

    fun onPointerUp(): SwipeTypingResult {
        val selectedAccent = highlightedAccentIndex?.let { index -> accentChars.getOrNull(index) }
        return when {
            selectedAccent != null -> SwipeTypingResult.Accent(selectedAccent)
            swipeActive -> {
                val letters = pathKeys.mapNotNull { letterChar(it, isShifted) }
                val word = SwipePathHelper.pathToWord(letters)
                if (word.isNotEmpty()) SwipeTypingResult.SwipeWord(word) else SwipeTypingResult.None
            }
            downKey != null -> SwipeTypingResult.Tap(downKey!!)
            else -> SwipeTypingResult.None
        }.also { reset() }
    }

    fun hitTest(position: Offset): KeyDefinition? =
        keyBounds.firstOrNull { it.bounds.contains(position) }?.key

    fun isKeyHighlighted(key: KeyDefinition): Boolean =
        swipeActive && pathKeys.contains(key)

    fun shouldShowAccentPopup(): Boolean = accentPopupVisible

    fun highlightedAccentIndex(): Int? = highlightedAccentIndex

    fun isPressedKey(key: KeyDefinition): Boolean =
        !swipeActive && downKey == key && !accentPopupVisible

    fun accentPopupKey(): KeyDefinition? = if (accentPopupVisible) downKey else null

    private fun appendKey(key: KeyDefinition) {
        if (key.type != KeyType.CHARACTER) return
        if (pathKeys.isEmpty() || pathKeys.last() != key) {
            pathKeys.add(key)
        }
    }

    private fun resolveAccentIndex(pointerX: Float, key: KeyDefinition?): Int? {
        val accents = accentChars
        val bounds = keyBounds.firstOrNull { it.key == key }?.bounds ?: return null
        if (accents.isEmpty()) return null

        val accentCellWidthPx = bounds.width / accents.size.coerceAtLeast(1)
        val index = ((pointerX - bounds.left) / accentCellWidthPx).toInt()
        return index.takeIf { it in accents.indices }
    }

    companion object {
        fun letterChar(key: KeyDefinition, isShifted: Boolean): Char? {
            if (key.type != KeyType.CHARACTER) return null
            val base = key.output.firstOrNull() ?: return null
            return if (isShifted) base.uppercaseChar() else base.lowercaseChar()
        }
    }
}

sealed class SwipeTypingResult {
    data object None : SwipeTypingResult()
    data class Tap(val key: KeyDefinition) : SwipeTypingResult()
    data class SwipeWord(val word: String) : SwipeTypingResult()
    data class Accent(val character: String) : SwipeTypingResult()
}
