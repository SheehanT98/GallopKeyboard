# Job 017 — Double-check

| Field | Value |
|-------|-------|
| **Job** | job-017 |
| **Branch** | `cursor/devteam-job-017-execute-plan-014-offload-streaming-asr-from-ime--c1fc` |
| **PR** | [#40](https://github.com/SheehanT98/GallopKeyboard/pull/40) |
| **Plan** | `plans/014-offload-streaming-asr-from-ime-main-thread.md` |
| **ADR** | `docs/adr/0002-hybrid-stt-pipeline.md` |
| **Double-checker** | devteam-double-checker (composer-2.5) |
| **Checked at** | 2026-07-17T16:20:00Z |
| **SHA checked** | `5717e8c` |
| **Review verdict** | APPROVE (`04-review.md`) |
| **Verdict** | **READY** |

## Summary

Cold re-verification of Plan 014 done criteria confirms the streaming ASR hot path is off the IME/Compose main thread via `AsrCoroutineDispatcher` (`"AsrEngine"`) and non-blocking `asrScope.launch` in `StreamingTranscriber`. Reviewer findings are confirmed; no blocking issues. **READY** for human merge after CI is green.

## Done criteria (independent re-run)

| Criterion | Result | Evidence |
|-----------|--------|----------|
| `onAudioFrame` never calls `acceptFrame` / `currentPartial` on IME/Compose main | **PASS** | `acceptFrame` / `currentPartial` only inside `asrScope.launch` (lines 67–77); caller returns immediately |
| No new `runBlocking` on per-frame path | **PASS** | `runBlocking` only in `onSessionCancel` (line 105); frame path has none |
| `:ime` ASR unit tests + thread-affinity green | **PASS** | `./gradlew :ime:testDebugUnitTest --tests 'com.gallopkeyboard.ime.asr.*'` → BUILD SUCCESSFUL; `acceptFrame runs on ASR dispatcher thread` asserts `"AsrEngine"` |
| `bash scripts/verify.sh` → `OK` | **PASS** | exit 0, ends with `OK` |
| `docs/dictus-inventory.md` Plan 014 section | **PASS** | Section documents `AsrCoroutineDispatcher` and threading guarantee |
| No out-of-scope production files modified | **PASS** | `SmartVoiceButton.kt`, `ParakeetEngine.kt`, `ImeTextCommitter.kt` unchanged vs `origin/main`; `PolishingTranscriberTest.kt` is test-only adjacency |
| `plans/README.md` 014 → `DONE` | **PASS** | Row 014 status `DONE` |

## Review confirmation (`04-review.md`)

| Review finding | Confirmed |
|----------------|-----------|
| Option B `AsrCoroutineDispatcher` justified (AudioRecord owns `"AudioRecorder"`) | **Yes** — new file matches `RecorderCoroutineDispatcher` shape |
| Frame path non-blocking; `frame.copyOf()`; Main composing via `Handler` | **Yes** — matches live `StreamingTranscriber.kt` |
| `onSessionStop` serializes via `withContext(asrDispatcher)` | **Yes** — single-thread dispatcher prevents stop/finalize race |
| Thread-affinity + rapid-frames tests present | **Yes** — both tests in `StreamingTranscriberTest.kt` |
| Nit: no frame coalescing under backlog | **Acknowledged** — non-blocking; not a done-criteria failure |
| `onSessionCancel` `runBlocking` from UI path (pre-existing pattern) | **Acknowledged** — not a frame-path regression |

## STOP conditions

| Condition | Outcome |
|-----------|---------|
| Drift vs plan excerpts | **Not hit** |
| Async frames race stop/finalize | **Not hit** — single-thread ASR dispatcher |
| Main posting insufficient for `ImeTextCommitter` | **Not hit** — `setComposingOnMain` / `clearComposingOnMain` use `Handler` |
| Parakeet JNI rewrite required | **Not hit** |

## Commands run (cold)

| Check | Command | Result |
|-------|---------|--------|
| Branch / SHA | `git branch --show-current`; `git rev-parse HEAD` | correct branch; `5717e8c` |
| Frame-path grep | `rg -n "fun onAudioFrame\|acceptFrame\|runBlocking\|withContext" ime/.../StreamingTranscriber.kt` | `acceptFrame` inside `asrScope.launch`; no frame `runBlocking` |
| Product diff | `git diff --name-only origin/main...HEAD` | 6 product files + job artifacts; scope compliant |
| IME ASR tests | `./gradlew :ime:testDebugUnitTest --tests 'com.gallopkeyboard.ime.asr.*'` | BUILD SUCCESSFUL |
| Full verify | `bash scripts/verify.sh` | exit 0, `OK` |

## CI / human gate

PR #40 `build` check was **pending** at double-check time. Confirm green before `/devteam approve job-017`.

## Advance

`npm run devteam:advance -- job-017 --to awaiting_review`
