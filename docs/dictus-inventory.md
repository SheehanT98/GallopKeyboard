# Dictus import inventory

Factual reference for Plans 004+ — updated when Kotlin modules change.

## Upstream commit

- **SHA:** `4f5d24821d0772be16f482455c00da77d9a5f594`
- **Branch:** `develop`
- **Source:** https://github.com/getdictus/dictus-android
- **Import date:** 2026-07-16

## Modules

Confirmed in `settings.gradle.kts`:

| Gradle module | Path | Role |
|---------------|------|------|
| `:app` | `app/` | Launcher app, onboarding, settings, `DictationService` |
| `:ime` | `ime/` | `InputMethodService`, Compose keyboard UI |
| `:core` | `core/` | Shared theme, preferences, `DictationController` interface |
| `:whisper` | `whisper/` | whisper.cpp JNI bridge (`WhisperLib`, `WhisperContext`) |
| `:asr` | `asr/` | sherpa-onnx JNI + `ParakeetProvider` |

## Whisper integration

| Item | Detail |
|------|--------|
| Module | `:whisper` |
| JNI entry | `whisper/src/main/jni/whisper/jni.c` → loads `libwhisper.so` (CMake via `third_party/whisper.cpp`) |
| Kotlin API | `WhisperLib` (JNI declarations), `WhisperContext` (lifecycle wrapper) |
| App wrapper | `app/.../service/WhisperProvider.kt` implements `SttProvider` |
| Model path | `ModelManager` resolves `context.filesDir/models/{fileName}` for GGML files |
| Submodule | `third_party/whisper.cpp` (ggml-org/whisper.cpp) |

## Sherpa-ONNX / Parakeet

**Present.** Upstream ships offline Parakeet via sherpa-onnx (not streaming in current code).

| Item | Detail |
|------|--------|
| Module | `:asr` |
| JNI libs | `asr/src/main/jniLibs/*/libonnxruntime.so`, `libsherpa-onnx-jni.so` |
| Kotlin bindings | `com.k2fsa.sherpa.onnx.*` (vendored in `asr/src/main/java/com/k2fsa/sherpa/onnx/`) |
| Provider | `asr/.../ParakeetProvider.kt` implements `SttProvider` via `OfflineRecognizer` |
| Models | Downloaded to `filesDir/models/{key}/` (ONNX + `tokens.txt`) per `ModelCatalog` |

Plan 006 (streaming pass) still needs live partial-transcript wiring; the engine dependency is already in-tree.

## IME entry point

| Item | Detail |
|------|--------|
| Class | `com.gallopkeyboard.ime.DictusImeService` |
| File | `ime/src/main/java/com/gallopkeyboard/ime/DictusImeService.kt` |
| Base class | `LifecycleInputMethodService` (Compose lifecycle for IME) |
| Manifest | `ime/src/main/AndroidManifest.xml` — `android:name="com.gallopkeyboard.ime.DictusImeService"`, `BIND_INPUT_METHOD` |
| IME metadata | `ime/src/main/res/xml/method.xml` |

## Model download / storage

| Item | Detail |
|------|--------|
| Downloader | `app/.../service/ModelDownloader.kt` (HuggingFace + sherpa-onnx release URLs) |
| Catalog | `app/.../model/ModelCatalog.kt`, `ModelManager.kt` |
| On-device dir | `context.filesDir/models/` (Whisper flat files; Parakeet per-key subdirs) |

## UI framework

- **Keyboard / IME screens:** Jetpack Compose (`ime/ui/*`, `DictusImeService` hosts Compose via `setContent`)
- **Launcher / settings:** Jetpack Compose (`app/` screens)
- **No XML `KeyboardView`** — layout data is Kotlin (`KeyboardLayouts.kt`, `KeyDefinition.kt`)

## Plan 004 additions

New panel scaffold for typing ↔ voice toggle (Plan 005 wires the smart button).

### Files added

| Path | Role |
|------|------|
| `ime/src/main/java/com/gallopkeyboard/ime/panel/PanelState.kt` | `TYPING` / `VOICE` enum |
| `ime/src/main/java/com/gallopkeyboard/ime/panel/PanelController.kt` | `StateFlow` panel state; `toggle()`, `showTyping()`, `showVoice()`, `reset()` |
| `ime/src/main/java/com/gallopkeyboard/ime/panel/VoicePanel.kt` | Compose placeholder voice panel (smart button + toolbar row) |
| `ime/src/main/java/com/gallopkeyboard/ime/panel/PanelHost.kt` | Root composable switching typing ↔ voice |
| `ime/src/test/java/com/gallopkeyboard/ime/panel/PanelControllerTest.kt` | Unit tests for controller transitions |

### IME service edits

| Class | Methods |
|-------|---------|
| `com.gallopkeyboard.ime.DictusImeService` | `KeyboardContent()` wraps content in `PanelHost`; `onStartInputView()` calls `panelController.reset()` when `!restarting`; `onFinishInputView()` calls `panelController.reset()` |

`PanelController` is a manual instance field on `DictusImeService` (not Hilt-injected).

### Typing panel edits

| Class | Change |
|-------|--------|
| `com.gallopkeyboard.ime.ui.KeyboardScreen` | Optional `onVoicePanelToggle` callback; bottom-right `IconButton` with `Icons.Filled.Mic` overlay (40×40 dp, 8 dp inset) calls `panelController.showVoice()` |

### `strings.xml` keys added (`ime/src/main/res/values/strings.xml`)

- `panel_toggle_voice` — "Switch to voice panel"
- `panel_toggle_typing` — "Switch to typing panel"
- `voice_panel_placeholder_button` — "Hold / Tap to speak"

### Note on upstream dictation UI

Dictus still ships `RecordingScreen` / `TranscribingScreen` driven by `DictationState` (top mic pill in `MicButtonRow`). The new `VoicePanel` is the HANDOFF scaffold for Plans 005–007; it is separate from the legacy recording overlay until Plan 005 replaces the placeholder button.

## Plan 005 additions

Smart voice button gesture FSM + 16 kHz mono PCM recorder for the voice panel (Plan 006 replaces [StubTranscriber]).

### Files added

| Path | Role |
|------|------|
| `ime/src/main/java/com/gallopkeyboard/ime/audio/RingByteBuffer.kt` | Thread-safe bounded PCM byte ring (5 min ceiling) |
| `ime/src/main/java/com/gallopkeyboard/ime/audio/AudioSession.kt` | Session timestamps + buffer reference |
| `ime/src/main/java/com/gallopkeyboard/ime/audio/AudioRecorderEngine.kt` | `AudioRecord` wrapper; cold `Flow<ShortArray>` at 16 kHz mono PCM16 |
| `ime/src/main/java/com/gallopkeyboard/ime/audio/RecorderCoroutineDispatcher.kt` | Single-thread dispatcher for the read loop |
| `ime/src/main/java/com/gallopkeyboard/ime/audio/Transcriber.kt` | ASR seam — Plan 006 implements |
| `ime/src/main/java/com/gallopkeyboard/ime/audio/StubTranscriber.kt` | Logs session duration; bound via Hilt |
| `ime/src/main/java/com/gallopkeyboard/ime/di/AudioModule.kt` | `@Binds Transcriber → StubTranscriber` |
| `ime/src/main/java/com/gallopkeyboard/ime/panel/GestureFsm.kt` | Pure Kotlin ADR-0003 gesture state machine |
| `ime/src/main/java/com/gallopkeyboard/ime/panel/SmartVoiceButton.kt` | Compose button + pointer gestures + recording wiring |
| `ime/src/main/java/com/gallopkeyboard/ime/panel/PermissionRequester.kt` | IME-scoped `RECORD_AUDIO` request helper |
| `ime/src/main/java/com/gallopkeyboard/ime/panel/PermissionProxyActivity.kt` | Translucent proxy activity for permission dialog |
| `ime/src/test/java/com/gallopkeyboard/ime/audio/RingByteBufferTest.kt` | Ring buffer unit tests |
| `ime/src/test/java/com/gallopkeyboard/ime/panel/GestureFsmTest.kt` | Gesture FSM unit tests (6 cases) |

### Manifest

- `RECORD_AUDIO` permission + `android.hardware.microphone` feature (`ime/src/main/AndroidManifest.xml`)
- `PermissionProxyActivity` registered (translucent, `noHistory`)

### IME wiring

| Class | Change |
|-------|--------|
| `DictusImeEntryPoint` | Exposes `audioRecorderEngine()`, `transcriber()`, `permissionRequester()` |
| `PanelHost` / `VoicePanel` | Replace placeholder button with `SmartVoiceButton` |
| `DictusImeService` | Passes audio deps into `PanelHost` |

### `strings.xml` keys added

- `voice_panel_recording` — "Recording…"
- `mic_permission_denied` — denied toast
- `mic_permission_rationale` — settings hint (reserved for Plan 009)

### Note on upstream `AudioCaptureManager`

The `app` module still has `AudioCaptureManager` (Float32 via foreground `DictationService`). Plan 005 adds a separate **PCM16** pipeline in `:ime` for the voice panel per HANDOFF; interfaces differ — not reused.

## Plan 006 additions

Streaming Parakeet partial transcripts via `InputConnection` composing text (Plan 007 adds Whisper polish).

### Files added

| Path | Role |
|------|------|
| `asr/src/main/java/com/k2fsa/sherpa/onnx/OnlineRecognizer.kt` | Vendored sherpa-onnx streaming JNI bindings |
| `asr/src/main/java/com/k2fsa/sherpa/onnx/OnlineStream.kt` | Streaming audio buffer for online recognizer |
| `asr/src/main/java/com/gallopkeyboard/asr/parakeet/StreamingAsrEngine.kt` | Minimal streaming ASR interface |
| `asr/src/main/java/com/gallopkeyboard/asr/parakeet/ParakeetConfig.kt` | Model paths + validation |
| `asr/src/main/java/com/gallopkeyboard/asr/parakeet/ParakeetEngine.kt` | `OnlineRecognizer` wrapper for transducer models |
| `asr/src/main/java/com/gallopkeyboard/asr/parakeet/AsrModelMissingException.kt` | Missing-model error type |
| `ime/src/main/java/com/gallopkeyboard/ime/asr/ImeTextCommitter.kt` | `setComposingText` / `finishComposingText` helper + `InputConnectionSupplier` |
| `ime/src/main/java/com/gallopkeyboard/ime/asr/StreamingTranscriber.kt` | `Transcriber` impl — partials every 500 ms |
| `ime/src/main/java/com/gallopkeyboard/ime/di/AsrModule.kt` | Hilt bindings for engine + committer |
| `core/src/main/java/com/gallopkeyboard/core/flags/Flags.kt` | `polishEnabled` flag (default `true` since Plan 007) |
| `docs/models.md` | On-device model directory layout |
| `asr/src/androidTest/.../ParakeetSmokeTest.kt` | Optional smoke test (`RUN_ASR_SMOKE=1`) |
| `ime/src/test/.../StreamingTranscriberTest.kt` | Unit tests with fake engine (8 cases) |
| `ime/src/test/.../ImeTextCommitterTest.kt` | Null-safe committer tests |

### Model layout (streaming)

Expected under `context.filesDir/models/parakeet/`:

- `encoder.onnx`, `decoder.onnx`, `joiner.onnx`, `tokens.txt`

See `docs/models.md` for sizes, download links, and `adb push` sideload steps.

### DI / IME wiring

| Class | Change |
|-------|--------|
| `AudioModule` | `@Binds Transcriber → StreamingTranscriber` (stub kept `@VisibleForTesting`) |
| `DictusImeService` | Sets `InputConnectionSupplier` in `onStartInputView` / clears in `onFinishInputView` |
| `DictusImeEntryPoint` | Exposes `inputConnectionSupplier()` |

### `strings.xml` keys added

- `asr_models_missing` — toast when Parakeet files absent
- `asr_recognition_failed` — toast on JNI / runtime ASR failure

### Note on Case A (sherpa-onnx present)

Plan 006 skipped the Case B submodule path. Streaming uses vendored `OnlineRecognizer` bindings alongside existing offline `OfflineRecognizer` JNI libs in `:asr`.

## Plan 007 additions

Whisper polish pass on stop — decorates Plan 006 streaming with full-buffer Whisper transcription.

### Files added

| Path | Role |
|------|------|
| `whisper/src/main/java/com/gallopkeyboard/whisper/WhisperConfig.kt` | Model path, language, thread count |
| `whisper/src/main/java/com/gallopkeyboard/whisper/PolishEngine.kt` | `AsrPolishEngine` wrapping `WhisperContext` |
| `ime/src/main/java/com/gallopkeyboard/ime/asr/PolishingTranscriber.kt` | Decorator over `StreamingTranscriber` |
| `ime/src/main/java/com/gallopkeyboard/ime/di/WhisperPolishModule.kt` | Hilt `AsrPolishEngine` provider |
| `ime/src/test/java/com/gallopkeyboard/ime/asr/PolishingTranscriberTest.kt` | Unit tests (6 cases) |

### Files edited

| Path | Change |
|------|--------|
| `ime/.../asr/ImeTextCommitter.kt` | `commitText()` — atomic composing replace + commit |
| `ime/.../audio/RingByteBuffer.kt` | `snapshotShorts()` for polish PCM buffer |
| `core/.../flags/Flags.kt` | `polishEnabled` default `true` |
| `ime/.../di/AudioModule.kt` | `@Binds Transcriber → PolishingTranscriber` |
| `docs/models.md` | Whisper polish model layout |

### Model layout (polish)

Expected under `context.filesDir/models/whisper/`:

- `base.en.gguf` (default) or `small.en.gguf` (opt-in)

See `docs/models.md` for sizes, HuggingFace source, and `adb push` sideload steps.

### DI / IME wiring

| Class | Change |
|-------|--------|
| `AudioModule` | `@Binds Transcriber → PolishingTranscriber` (wraps `StreamingTranscriber`) |
| `WhisperPolishModule` | Provides `AsrPolishEngine` / `PolishEngine` with `WhisperConfig.fromModelDir` |

### STOP-condition notes (maintenance)

- `WhisperContext.transcribeData` is **suspend** — `AsrPolishEngine.transcribe` is suspend; no blocking dispatcher wrap.
- `withTimeout` does not cancel JNI whisper work — native thread may finish after timeout returns (acceptable v1).
- `whisper_full_cancel` not exposed in `WhisperLib` — follow-up if polish overlap becomes an issue.

## Plan 008 additions

GallopKeyboard-specific model download for hybrid STT (`models/parakeet/` + `models/whisper/`). **Did not reuse** upstream `app/.../service/ModelDownloader.kt` — that downloader targets Dictus `ModelCatalog` keys (tar.bz2 archives, flat Whisper bins) without SHA-256 pinning or HTTP resume. New stack in `:core`:

| Path | Role |
|------|------|
| `core/.../models/ModelSpec.kt` | Single-file descriptor (url, sha256, relPath) |
| `core/.../models/ModelRegistry.kt` | Pinned Parakeet zipformer + Whisper base/small specs |
| `core/.../models/ModelDownloader.kt` | OkHttp downloader with `.part`, Range resume, SHA-256 verify |
| `core/.../models/ModelInstaller.kt` | Sequential bundle install, corrupt detection, daily verify |
| `core/.../models/VoiceSetupPrefs.kt` | Launcher routing flag + Whisper tier preference |
| `core/.../models/VoiceSetupIntents.kt` | IME → app deep-link component names |
| `app/.../onboarding/OnboardingActivity.kt` | First-launch download UX (launcher) |
| `app/.../settings/ModelsSettingsActivity.kt` | Post-setup models screen host |
| `app/.../settings/ModelsSettingsScreen.kt` | Re-download, tier switch, delete-all |
| `ime/.../panel/VoicePanelPromptBanner.kt` | In-keyboard “Set up voice models” banner |
| `ime/.../asr/VoiceModelPromptState.kt` | Banner visibility state |
| `core/.../models/ModelDownloaderTest.kt` | MockWebServer unit tests (6 cases) |

Upstream `ModelDownloader` / `ModelCatalog` remain for legacy Dictus dictation flows in the launcher app.

## Plan 009 additions

Keyboard polish: short clipboard strip, emoji reuse, DeepSeek-inspired voice panel.

### STOP-condition: emoji reuse

Dictus already ships `ime/ui/EmojiPickerScreen.kt` with `androidx.emoji2.emojipicker.EmojiPickerView`
(8-column grid, category tabs built into the widget). **Did not create** parallel `EmojiPanel.kt`
or `arrays.xml` per STOP condition — existing picker extended in place (emoji key on row 4 via
`KeyType.EMOJI` in `KeyboardLayouts.kt`).

### Files added

| Path | Role |
|------|------|
| `ime/.../clipboard/ClipboardStore.kt` | In-memory 3-item ring (plain text, dedup, 500-char cap) |
| `ime/.../clipboard/ClipboardWatcher.kt` | `OnPrimaryClipChangedListener` + `onStartInputView` fallback |
| `ime/.../panel/ClipboardStrip.kt` | Compose chip row above typing keys; tap insert, long-press clear |
| `ime/.../theme/GallopTheme.kt` | `GallopColors` + `GallopVoiceTheme` for voice panel |
| `ime/src/test/.../clipboard/ClipboardStoreTest.kt` | Unit tests (7 cases) |
| `docs/limitations.md` | Clipboard listener / Android 13 toast notes |

### Files edited

| Path | Change |
|------|--------|
| `ime/.../DictusImeService.kt` | Clipboard watcher lifecycle, `itemsFlow` → `KeyboardScreen` |
| `ime/.../ui/KeyboardScreen.kt` | `ClipboardStrip` above `KeyboardView` |
| `ime/.../panel/VoicePanel.kt` | `GallopVoiceTheme`, flat dark surface, placeholder toolbar |
| `ime/.../panel/SmartVoiceButton.kt` | 72.dp pill button, mic icon, 1 Hz pulse ring while recording |
| `ime/.../panel/PanelHost.kt` | Voice panel no longer passes `themeMode` (uses Gallop theme) |

### Clipboard behavior

- No persistence — ring resets when IME process dies.
- Fresh install: empty strip until user copies text (watcher-only history).
- Fallback read on keyboard show documented in `docs/limitations.md`.

## Plan 010 additions

Hardening: local crash logs, StrictMode (debug), model lifecycle unload, release APK.

### Files added

| Path | Role |
|------|------|
| `core/.../log/CrashHandler.kt` | Uncaught handler → `filesDir/crashes/*.txt` (max 20 files) |
| `ime/.../asr/ModelLifecycleManager.kt` | Unloads Parakeet + Whisper after 60 s voice-panel idle |
| `ime/.../asr/ModelLifecycleController.kt` | Lifecycle callback interface |
| `app/.../settings/CrashLogsScreen.kt` | List / copy / share / delete crash files |
| `app/proguard-rules.pro` | R8 keep rules for JNI, sherpa-onnx, whisper |
| `docs/release-signing.md` | Local keystore setup for sideload release APK |
| `docs/manual-test-matrix.md` | Cross-app smoke + battery profiling checklist |

### Files edited

| Path | Change |
|------|--------|
| `DictusApplication.kt` | `CrashHandler.install`, StrictMode in debug |
| `DictusImeService.kt` | `CrashHandler.install` |
| `PolishingTranscriber.kt` | Lifecycle hooks on session start/stop |
| `PanelHost.kt` | Voice panel shown/hidden → lifecycle timer |
| `PreferenceKeys.kt` | `MODELS_KEEP_LOADED` (default false) |
| `SettingsScreen.kt` / `SettingsViewModel.kt` | Toggle + Crash logs nav |
| `app/build.gradle.kts` | Release minify/shrink, `~/.gallopkeyboard/keystore.properties` |
| `scripts/verify.sh` | `System.out.println` guard + hot-path Log.d advisory |

### Model unload default

`ModelLifecycleManager.UNLOAD_DELAY_MS` = **60_000** (60 s). Toggle
`models_keep_loaded` in Settings skips unload entirely.

### Battery baseline

Recorded in `docs/manual-test-matrix.md` — requires unplugged S22 device run
(agent environment: pending owner measurement).

## Plan 015 additions

Defer daily model SHA-256 verify off IME `onCreate` critical path.

### Files edited

| Path | Change |
|------|--------|
| `ime/.../DictusImeService.kt` | `verifyInstalledIfDue()` on `bindingScope` + `Dispatchers.IO`; corrupt → banner on Main |
| `core/.../ModelInstaller.kt` | KDoc: daily verify is background-scheduled from IME startup |

### Files added

| Path | Role |
|------|------|
| `core/src/test/.../ModelInstallerTest.kt` | `verifyInstalledIfDue` skips when recently verified |

### Manual test

Cold-start IME on a device with models installed on a verify-due day — keyboard
chrome appears without multi-second freeze; logcat may show verify completing
afterward.

## Plan 014 additions

Streaming ASR frame work off the IME/Compose main thread (ADR-0002).

- **`AsrCoroutineDispatcher`** (`ime/.../audio/AsrCoroutineDispatcher.kt`) — single-thread
  `"AsrEngine"` executor, separate from `RecorderCoroutineDispatcher` (`"AudioRecorder"`)
  so AudioRecord I/O is not starved by ONNX decode.
- **`StreamingTranscriber`** — `onSessionStart` / `onAudioFrame` enqueue work on
  `AsrCoroutineDispatcher` via an internal `CoroutineScope` (non-blocking for callers);
  `acceptFrame` / `currentPartial` / `beginStream` / `cancel` / `finalize` never run on
  Main; composing updates post to Main via `Handler(Looper.getMainLooper())`.
- **Tests** — `StreamingTranscriberTest` thread-affinity case asserts `acceptFrame` runs
  on `"AsrEngine"`.

## Plan 016 additions

Cancel ASR on SmartVoice dispose; unify IME mic entry to hybrid voice panel.

### Product decision

Bottom-row `KeyType.MIC` opens the **voice panel** (`PanelController.showVoice`) —
same hybrid `SmartVoiceButton` / `Transcriber` path as the toolbar Voice control.
`DictationService` recording remains for companion-app `RecordingScreen` only.

### Files edited

| Path | Change |
|------|--------|
| `ime/.../panel/SmartVoiceButton.kt` | `DisposableEffect` calls `cancelActiveSession` before `fsm.reset()` |
| `ime/.../panel/VoiceSessionCleanup.kt` | `cancelActiveSession(transcriber, session)` helper |
| `ime/.../ui/KeyboardScreen.kt` | `KeyType.MIC` → `onVoicePanelToggle()` (not `DictationService`) |
| `ime/.../DictusImeService.kt` | KeyboardScreen no longer passes `onMicTap` / `handleMicTap` |

### Files added

| Path | Role |
|------|------|
| `ime/src/test/.../panel/VoiceSessionCleanupTest.kt` | Cancel helper + idempotency |

### Manual test

Start toolbar Voice recording → switch to typing panel or hide IME → mic indicator
off; no orphan partial commits in the editor.

## Plan 018 additions

Docs-only reconciliation — no Kotlin or Gradle changes.

### Files edited

| Path | Change |
|------|--------|
| `AGENTS.md` | Remove swipe ban; document Plan 013 in-scope swipe; CONTEXT supersession note; plans index pointer 001+ |
| `plans/README.md` | Intro prose for 001–010 / 011–013 / 014–018 waves; Plan 018 status DONE |

### Agent rules (authoritative over historical CONTEXT)

- Swipe typing on LETTERS is in scope (Plan 013); do not remove without ADR.
- Cloud-backed swipe decoders / network lexicon remain out of scope.
- `CONTEXT.md` swipe out-of-scope bullet is historical — see `AGENTS.md`.

## Plan 017 additions

Single accent commit channel and shared popup hit-test geometry for swipe typing.

### Product decision

Accent selection commits on pointer release only (KeyButton non-swipe path and
`SwipeTypingController` swipe path). `AccentPopup` cells are display-only — no
per-cell `clickable` commit.

### Files added

| Path | Role |
|------|------|
| `ime/.../ui/AccentPopupGeometry.kt` | Shared `44.dp` cell width, clamp shift, `resolveAccentIndex` |

### Files edited

| Path | Change |
|------|--------|
| `ime/.../ui/AccentPopup.kt` | Remove `clickable` / `onAccentSelected` — visual + highlight only |
| `ime/.../ui/KeyButton.kt` | Shared geometry; release-only accent commit |
| `ime/.../ui/SwipeTypingController.kt` | `44.dp` popup cells + parent-width clamp (not key-width fractions) |
| `ime/.../ui/KeyboardView.kt` | Pass `accentCellWidthPx` and column width into controller |
| `ime/src/test/.../ui/SwipeTypingControllerTest.kt` | Geometry + swipe-before-long-press regression |

### Constant

- `ACCENT_CELL_WIDTH_DP = 44.dp` — must stay in sync with `AccentPopup` cell `size`.

## Plan 024 additions

Keep voice stop/polish alive after leaving the voice panel; blank polish must not
wipe streaming partials; late ASR frames after stop/cancel must not toast failure.

### Product decision

`onSessionStop` (streaming finalize + Whisper polish) runs on process-lifetime
`voiceStopScope`, not Compose `rememberCoroutineScope`, so panel switch / IME hide
does not cancel mid-polish. Dispose mid-recording still cancels; dispose mid-stop
does not. Successful polish runs through `TextPostProcessor` (same as
`DictationService`); empty/blank polish finishes composing instead of
`commitText("")`.

### Files edited

| Path | Change |
|------|--------|
| `ime/.../panel/VoiceSessionCleanup.kt` | `voiceStopScope`, `shouldCancelRecordingOnDispose` |
| `ime/.../panel/SmartVoiceButton.kt` | `stoppingJob` on `sessionScope`; dispose keeps mid-stop |
| `ime/.../asr/PolishingTranscriber.kt` | `TextPostProcessor`; blank → `clearComposing` |
| `ime/.../asr/StreamingTranscriber.kt` | `sessionEpoch` late-frame guard |
| `ime/src/test/.../panel/VoiceSessionCleanupTest.kt` | Dispose cancel vs keep policy |
| `ime/src/test/.../asr/PolishingTranscriberTest.kt` | Empty polish + post-processor |
| `ime/src/test/.../asr/StreamingTranscriberTest.kt` | Late frame after stop → no toast |

### Manual test

Release mic → immediately tap keyboard icon → polish must still replace composing
text in Notes/WhatsApp. Empty Whisper result must leave streaming partial committed.

## Plan 027 additions

Move voice PCM collection off Compose Main; bound ASR frame work; gate idle pulse.

### Product / perf decision

- PCM collect/write runs on `RecorderCoroutineDispatcher` via `sessionScope.launch(recorderDispatcher)`
  (not Compose `rememberCoroutineScope`), preserving Plan 024 stop/polish on `voiceStopScope`.
- `StreamingTranscriber` uses a serial consumer + capacity-2 DROP_OLDEST frame queue instead of
  unbounded per-frame `launch`.
- `rememberInfiniteTransition` for the mic pulse runs only while `isRecordingVisual` (RecordingDot
  was already gated). Idle voice panel must not spin an infinite transition.
- Optional 60 s→5 min ring grow deferred — keep Plan 005 five-minute ceiling capacity.

### Files edited

| Path | Change |
|------|--------|
| `ime/.../panel/SmartVoiceButton.kt` | Collect on recorder dispatcher; `writeShorts` + scratch; gate pulse |
| `ime/.../asr/StreamingTranscriber.kt` | Bounded serial frame queue + single consumer; keep sessionEpoch |
| `ime/.../audio/RingByteBuffer.kt` | `writeShorts(samples, scratch?)` bulk helper |
| `ime/.../di/DictusImeEntryPoint.kt` | Expose `recorderCoroutineDispatcher()` |
| `ime/src/test/.../audio/RingByteBufferTest.kt` | writeShorts equivalence |
| `ime/src/test/.../asr/StreamingTranscriberTest.kt` | Slow-engine queue bound; interleaved drain for partials |

### Manual / visual smoke

Idle voice panel: no continuous pulse animation CPU. Start recording: pulse + RecordingDot
animate. Release: pulse stops. Partials still update under normal speech (queue capacity 2).

## Plan 025 additions

Code-point delete, space-bar cursor drag, and accelerated word delete on long-hold.

### Files added

| Path | Role |
|------|------|
| `ime/.../EditorEditHelpers.kt` | Pure helpers: `countCharsToDeleteForWord`, `deleteMode`, `cursorOffsetAfterDrag` |
| `ime/src/test/.../EditorEditHelpersTest.kt` | Unit tests (ASCII word delete, surrogate emoji UTF-16 lengths, delete-mode policy, cursor clamp) |

### Files edited

| Path | Change |
|------|--------|
| `ime/.../DictusImeService.kt` | `deleteBackward` prefers `deleteSurroundingTextInCodePoints`; adds `deleteBackwardWord`, `moveCursor` |
| `ime/.../ui/KeyboardScreen.kt` | Wires `onDeleteBackwardWord`, `onSpaceCursorDrag` |
| `ime/.../ui/KeyboardView.kt` / `KeyRow.kt` | Pass delete-word + space-drag callbacks to keys |
| `ime/.../ui/KeyButton.kt` | DELETE accelerates to word after ~8 repeats or 900 ms; SPACE horizontal drag moves cursor (KeyButton-local; not swipe layer) |

### Behavior notes

- Word-delete length uses Java `String.length` (UTF-16) to match `deleteSurroundingText`.
- Space drag does not commit spaces; tap / double-tap → `. ` still uses release-without-drag.
- SPACE stays outside Plan 013 character swipe hit-testing (`KeyType.CHARACTER` only).


