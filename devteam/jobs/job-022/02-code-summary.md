# Job-022 code summary — Plan 033

## What changed

Removed `runBlocking` mic permission from the SmartVoiceButton gesture path
(async second-press pattern) and pinned InputConnection through Whisper polish
so hide-keyboard mid-polish does not drop successful commits.

## Files changed

| File | Why |
|------|-----|
| `ime/.../panel/SmartVoiceButton.kt` | Async `PermissionRequester.request` on Compose scope; no pointer-thread block |
| `ime/.../asr/ImeTextCommitter.kt` | `InputConnectionSupplier` pin/defer-clear; inactive IC safe drop |
| `ime/.../asr/PolishingTranscriber.kt` | `beginPolishCommit` / `endPolishCommit` around polish |
| `ime/.../DictusImeService.kt` | `clearSupplierIfIdle()` in `onFinishInputView` |
| `ime/.../di/AsrModule.kt` | Committer uses `connection()` |
| `ime/src/test/.../InputConnectionSupplierTest.kt` | Pin + deferred clear |
| `ime/src/test/.../PolishingTranscriberTest.kt` | Supplier nulled mid-polish still commits |
| `ime/src/test/.../StreamingTranscriberTest.kt` | `RecordingImeTextCommitter` accepts IC lambda |
| `docs/dictus-inventory.md` | Plan 033 inventory section |
| `plans/README.md` | Plan 033 marked DONE |

## Verification

- `rg runBlocking SmartVoiceButton.kt` — no matches
- `./gradlew :ime:testDebugUnitTest --tests '*Polishing*' --tests '*VoiceSession*' --tests '*Streaming*' --tests '*InputConnection*'` — BUILD SUCCESSFUL
- `bash scripts/verify.sh` — OK

## Drift check note

Plan drift anchor `32b0d20` is not in repo history. Phase 9 (`voiceStopScope` /
`stoppingJob`) present on main; proceeded without STOP.

## Out of scope (unchanged)

Permission receiver exported fix; `StreamingTranscriber.onSessionCancel` `runBlocking`.
