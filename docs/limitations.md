# Known limitations

## Clipboard in the IME

Android 12+ hardened clipboard access for background processes. The IME may not
receive `ClipboardManager.OnPrimaryClipChangedListener` callbacks reliably.

**Mitigation:** `ClipboardWatcher.refreshFromPrimaryClip()` runs on each
`onStartInputView` and `onWindowShown`, and accepts plain/HTML/URI text clips.
History still only grows from clips observed while the keyboard is opening or
active — not a full system clipboard archive.

**UX cost:** Reading the primary clip on Android 13+ can trigger a system toast
("App pasted from your clipboard"). That is why we refresh on keyboard show
rather than polling. Long-term clipboard sync is out of scope per `CONTEXT.md`.

## 32-bit Android (armeabi-v7a)

Release and debug APKs ship **arm64-v8a only** to keep the install size smaller.
32-bit-only devices are not supported in v1. Galaxy S22 and other modern phones
are arm64.
