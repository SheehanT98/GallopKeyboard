# Job 006 — Test report

| Field | Value |
|-------|-------|
| **Job** | job-006 |
| **Branch** | `cursor/devteam-job-006-execute-plan-006-parakeet-streaming-pass-c1fc` |
| **PR** | [#17](https://github.com/SheehanT98/GallopKeyboard/pull/17) |
| **Tester** | devteam-tester (composer-2.5) |
| **Tested at** | 2026-07-16T18:21:00Z |
| **SHA tested** | `8071be0c915461192e05152b9e18483fe8a679cb` |
| **Verdict** | **PASS** (automated); manual on-device deferred |

## Environment

```bash
source scripts/android-env.sh
# ANDROID_HOME=/opt/android-sdk
# JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

| Check | Command | Result |
|-------|---------|--------|
| Branch | `git branch --show-current` | on job-006 branch |
| Device | `adb devices` | no devices attached |

## Commands run

| Check | Command | Result |
|-------|---------|--------|
| Drift guard | `bash scripts/verify.sh` | exit 0, printed `OK` |
| Plan 005 inventory | `grep -q "Plan 005 additions" docs/dictus-inventory.md` | OK |
| Plan 006 inventory | `grep -q "Plan 006 additions" docs/dictus-inventory.md` | OK |
| ASR unit tests | `./gradlew --no-daemon :asr:testDebugUnitTest` | BUILD SUCCESSFUL |
| IME unit tests | `./gradlew --no-daemon :ime:testDebugUnitTest` | BUILD SUCCESSFUL |
| Streaming transcriber tests | `--tests '*StreamingTranscriberTest'` | 8 tests, 0 failures |
| ImeTextCommitter tests | `--tests '*ImeTextCommitterTest'` | 3 tests, 0 failures |
| APK sherpa native lib | `unzip -l app-debug.apk \| grep sherpa` | `arm64-v8a/libsherpa-onnx-jni.so` present |
| DI binding | `AudioModule.bindTranscriber(StreamingTranscriber)` | present |
| Models doc | `test -f docs/models.md` | OK |
| Plan status | `plans/README.md` row 006 | DONE |

### verify.sh breakdown

- `assembleDebug` — BUILD SUCCESSFUL
- `testAll` — BUILD SUCCESSFUL
- `lint` — BUILD SUCCESSFUL
- Dictus package grep guard — no hits outside `third_party/`
- Model binary grep guard — no hits outside `third_party/`

### StreamingTranscriberTest (8/8)

| Test | Result |
|------|--------|
| session start commits empty composing region once | pass |
| ten frames deliver two partial updates | pass |
| duplicate partials are not re-committed | pass |
| session stop finalizes and sets composing text | pass |
| session cancel clears composing | pass |
| model missing on start shows toast path without composing | pass |
| polish flag off clears composing after stop | pass |
| polish flag on leaves composing after stop | pass |

### ImeTextCommitterTest (3/3)

| Test | Result |
|------|--------|
| setComposing forwards to InputConnection | pass |
| clearComposing finishes composing region | pass |
| null connection is safe for all operations | pass |

## Done criteria (Plan 006)

| Criterion | Result |
|-----------|--------|
| `bash scripts/verify.sh` exits 0 | PASS |
| `:asr:testDebugUnitTest` and `:ime:testDebugUnitTest` pass; ≥7 new tests | PASS (11 new: 8 + 3) |
| APK includes ARM64 sherpa-onnx `.so` (Case A bindings) | PASS |
| `StreamingTranscriber` bound as `Transcriber` impl | PASS |
| With models sideloaded, on-device "hello world" partials in Notes | **NOT RUN** — no device |
| `docs/models.md` exists | PASS |
| `docs/dictus-inventory.md` Plan 006 additions | PASS |
| `plans/README.md` row 006 `DONE` | PASS |

## Manual on-device (deferred)

Plan acceptance tests require a physical device or configured AVD with Parakeet models sideloaded per `docs/models.md`. Not executed in this session.

| # | Action | Result |
|---|--------|--------|
| 1 | Sideload models via `adb push` | not run |
| 2 | Hold-and-speak "hello world" — partial text in host field | not run |
| 3 | Release — final transcript (composing → committed when polish off) | not run |
| 4 | Cancel mid-recording — no text in host field | not run |
| 5 | Recording without models — toast, no crash | not run |
| 6 | `RUN_ASR_SMOKE=1` instrumented Parakeet smoke test | not run |

## Blockers

- **Manual IME/ASR acceptance (non-blocking for automated gate):** Live partial commits, cancel behavior, missing-model toast, and optional `ParakeetSmokeTest` not run — no `adb` device attached and models not sideloaded. Reviewer or human should validate per `docs/models.md` before merge.

## Advance

Automated verification passed → `npm run devteam:advance -- job-006 --to reviewing`
