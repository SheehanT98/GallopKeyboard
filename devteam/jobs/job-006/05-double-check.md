# Job 006 — Double-check

| Field | Value |
|-------|-------|
| **Job** | job-006 |
| **Branch** | `cursor/devteam-job-006-execute-plan-006-parakeet-streaming-pass-c1fc` |
| **PR** | https://github.com/SheehanT98/GallopKeyboard/pull/17 |
| **Double-checked at** | 2026-07-16T18:25:00Z |
| **SHA (tip)** | `7dbe0e2eee3e669396f1bc2c1c13f303d9545cd6` |
| **Product commit** | `bf770995f92590812ac53a56890f8a9531d3d6e4` |
| **Verdict** | **READY** |

## Cold verification (independent re-run)

Environment: `source scripts/android-env.sh` (`ANDROID_HOME=/opt/android-sdk`, `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`).

| Check | Command | Result |
|-------|---------|--------|
| Full verify | `bash scripts/verify.sh` | exit 0, printed `OK` |
| Plan 005 drift guard | `grep -q "Plan 005 additions" docs/dictus-inventory.md` | OK |
| Plan 006 drift guard | `grep -q "Plan 006 additions" docs/dictus-inventory.md` | OK |
| ASR unit tests | `./gradlew --no-daemon :asr:testDebugUnitTest` | BUILD SUCCESSFUL (via verify.sh) |
| IME unit tests | `./gradlew --no-daemon :ime:testDebugUnitTest` | BUILD SUCCESSFUL (via verify.sh) |
| Streaming transcriber tests | `--tests '*StreamingTranscriberTest'` | 8 tests, 0 failures |
| ImeTextCommitter tests | `--tests '*ImeTextCommitterTest'` | 3 tests, 0 failures |
| APK sherpa native lib | `unzip -l app-debug.apk \| grep sherpa` | `arm64-v8a/libsherpa-onnx-jni.so` present |
| DI binding | `AudioModule.bindTranscriber(StreamingTranscriber)` | present |
| `docs/models.md` | `test -f docs/models.md` | OK |
| Plan status | `plans/README.md` row 006 | DONE |
| `meta.pr` | `devteam/jobs/job-006/meta.json` | `https://github.com/SheehanT98/GallopKeyboard/pull/17` |

### Key file presence

| Path | Present |
|------|---------|
| `asr/.../parakeet/ParakeetEngine.kt` | yes |
| `asr/.../parakeet/ParakeetConfig.kt` | yes |
| `asr/.../parakeet/StreamingAsrEngine.kt` | yes |
| `ime/.../asr/StreamingTranscriber.kt` | yes |
| `ime/.../asr/ImeTextCommitter.kt` | yes |
| `ime/src/test/.../StreamingTranscriberTest.kt` | yes (8 cases) |
| `ime/src/test/.../ImeTextCommitterTest.kt` | yes (3 cases) |
| `asr/src/androidTest/.../ParakeetSmokeTest.kt` | yes |
| `asr/src/androidTest/assets/reference-hello-world.wav` | yes |

## Plan 006 done criteria

| Criterion | Result |
|-----------|--------|
| `bash scripts/verify.sh` exits 0 | PASS |
| `:asr:testDebugUnitTest` and `:ime:testDebugUnitTest` pass; ≥7 new tests | PASS (11 new: 8 + 3) |
| APK includes ARM64 sherpa-onnx `.so` (Case A bindings) | PASS |
| `StreamingTranscriber` bound as `Transcriber` impl | PASS |
| With models sideloaded, on-device "hello world" partials in Notes | **NOT RUN** — no adb device (human risk) |
| `docs/models.md` exists | PASS |
| `docs/dictus-inventory.md` Plan 006 additions | PASS |
| `plans/README.md` row 006 `DONE` | PASS |

## Review confirmation (`04-review.md`)

- Review verdict **approve** — confirmed at cold re-check; no scope regressions since product commit `bf77099`.
- Reviewer blockers: **none** — still none.
- Acceptable deviations noted in review (squashed commit, tests under `:ime`, pre-existing armeabi-v7a `.so`) — unchanged.
- Tip `7dbe0e2` is meta-only (`chore(devteam): advance job-006 to double_checking`) after review commit `a5d79d8`; no product-code drift.

## Risks for human (not auto-blockers)

1. **Manual IME/ASR acceptance** — sideload Parakeet models per `docs/models.md`; validate partials, cancel, missing-model toast in Notes before merge.
2. **Optional smoke test** — `RUN_ASR_SMOKE=1` instrumented `ParakeetSmokeTest` not run (no device/models).
3. **CI** — PR #17 `build` check was **IN_PROGRESS** at double-check time; confirm green before `/devteam approve job-006`.
4. **InputConnection thread affinity** and **first-press model load hitch** — per review; watch on device.

## Blockers

None for automated gate.

## Advance

`npm run devteam:advance -- job-006 --to awaiting_review`
