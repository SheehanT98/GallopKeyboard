# Code summary — job-007 (Whisper polish pass)

## What landed

Hybrid STT polish half: `PolishingTranscriber` decorates `StreamingTranscriber` and runs a full-buffer Whisper pass on stop, replacing the streaming partial when polish succeeds within 2 s.

## STOP conditions

| Condition | Outcome |
|-----------|---------|
| Dictus whisper wrapper is suspend/async | **Hit** — `AsrPolishEngine.transcribe` is `suspend`; no blocking `withContext(dispatcher)` around polish |
| JNI cancellation unaware | **Noted** in `docs/dictus-inventory.md` Plan 007 maintenance notes |
| Other STOP conditions | Not hit |

## Files changed

| File | Why |
|------|-----|
| `whisper/.../WhisperConfig.kt` | Model path (`models/whisper/`), language, threads |
| `whisper/.../PolishEngine.kt` | `AsrPolishEngine` wrapping `WhisperContext` |
| `ime/.../PolishingTranscriber.kt` | Decorator — polish on stop, 2 s timeout, fallback |
| `ime/.../ImeTextCommitter.kt` | `commitText()` atomic replace + commit |
| `ime/.../RingByteBuffer.kt` | `snapshotShorts()` for PCM buffer |
| `ime/.../di/WhisperPolishModule.kt` | Hilt `AsrPolishEngine` provider |
| `ime/.../di/AudioModule.kt` | `Transcriber` → `PolishingTranscriber` |
| `ime/build.gradle.kts` | `:whisper` dependency |
| `core/.../Flags.kt` | `polishEnabled` default `true` |
| `ime/src/test/.../PolishingTranscriberTest.kt` | 6 unit cases |
| `ime/src/test/.../StreamingTranscriberTest.kt` | `CommitText` in test harness |
| `docs/models.md` | Whisper polish section |
| `docs/dictus-inventory.md` | Plan 007 additions |
| `plans/README.md` | Plan 007 → DONE |

## Deviations

- Plan showed `withContext(Dispatchers.Main)` for committer calls; implemented direct calls to match `StreamingTranscriber` (same recorder-thread path, avoids Robolectric Main deadlock in unit tests).

## Verification

```bash
source scripts/android-env.sh
bash scripts/verify.sh   # OK
./gradlew --no-daemon :ime:testDebugUnitTest --tests '*PolishingTranscriberTest'  # 6/6 pass
```

Manual on-device polish (models sideloaded) deferred to test stage.
