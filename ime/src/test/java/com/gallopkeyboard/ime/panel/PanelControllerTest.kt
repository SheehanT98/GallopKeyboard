package com.gallopkeyboard.ime.panel

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PanelControllerTest {

  @Test
  fun `initial state is TYPING`() {
    val controller = PanelController()
    assertEquals(PanelState.TYPING, controller.state.value)
  }

  @Test
  fun `toggle flips TYPING to VOICE`() {
    val controller = PanelController()
    controller.toggle()
    assertEquals(PanelState.VOICE, controller.state.value)
  }

  @Test
  fun `toggle from VOICE returns to TYPING`() {
    val controller = PanelController()
    controller.showVoice()
    controller.toggle()
    assertEquals(PanelState.TYPING, controller.state.value)
  }

  @Test
  fun `showVoice sets state to VOICE`() {
    val controller = PanelController()
    controller.showVoice()
    assertEquals(PanelState.VOICE, controller.state.value)
  }

  @Test
  fun `showTyping sets state to TYPING`() {
    val controller = PanelController()
    controller.showVoice()
    controller.showTyping()
    assertEquals(PanelState.TYPING, controller.state.value)
  }

  @Test
  fun `reset returns to TYPING regardless of previous state`() {
    val controller = PanelController()
    controller.showVoice()
    controller.reset()
    assertEquals(PanelState.TYPING, controller.state.value)
  }

  @Test
  fun `showClipboard sets state to CLIPBOARD`() {
    val controller = PanelController()
    controller.showClipboard()
    assertEquals(PanelState.CLIPBOARD, controller.state.value)
  }

  @Test
  fun `toggle from CLIPBOARD returns to TYPING`() {
    val controller = PanelController()
    controller.showClipboard()
    controller.toggle()
    assertEquals(PanelState.TYPING, controller.state.value)
  }

  @Test
  fun `state is a hot StateFlow collectors receive current value on subscribe`() {
    val controller = PanelController()
    controller.showVoice()
    val collected = runBlocking { controller.state.first() }
    assertEquals(PanelState.VOICE, collected)
  }
}
