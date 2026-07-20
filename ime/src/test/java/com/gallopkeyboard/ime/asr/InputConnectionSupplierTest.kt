package com.gallopkeyboard.ime.asr

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class InputConnectionSupplierTest {

    @Test
    fun `beginPolishCommit pins IC surviving supplier clear`() {
        val supplier = InputConnectionSupplier()
        val pinned = StubInputConnection()
        supplier.supplier = { pinned }

        supplier.beginPolishCommit()
        supplier.supplier = { null }

        assertSame(pinned, supplier.connection())
        supplier.endPolishCommit()
        assertNull(supplier.connection())
    }

    @Test
    fun `clearSupplierIfIdle defers while polish in flight`() {
        val supplier = InputConnectionSupplier()
        val ic = StubInputConnection()
        supplier.supplier = { ic }

        supplier.beginPolishCommit()
        supplier.clearSupplierIfIdle()

        assertSame(ic, supplier.connection())
        supplier.endPolishCommit()
        assertNull(supplier.connection())
    }
}

private class StubInputConnection : android.view.inputmethod.InputConnection {
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
