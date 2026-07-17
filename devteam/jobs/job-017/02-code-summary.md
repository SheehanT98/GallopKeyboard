# Code summary — job-017 (Plan 014)

## Dispatcher choice

Added **`AsrCoroutineDispatcher`** (`"AsrEngine"` single-thread executor) instead of reusing
`RecorderCoroutineDispatcher` (`"AudioRecorder"`). AudioRecord I/O already owns the recorder
dispatcher via `flowOn`; ONNX decode on a separate thread avoids starving mic reads.

## Changes

| File | Why |
|------|-----|
| `ime/.../audio/AsrCoroutineDispatcher.kt` | New singleton ASR single-thread dispatcher |
| `ime/.../asr/StreamingTranscriber.kt` | `onSessionStart` / `onAudioFrame` enqueue on `AsrCoroutineDispatcher` via internal `CoroutineScope` (non-blocking); composing posts to Main via `Handler`; `onSessionStop`/`onSessionCancel` use `AsrCoroutineDispatcher` |
| `ime/.../asr/StreamingTranscriberTest.kt` | `drainAsrAndMain()` for async tests; thread-affinity + rapid-frames tests |
| `ime/.../asr/PolishingTranscriberTest.kt` | Constructor/drain adjacency for `AsrCoroutineDispatcher` (compile + async frame test) |
| `docs/dictus-inventory.md` | Plan 014 additions section |
| `plans/README.md` | Plan 014 status → DONE |

## Verification

- Drift check (`3571aab..HEAD` on in-scope files): empty
- `./gradlew :ime:testDebugUnitTest --tests 'com.gallopkeyboard.ime.asr.*'`: BUILD SUCCESSFUL
- `bash scripts/verify.sh`: OK

## Out of scope (unchanged)

- `SmartVoiceButton.kt`, `ParakeetEngine.kt`, `ImeTextCommitter.kt` (main posting done in transcriber)
