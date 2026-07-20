package com.gallopkeyboard.ime.asr

import android.util.Log
import android.view.inputmethod.InputConnection
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the current [InputConnection] for the active editor session.
 * [DictusImeService] updates [supplier] when the input view starts/finishes.
 *
 * During Whisper polish (Plan 033), [beginPolishCommit] pins the connection
 * captured at stop time so [onFinishInputView] can clear the live supplier
 * without dropping the finalize commit.
 */
@Singleton
class InputConnectionSupplier @Inject constructor() {
    var supplier: () -> InputConnection? = { null }

    @Volatile
    private var pinnedConnection: InputConnection? = null

    @Volatile
    private var polishInFlight: Boolean = false

    @Volatile
    private var deferSupplierClear: Boolean = false

    /** Live supplier, or the IC pinned for an in-flight polish commit. */
    fun connection(): InputConnection? = pinnedConnection ?: supplier()

    /** Capture the current IC before polish; pair with [endPolishCommit]. */
    fun beginPolishCommit() {
        polishInFlight = true
        pinnedConnection = supplier()
    }

    fun endPolishCommit() {
        polishInFlight = false
        pinnedConnection = null
        if (deferSupplierClear) {
            deferSupplierClear = false
            supplier = { null }
        }
    }

    /**
     * Clear the live supplier. When polish is in flight, defer until
     * [endPolishCommit] so the pinned IC remains the commit target.
     */
    fun clearSupplierIfIdle() {
        if (polishInFlight) {
            deferSupplierClear = true
        } else {
            supplier = { null }
        }
    }

    fun isPolishInFlight(): Boolean = polishInFlight
}

/**
 * Single write path for composing / committed text into the host field.
 */
open class ImeTextCommitter(
    private val ic: () -> InputConnection?,
) {
    companion object {
        private const val TAG = "ImeTextCommitter"
    }

    open fun setComposing(text: String) {
        val connection = ic() ?: return
        if (!connection.setComposingText(text, 1)) {
            Log.w(TAG, "setComposing failed — connection inactive")
        }
    }

    open fun commitFinal(text: String) {
        val connection = ic() ?: return
        if (!connection.commitText(text, 1)) {
            Log.w(TAG, "commitFinal failed — connection inactive")
        }
    }

    /** Replace composing text and commit in one atomic IME operation. */
    open fun commitText(text: String) {
        val connection = ic() ?: return
        if (!connection.setComposingText(text, 1)) {
            Log.w(TAG, "commitText setComposing failed — connection inactive")
            return
        }
        if (!connection.finishComposingText()) {
            Log.w(TAG, "commitText finishComposing failed — connection inactive")
        }
    }

    open fun clearComposing() {
        val connection = ic() ?: return
        if (!connection.finishComposingText()) {
            Log.w(TAG, "clearComposing failed — connection inactive")
        }
    }
}
