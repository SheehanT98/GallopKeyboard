# Job 017 — Test report

| Field | Value |
|-------|-------|
| **Job** | job-017 |
| **Branch** | `cursor/devteam-job-017-execute-plan-014-offload-streaming-asr-from-ime--c1fc` |
| **PR** | [#40](https://github.com/SheehanT98/GallopKeyboard/pull/40) |
| **Plan** | `plans/014-offload-streaming-asr-from-ime-main-thread.md` |
| **Tester** | devteam-tester (composer-2.5) |
| **Tested at** | 2026-07-17T16:15:00Z |
| **SHA tested** | `854638e50058b396a83d2872f852bda6f7c9462c` |
| **Verdict** | **PASS** |

## Environment

| Check | Command | Result |
|-------|---------|--------|
| Branch | `git branch --show-current` | `cursor/devteam-job-017-execute-plan-014-offload-streaming-asr-from-ime--c1fc` |
| Job status | `devteam/jobs/job-017/meta.json` | `testing` |

## Commands run

| Check | Command | Result |
|-------|---------|--------|
| Drift check | `git diff --stat 3571aab..HEAD` (in-scope files) | 2 files changed: `StreamingTranscriber.kt`, `StreamingTranscriberTest.kt` (expected implementation delta) |
| Dispatcher grep | `rg -n "RecorderCoroutineDispatcher\|AsrCoroutineDispatcher\|AudioRecorder" ime/src/main/java/com/gallopkeyboard/ime/audio/` | `AsrCoroutineDispatcher` (`"AsrEngine"`) present; `RecorderCoroutineDispatcher` (`"AudioRecorder"`) unchanged |
| Frame-path grep | `rg -n "fun onAudioFrame\|acceptFrame\|runBlocking\|withContext" ime/.../StreamingTranscriber.kt` | `acceptFrame` inside `asrScope.launch`; no `runBlocking` on frame path |
| IME ASR unit tests | `./gradlew :ime:testDebugUnitTest --tests 'com.gallopkeyboard.ime.asr.*'` | BUILD SUCCESSFUL |
| Full verify | `bash scripts/verify.sh` | exit 0, ends with `OK` |
| Product diff scope | `git diff --name-only origin/main...HEAD` | 6 files: `AsrCoroutineDispatcher.kt`, `StreamingTranscriber.kt`, `StreamingTranscriberTest.kt`, `PolishingTranscriberTest.kt`, `docs/dictus-inventory.md`, `plans/README.md` |

## Done criteria (Plan 014)

| Criterion | Result |
|-----------|--------|
| `onAudioFrame` never calls `acceptFrame` / `currentPartial` on IME/Compose main thread | **PASS** — work enqueued via `asrScope.launch` on `AsrCoroutineDispatcher` |
| No new `runBlocking` on per-frame path | **PASS** — `runBlocking` only in `onSessionCancel` (not frame path) |
| `./gradlew :ime:testDebugUnitTest --tests 'com.gallopkeyboard.ime.asr.*'` exits 0 with thread-affinity test green | **PASS** — `acceptFrame runs on ASR dispatcher thread` asserts `"AsrEngine"` thread |
| `bash scripts/verify.sh` → `OK` | **PASS** |
| `docs/dictus-inventory.md` has Plan 014 additions | **PASS** |
| `plans/README.md` 014 → `DONE` | **PASS** |
| No out-of-scope production files modified | **PASS** — `SmartVoiceButton.kt`, `ParakeetEngine.kt`, `ImeTextCommitter.kt` untouched; `PolishingTranscriberTest.kt` adjacency-only |

## STOP conditions

| Condition | Outcome |
|-----------|---------|
| Live code drift vs plan excerpts | **Not hit** — hot path matches expected async dispatcher pattern |
| Frame async races with `onSessionStop` / `finalize` | **Not hit** — `onSessionStop` uses `withContext(asrDispatcher)`; single-thread dispatcher serializes |
| `ImeTextCommitter` requires Main and posting insufficient | **Not hit** — composing posts via `Handler(Looper.getMainLooper())` |
| Fix requires rewriting `ParakeetEngine` JNI | **Not hit** — engine unchanged |

## Blockers

None.

## Advance

`npm run devteam:advance -- job-017 --to reviewing`
