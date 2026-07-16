package com.gallopkeyboard.ime.clipboard

import android.content.ClipboardManager
import android.content.ClipDescription
import android.content.Context
import timber.log.Timber

/**
 * Observes system clipboard changes and pushes plain-text clips into [store].
 *
 * Registers [ClipboardManager.OnPrimaryClipChangedListener] for the IME lifecycle.
 * On Android 12+, the listener may not fire in the IME process — call
 * [refreshFromPrimaryClip] from [android.inputmethodservice.InputMethodService.onStartInputView]
 * as a fallback (see `docs/limitations.md`).
 */
class ClipboardWatcher(
    private val context: Context,
    private val store: ClipboardStore,
) {
    private val clipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        readPrimaryClip()
    }

    fun start() {
        clipboardManager.addPrimaryClipChangedListener(listener)
        Timber.d("ClipboardWatcher started")
    }

    fun stop() {
        clipboardManager.removePrimaryClipChangedListener(listener)
        Timber.d("ClipboardWatcher stopped")
    }

    /** Fallback when the listener does not fire (Android 12+ IME hardening). */
    fun refreshFromPrimaryClip() {
        readPrimaryClip()
    }

    private fun readPrimaryClip() {
        val clip = clipboardManager.primaryClip ?: return
        val description = clipboardManager.primaryClipDescription ?: return
        if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) return

        val item = clip.getItemAt(0) ?: return
        val text = item.coerceToText(context)?.toString() ?: return
        store.add(text)
        Timber.d("Clipboard captured (%d chars)", text.length)
    }
}
