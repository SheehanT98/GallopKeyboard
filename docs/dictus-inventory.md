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
| `core/src/main/java/com/gallopkeyboard/core/flags/Flags.kt` | `polishEnabled` flag (default `false`) |
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

