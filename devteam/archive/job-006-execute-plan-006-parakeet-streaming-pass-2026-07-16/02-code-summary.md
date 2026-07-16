# Code summary — job-006 (Plan 006: Parakeet streaming pass)

## What landed

Hybrid STT **streaming half**: while the user holds the smart button, partial transcripts appear in the host field as **composing text** via `InputConnection`. Case A applied (sherpa-onnx already in-tree); no submodule/native build added.

### ASR (`:asr`)

- Vendored **streaming** JNI bindings: `OnlineRecognizer.kt`, `OnlineStream.kt`
- `StreamingAsrEngine` interface + `ParakeetEngine` wrapping `OnlineRecognizer` transducer API
- `ParakeetConfig` / `AsrModelMissingException` — expects `filesDir/models/parakeet/{encoder,decoder,joiner}.onnx` + `tokens.txt`
- Optional `ParakeetSmokeTest` (gated `RUN_ASR_SMOKE=1`) + reference WAV asset

### IME (`:ime`)

- `ImeTextCommitter` + `InputConnectionSupplier` — null-safe composing text writes
- `StreamingTranscriber` implements `Transcriber` — 500 ms partial polling (every 5 frames), engine on recorder dispatcher, toasts on main thread
- Hilt: `AsrModule` provides engine/committer; `AudioModule` binds `StreamingTranscriber` (stub kept `@VisibleForTesting`)
- `DictusImeService` wires live `InputConnection` per input session

### Core

- `Flags.polishEnabled` default `false` — after stop, `clearComposing()` after 50 ms until Plan 007

### Docs

- `docs/models.md` — on-device Parakeet layout, sizes, sideload `adb push`
- `docs/dictus-inventory.md` — Plan 006 additions
- `plans/README.md` — Plan 006 → DONE

### Tests (11 new)

- `StreamingTranscriberTest` — 8 cases (fake engine)
- `ImeTextCommitterTest` — 3 cases

## Verification

```bash
source scripts/android-env.sh
bash scripts/verify.sh   # OK (assembleDebug + testAll + lint)
```

APK includes existing `arm64-v8a/libsherpa-onnx-jni.so` from `:asr` jniLibs (Case A).

## STOP conditions

None hit.

## Files changed (high level)

| Area | Key paths |
|------|-----------|
| Bindings | `asr/.../OnlineRecognizer.kt`, `OnlineStream.kt` |
| Engine | `asr/.../parakeet/*` |
| IME | `ime/.../asr/*`, `ime/di/AsrModule.kt`, `AudioModule.kt`, `DictusImeService.kt` |
| Flags | `core/.../flags/Flags.kt` |
| Tests | `ime/src/test/.../StreamingTranscriberTest.kt`, `ImeTextCommitterTest.kt`, `asr/src/androidTest/...` |
| Docs | `docs/models.md`, `docs/dictus-inventory.md`, `plans/README.md` |

## Manual follow-up (not blocking)

- Sideload streaming Parakeet models per `docs/models.md` and speak in Notes to validate live partials
- Run `RUN_ASR_SMOKE=1 ./gradlew :asr:connectedDebugAndroidTest` on device with models
