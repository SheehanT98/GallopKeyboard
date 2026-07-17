package com.gallopkeyboard.ime.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePanelHeightTest {

    @Test
    fun `voice panel is much shorter than typing keyboard`() {
        assertTrue(VOICE_PANEL_HEIGHT_DP < KEYBOARD_PANEL_HEIGHT_DP)
    }

    @Test
    fun `voice panel height is about 140dp`() {
        assertEquals(140, VOICE_PANEL_HEIGHT_DP.value.toInt())
    }

    @Test
    fun `typing keyboard height unchanged at 346dp`() {
        assertEquals(346, KEYBOARD_PANEL_HEIGHT_DP.value.toInt())
    }
}
