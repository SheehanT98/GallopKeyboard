package com.gallopkeyboard.ime.ui

import com.gallopkeyboard.ime.model.KeyDefinition
import com.gallopkeyboard.ime.model.KeyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class GestureUiStateTest {

    private val keyA = KeyDefinition(label = "A", output = "a", type = KeyType.CHARACTER)
    private val keyB = KeyDefinition(label = "B", output = "b", type = KeyType.CHARACTER)

    @Test
    fun `gestureUiStateIfChanged returns null when equal`() {
        val state = GestureUiState(highlightedKeys = setOf(keyA), pressedKey = keyB)
        assertNull(gestureUiStateIfChanged(state, state))
    }

    @Test
    fun `gestureUiStateIfChanged returns next when highlight set changes`() {
        val current = GestureUiState(highlightedKeys = setOf(keyA))
        val next = GestureUiState(highlightedKeys = setOf(keyA, keyB))
        assertSame(next, gestureUiStateIfChanged(current, next))
    }

    @Test
    fun `gestureUiStateIfChanged returns next when accent index changes`() {
        val current = GestureUiState(accentPopupKey = keyA, highlightedAccentIndex = 0)
        val next = current.copy(highlightedAccentIndex = 1)
        assertEquals(next, gestureUiStateIfChanged(current, next))
    }

    @Test
    fun `snapshotGestureUi exposes pressed key before swipe slop`() {
        val controller = SwipeTypingController(swipeSlopPx = 10f, accentCellWidthPx = 44f)
        controller.onPointerDown(
            position = androidx.compose.ui.geometry.Offset(20f, 20f),
            hitKey = keyA,
            accents = null,
        )
        val snapshot = controller.snapshotGestureUi()
        assertEquals(emptySet<KeyDefinition>(), snapshot.highlightedKeys)
        assertEquals(keyA, snapshot.pressedKey)
    }

    @Test
    fun `snapshotGestureUi exposes highlights after swipe activates`() {
        val controller = SwipeTypingController(swipeSlopPx = 10f, accentCellWidthPx = 44f)
        controller.onPointerDown(
            position = androidx.compose.ui.geometry.Offset(20f, 20f),
            hitKey = keyA,
            accents = null,
        )
        controller.onPointerMove(androidx.compose.ui.geometry.Offset(50f, 20f))
        val snapshot = controller.snapshotGestureUi()
        assertEquals(setOf(keyA), snapshot.highlightedKeys)
        assertNull(snapshot.pressedKey)
    }
}
