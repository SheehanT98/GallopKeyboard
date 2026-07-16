# Job 006 — Review

| Field | Value |
|-------|-------|
| **Job** | job-006 |
| **Branch** | `cursor/devteam-job-006-execute-plan-006-parakeet-streaming-pass-c1fc` |
| **PR** | https://github.com/SheehanT98/GallopKeyboard/pull/17 (OPEN) |
| **Base** | `origin/main` |
| **Reviewed at tip** | `a30ec1d` |
| **Product commit** | `bf77099` |
| **Verdict** | **approve** |

## Summary

Plan 006 Parakeet streaming pass matches scope: Case A (in-tree sherpa-onnx), `StreamingAsrEngine`/`ParakeetEngine`, `ImeTextCommitter` + `InputConnectionSupplier`, `StreamingTranscriber` bound over stub, `Flags.polishEnabled` default false, 11 unit tests, optional gated smoke test, `docs/models.md` + inventory + plans `DONE`. Automated verification **PASS** (`verify.sh`, `:asr`/`:ime` unit tests, ARM64 `libsherpa-onnx-jni.so` in APK). Manual on-device / model sideload deferred — **risk for human, not an auto-block**. **Approve** for double-check.

## Scope compliance

| Area | Status |
|------|--------|
| Case A — wrap existing sherpa; no new submodule/native CMake | Present (vendored `OnlineRecognizer`/`OnlineStream` + jniLibs `.so`) |
| `StreamingAsrEngine` + `ParakeetEngine` + `ParakeetConfig` + `AsrModelMissingException` | Present |
| Model paths `filesDir/models/parakeet/{encoder,decoder,joiner}.onnx` + `tokens.txt` | Present |
| `ImeTextCommitter` (null-safe composing / commit / clear) + `InputConnectionSupplier` | Present |
| `StreamingTranscriber` — begin/partials every 5 frames / finalize composing / cancel clear | Present |
| `Flags.polishEnabled` false → `clearComposing()` after 50 ms on stop | Present |
| Missing-model / JNI failure → toast, no crash | Present |
| Hilt: `AudioModule` binds `StreamingTranscriber`; stub `@VisibleForTesting` | Present |
| `DictusImeService` wires live `InputConnection` per input view | Present |
| Unit tests ≥7 (`StreamingTranscriberTest` 8 + `ImeTextCommitterTest` 3) | Present (11) |
| Optional `ParakeetSmokeTest` + reference WAV (`RUN_ASR_SMOKE=1`) | Present |
| `docs/models.md` + inventory Plan 006 + plans README `DONE` | Present |
| Polish / download UX / SmartVoiceButton gesture / visual polish | Out of scope (correctly absent) |

Minor deviations (acceptable):

- Single squashed `feat(ime)` product commit vs plan’s multi-commit granularity — fine for quick mode.
- Unit tests live under `:ime` (`ime/src/test/...`) rather than plan’s `asr/src/test/...` — correct module ownership for `StreamingTranscriber`.
- APK still contains pre-existing `armeabi-v7a` sherpa `.so` from Dictus Case A jniLibs (plan’s “no v7a” applied to Case B native builds).

## Verification evidence

Relies on tester artifact (`03-test-report.md`, SHA `8071be0`) plus spot-check of sources at tip `a30ec1d` (meta-only commits after product `bf77099`):

| Criterion | Result |
|-----------|--------|
| `bash scripts/verify.sh` | PASS (tester) |
| `:asr:testDebugUnitTest` + `:ime:testDebugUnitTest`; ≥7 new | PASS — 11/11 (tester) |
| APK `arm64-v8a/libsherpa-onnx-jni.so` | PASS (tester + local APK spot-check) |
| `StreamingTranscriber` bound as `Transcriber` | Confirmed (`AudioModule`) |
| `docs/models.md` + Plan 006 inventory + plans `DONE` | Confirmed |
| PR #17 | OPEN, MERGEABLE (do not recreate) |
| On-device “hello world” partials / cancel / missing-model toast | **NOT RUN** — no adb device / models |
| `RUN_ASR_SMOKE=1` instrumented smoke | **NOT RUN** |

## Risks for the human reviewer

1. **Manual IME/ASR acceptance deferred**: Sideload streaming Parakeet models per `docs/models.md`, hold-and-speak in Notes (partials → final when polish off), cancel mid-hold, and missing-model toast. Optional: `RUN_ASR_SMOKE=1 ./gradlew :asr:connectedDebugAndroidTest`.
2. **`InputConnection` thread affinity**: Partials call `setComposingText` from the recorder-dispatcher collect path (`flowOn` + `onAudioFrame`). Some hosts are picky about IC writes off the IME main thread — watch for dropped/janky composing updates on device.
3. **`runBlocking` on session start/cancel**: Gesture path can block briefly while engine work runs on the recorder dispatcher; first `beginStream` may also lazy-load models — possible first-press hitch.
4. **First-use without models**: Until Plan 008, missing files toast only; no download UX (by design).
5. **`finalize()` naming**: Interface method name matches plan; JVM `Object.finalize` overlap is a known Kotlin quirk — build/tests pass; leave as-is unless a future lint flags it.
6. **CI**: PR checks were in progress at review time — confirm green before merge.

## Blockers

None.

## Advance

`npm run devteam:advance -- job-006 --to double_checking`
