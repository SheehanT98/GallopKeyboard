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

## Voice model check on panel open

Opening the voice panel runs a **lightweight** presence check (file exists +
expected size). It does **not** re-hash ~220 MB every time. Full SHA-256
verification runs at most once per day on IME start (`verifyInstalledIfDue`)
and in the Voice models settings screen.


Release and debug APKs ship **arm64-v8a only** to keep the install size smaller.
32-bit-only devices are not supported in v1. Galaxy S22 and other modern phones
are arm64.

## Space-bar cursor drag (`setSelection`)

Horizontal drag on the space bar moves the cursor via
`InputConnection.setSelection`. Native editors (Gmail compose, Samsung Notes,
most `EditText`s) honor this. Some WebViews and browser URL/omnibox fields
ignore or partially apply `setSelection`; the feature remains enabled for native
editors — we do not synthesize key-event cursor hacks as a workaround.
