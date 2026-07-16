# Known limitations

## Clipboard in the IME (Plan 009)

Android 12+ hardened clipboard access for background processes. The IME may not
receive `ClipboardManager.OnPrimaryClipChangedListener` callbacks reliably.

**Mitigation:** `ClipboardWatcher.refreshFromPrimaryClip()` runs on each
`onStartInputView`, so the current primary clip is captured when the keyboard
opens. History still only grows from clips observed while the IME is active or
at keyboard show time — not a full system clipboard archive.

**UX cost:** Reading the primary clip on Android 13+ can trigger a system toast
("App pasted from your clipboard"). This is acceptable for v1 short clipboard;
long-term clipboard sync is out of scope per `CONTEXT.md`.
