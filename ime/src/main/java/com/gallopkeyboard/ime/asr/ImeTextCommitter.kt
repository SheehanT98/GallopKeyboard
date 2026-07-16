package com.gallopkeyboard.ime.asr

import android.view.inputmethod.InputConnection
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the current [InputConnection] for the active editor session.
 * [DictusImeService] updates [supplier] when the input view starts/finishes.
 */
@Singleton
class InputConnectionSupplier @Inject constructor() {
    var supplier: () -> InputConnection? = { null }
}

/**
 * Single write path for composing / committed text into the host field.
 */
open class ImeTextCommitter(
    private val ic: () -> InputConnection?,
) {
    open fun setComposing(text: String) {
        ic()?.setComposingText(text, 1)
    }

    open fun commitFinal(text: String) {
        ic()?.commitText(text, 1)
    }

    open fun clearComposing() {
        ic()?.finishComposingText()
    }
}
