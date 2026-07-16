package com.gallopkeyboard.ime.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GestureFsmTest {

    private val callbacks = mutableListOf<String>()
    private lateinit var fsm: GestureFsm

    @Before
    fun setUp() {
        callbacks.clear()
        fsm = GestureFsm(
            cancelSlopPx = 48f,
            onSessionStart = { callbacks += "start:${it.name}" },
            onSessionStop = { callbacks += "stop:${it.name}" },
            onSessionCancel = { callbacks += "cancel" },
        )
    }

    @Test
    fun `short tap then second tap stops with TAP reason`() {
        fsm.onEvent(GestureEvent.Down(0, 0f, 0f))
        fsm.onEvent(GestureEvent.Up(100, 0f, 0f))
        assertEquals(GestureState.TAP_TOGGLE_ON, fsm.state)
        assertEquals(listOf("start:TAP"), callbacks)

        fsm.onEvent(GestureEvent.Down(200, 0f, 0f))
        assertEquals(GestureState.IDLE, fsm.state)
        assertEquals(listOf("start:TAP", "stop:TAP"), callbacks)
    }

    @Test
    fun `tap toggle full cycle`() {
        fsm.onEvent(GestureEvent.Down(0, 0f, 0f))
        fsm.onEvent(GestureEvent.Up(100, 0f, 0f))
        fsm.onEvent(GestureEvent.Down(500, 0f, 0f))
        fsm.onEvent(GestureEvent.Up(550, 0f, 0f))
        assertEquals(listOf("start:TAP", "stop:TAP"), callbacks)
    }

    @Test
    fun `hold past threshold then release`() {
        fsm.onEvent(GestureEvent.Down(0, 0f, 0f))
        fsm.onEvent(GestureEvent.HoldThresholdElapsed(400))
        assertEquals(GestureState.HOLDING, fsm.state)
        fsm.onEvent(GestureEvent.Up(1000, 0f, 0f))
        assertEquals(
            listOf("start:HOLD", "stop:HOLD"),
            callbacks,
        )
    }

    @Test
    fun `cancel shortly after down`() {
        fsm.onEvent(GestureEvent.Down(0, 0f, 0f))
        fsm.onEvent(GestureEvent.Cancel(50))
        assertEquals(GestureState.IDLE, fsm.state)
        assertEquals(listOf("start:TAP", "cancel"), callbacks)
        assertTrue(callbacks.none { it.startsWith("stop") })
    }

    @Test
    fun `drag outside slop cancels`() {
        fsm.onEvent(GestureEvent.Down(0, 0f, 0f))
        fsm.onEvent(GestureEvent.Move(100f, 0f))
        assertEquals(GestureState.IDLE, fsm.state)
        assertEquals(listOf("start:TAP", "cancel"), callbacks)
    }

    @Test
    fun `drag within slop allows tap toggle`() {
        fsm.onEvent(GestureEvent.Down(0, 0f, 0f))
        fsm.onEvent(GestureEvent.Move(40f, 0f))
        fsm.onEvent(GestureEvent.Up(100, 0f, 0f))
        assertEquals(GestureState.TAP_TOGGLE_ON, fsm.state)
        assertEquals(listOf("start:TAP"), callbacks)
    }
}
