package com.gallopkeyboard.ime.asr

import android.view.inputmethod.InputConnection
import org.junit.Assert.assertEquals
import org.junit.Test

class ImeTextCommitterTest {

    @Test
    fun `null connection is safe for all operations`() {
        val committer = ImeTextCommitter { null }
        committer.setComposing("hello")
        committer.commitFinal("hello")
        committer.clearComposing()
    }

    @Test
    fun `setComposing forwards to InputConnection`() {
        val fake = FakeInputConnection()
        val committer = ImeTextCommitter { fake }
        committer.setComposing("partial")
        assertEquals("partial", fake.composingText)
    }

    @Test
    fun `clearComposing finishes composing region`() {
        val fake = FakeInputConnection()
        val committer = ImeTextCommitter { fake }
        committer.clearComposing()
        assertEquals(1, fake.finishComposingCount)
    }
}

private class FakeInputConnection : InputConnection by NoOpInputConnection() {
    var composingText: String? = null
    var finishComposingCount = 0

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
        composingText = text?.toString()
        return true
    }

    override fun finishComposingText(): Boolean {
        finishComposingCount++
        return true
    }
}

/** Delegates unimplemented [InputConnection] methods — only overrides under test matter. */
private open class NoOpInputConnection : InputConnection {
    override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? = null
    override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? = null
    override fun getSelectedText(flags: Int): CharSequence? = null
    override fun getCursorCapsMode(reqModes: Int): Int = 0
    override fun getExtractedText(request: android.view.inputmethod.ExtractedTextRequest?, flags: Int) = null
    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean = false
    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean = false
    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean = false
    override fun setComposingRegion(start: Int, end: Int): Boolean = false
    override fun finishComposingText(): Boolean = false
    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean = false
    override fun commitCompletion(text: android.view.inputmethod.CompletionInfo?): Boolean = false
    override fun commitCorrection(correctionInfo: android.view.inputmethod.CorrectionInfo?): Boolean = false
    override fun setSelection(start: Int, end: Int): Boolean = false
    override fun performEditorAction(editorAction: Int): Boolean = false
    override fun performContextMenuAction(id: Int): Boolean = false
    override fun beginBatchEdit(): Boolean = false
    override fun endBatchEdit(): Boolean = false
    override fun sendKeyEvent(event: android.view.KeyEvent?): Boolean = false
    override fun clearMetaKeyStates(states: Int): Boolean = false
    override fun reportFullscreenMode(enabled: Boolean): Boolean = false
    override fun performPrivateCommand(action: String?, data: android.os.Bundle?): Boolean = false
    override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = false
    override fun requestCursorUpdates(cursorUpdateMode: Int, sequenceNumber: Int): Boolean = false
    override fun closeConnection() = Unit
    override fun getHandler(): android.os.Handler? = null
    override fun commitContent(
        inputContentInfo: android.view.inputmethod.InputContentInfo,
        flags: Int,
        opts: android.os.Bundle?,
    ): Boolean = false
    override fun performSpellCheck(): Boolean = false
    override fun setImeConsumesInput(imeConsumesInput: Boolean): Boolean = false
}
