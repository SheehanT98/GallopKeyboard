package com.gallopkeyboard.ime.clipboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import timber.log.Timber

/**
 * Observes system clipboard changes and pushes text clips into [store].
 *
 * Registers [ClipboardManager.OnPrimaryClipChangedListener] for the IME lifecycle.
 * On Android 12+, that listener is unreliable in the IME process — call
 * [refreshFromPrimaryClip] whenever the keyboard window is shown (see
 * `docs/limitations.md`).
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

    /**
     * Re-read the current primary clip.
     *
     * Call from [android.inputmethodservice.InputMethodService.onStartInputView]
     * and [android.inputmethodservice.InputMethodService.onWindowShown] so the
     * strip updates when the keyboard opens even if the change listener missed
     * the copy.
     */
    fun refreshFromPrimaryClip() {
        readPrimaryClip()
    }

    private fun readPrimaryClip() {
        val clip = clipboardManager.primaryClip ?: return
        val description = clipboardManager.primaryClipDescription ?: return
        if (!descriptionHasReadableText(description)) return

        val item = clip.getItemAt(0) ?: return
        val text = item.coerceToText(context)?.toString()?.trim() ?: return
        if (text.isEmpty()) return
        store.add(text)
        Timber.d("Clipboard captured (%d chars)", text.length)
    }

    private fun descriptionHasReadableText(description: ClipDescription): Boolean {
        if (description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) return true
        if (description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)) return true
        if (description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)) return true
        // Some apps label clips as text/* without the plain subtype.
        for (i in 0 until description.mimeTypeCount) {
            val mime = description.getMimeType(i)
            if (mime.startsWith("text/")) return true
        }
        return false
    }
}
