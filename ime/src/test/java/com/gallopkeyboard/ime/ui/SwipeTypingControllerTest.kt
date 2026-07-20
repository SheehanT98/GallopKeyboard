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

    private val accentCellWidthPx = 44f
    private val parentWidthPx = 200f

    private val keyH = KeyDefinition(label = "H", output = "h", type = KeyType.CHARACTER)
    private val keyE = KeyDefinition(label = "E", output = "e", type = KeyType.CHARACTER)
    private val keyL = KeyDefinition(label = "L", output = "l", type = KeyType.CHARACTER)
    private val keyO = KeyDefinition(label = "O", output = "o", type = KeyType.CHARACTER)

    @Before
    fun setUp() {
        controller = SwipeTypingController(swipeSlopPx = 10f, accentCellWidthPx = accentCellWidthPx)
        controller.parentWidthPx = parentWidthPx
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
    fun `dwell on same key emits double letter`() {
        var now = 0L
        val dwellController = SwipeTypingController(
            swipeSlopPx = 10f,
            accentCellWidthPx = accentCellWidthPx,
            dwellMs = 300L,
            clock = { now },
        )
        dwellController.parentWidthPx = parentWidthPx
        dwellController.updateKeyBounds(
            listOf(
                CharacterKeyBounds(keyH, Rect(0f, 0f, 40f, 40f)),
                CharacterKeyBounds(keyE, Rect(50f, 0f, 90f, 40f)),
                CharacterKeyBounds(keyL, Rect(100f, 0f, 140f, 40f)),
                CharacterKeyBounds(keyO, Rect(150f, 0f, 190f, 40f)),
            ),
        )

        dwellController.onPointerDown(Offset(20f, 20f), keyH, accents = null)
        dwellController.onPointerMove(Offset(70f, 20f))
        now = 400L
        dwellController.onPointerMove(Offset(120f, 20f))
        now = 800L
        dwellController.onPointerMove(Offset(120f, 20f))
        dwellController.onPointerMove(Offset(170f, 20f))
        val result = dwellController.onPointerUp()
        assertTrue(result is SwipeTypingResult.SwipeWord)
        assertEquals("hello", (result as SwipeTypingResult.SwipeWord).word)
    }

    @Test
    fun `jitter below dwell does not emit double letter`() {
        var now = 0L
        val dwellController = SwipeTypingController(
            swipeSlopPx = 10f,
            accentCellWidthPx = accentCellWidthPx,
            dwellMs = 300L,
            clock = { now },
        )
        dwellController.parentWidthPx = parentWidthPx
        dwellController.updateKeyBounds(
            listOf(
                CharacterKeyBounds(keyH, Rect(0f, 0f, 40f, 40f)),
                CharacterKeyBounds(keyE, Rect(50f, 0f, 90f, 40f)),
                CharacterKeyBounds(keyL, Rect(100f, 0f, 140f, 40f)),
                CharacterKeyBounds(keyO, Rect(150f, 0f, 190f, 40f)),
            ),
        )

        dwellController.onPointerDown(Offset(20f, 20f), keyH, accents = null)
        dwellController.onPointerMove(Offset(70f, 20f))
        now = 100L
        dwellController.onPointerMove(Offset(120f, 20f))
        now = 150L
        dwellController.onPointerMove(Offset(120f, 20f))
        dwellController.onPointerMove(Offset(170f, 20f))
        val result = dwellController.onPointerUp()
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

    @Test
    fun `long press accent at rightmost 44dp cell selects last accent`() {
        controller.onPointerDown(Offset(60f, 20f), keyE, accents = listOf("é", "è", "ê"))
        controller.onLongPressThreshold()
        // Popup left for key E (50–90): natural 4px; cells at 4, 48, 92
        controller.onPointerMove(Offset(110f, 20f))
        val result = controller.onPointerUp()
        assertEquals(SwipeTypingResult.Accent("ê"), result)
    }

    @Test
    fun `movement beyond slop before long press yields swipe not accent`() {
        controller.onPointerDown(Offset(60f, 20f), keyE, accents = listOf("é", "è", "ê"))
        controller.onPointerMove(Offset(120f, 20f))
        controller.onLongPressThreshold()
        val result = controller.onPointerUp()
        assertTrue(result is SwipeTypingResult.SwipeWord)
    }

    @Test
    fun `resolveAccentIndex maps left middle and right cells`() {
        val accents = listOf("é", "è", "ê")
        val keyLeft = 50f
        val keyWidth = 40f
        val shift = computeAccentShiftPx(
            keyLeftPx = keyLeft,
            keyWidthPx = keyWidth,
            accentCount = accents.size,
            accentCellWidthPx = accentCellWidthPx,
            parentWidthPx = parentWidthPx,
        )
        val popupLeft = keyLeft + (keyWidth - accents.size * accentCellWidthPx) / 2f + shift

        assertEquals(
            0,
            resolveAccentIndex(
                pointerX = popupLeft + accentCellWidthPx / 2f,
                keyLeftPx = keyLeft,
                keyWidthPx = keyWidth,
                accentCount = accents.size,
                accentCellWidthPx = accentCellWidthPx,
                accentShiftPx = shift,
            ),
        )
        assertEquals(
            1,
            resolveAccentIndex(
                pointerX = popupLeft + accentCellWidthPx * 1.5f,
                keyLeftPx = keyLeft,
                keyWidthPx = keyWidth,
                accentCount = accents.size,
                accentCellWidthPx = accentCellWidthPx,
                accentShiftPx = shift,
            ),
        )
        assertEquals(
            2,
            resolveAccentIndex(
                pointerX = popupLeft + accentCellWidthPx * 2.5f,
                keyLeftPx = keyLeft,
                keyWidthPx = keyWidth,
                accentCount = accents.size,
                accentCellWidthPx = accentCellWidthPx,
                accentShiftPx = shift,
            ),
        )
    }
}
