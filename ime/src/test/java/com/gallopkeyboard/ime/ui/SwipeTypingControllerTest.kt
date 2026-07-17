package com.gallopkeyboard.ime.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.gallopkeyboard.ime.model.KeyDefinition
import com.gallopkeyboard.ime.model.KeyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SwipeTypingControllerTest {

    private lateinit var controller: SwipeTypingController

    private val keyH = KeyDefinition(label = "H", output = "h", type = KeyType.CHARACTER)
    private val keyE = KeyDefinition(label = "E", output = "e", type = KeyType.CHARACTER)
    private val keyL = KeyDefinition(label = "L", output = "l", type = KeyType.CHARACTER)
    private val keyO = KeyDefinition(label = "O", output = "o", type = KeyType.CHARACTER)

    @Before
    fun setUp() {
        controller = SwipeTypingController(swipeSlopPx = 10f)
        controller.updateKeyBounds(
            listOf(
                CharacterKeyBounds(keyH, Rect(0f, 0f, 40f, 40f)),
                CharacterKeyBounds(keyE, Rect(50f, 0f, 90f, 40f)),
                CharacterKeyBounds(keyL, Rect(100f, 0f, 140f, 40f)),
                CharacterKeyBounds(keyO, Rect(150f, 0f, 190f, 40f)),
            ),
        )
    }

    @Test
    fun `short tap resolves to Tap`() {
        controller.onPointerDown(Offset(20f, 20f), keyH, accents = null)
        val result = controller.onPointerUp()
        assertEquals(SwipeTypingResult.Tap(keyH), result)
    }

    @Test
    fun `swipe across keys resolves to SwipeWord`() {
        controller.onPointerDown(Offset(20f, 20f), keyH, accents = null)
        controller.onPointerMove(Offset(70f, 20f))
        controller.onPointerMove(Offset(120f, 20f))
        controller.onPointerMove(Offset(120f, 20f))
        controller.onPointerMove(Offset(170f, 20f))
        val result = controller.onPointerUp()
        assertTrue(result is SwipeTypingResult.SwipeWord)
        assertEquals("helo", (result as SwipeTypingResult.SwipeWord).word)
    }

    @Test
    fun `long press with accent selection resolves to Accent`() {
        controller.onPointerDown(Offset(60f, 20f), keyE, accents = listOf("é", "è", "ê"))
        controller.onLongPressThreshold()
        controller.onPointerMove(Offset(75f, 20f))
        val result = controller.onPointerUp()
        assertEquals(SwipeTypingResult.Accent("è"), result)
    }
}
