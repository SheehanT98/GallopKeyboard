# Job 017 — Review

| Field | Value |
|-------|-------|
| **Job** | job-017 |
| **Branch** | `cursor/devteam-job-017-execute-plan-014-offload-streaming-asr-from-ime--c1fc` |
| **PR** | [#40](https://github.com/SheehanT98/GallopKeyboard/pull/40) |
| **Plan** | `plans/014-offload-streaming-asr-from-ime-main-thread.md` |
| **ADR** | `docs/adr/0002-hybrid-stt-pipeline.md` |
| **Reviewer** | devteam-reviewer (cursor-grok-4.5-high) |
| **Reviewed at** | 2026-07-17T16:12:00Z |
| **SHA reviewed** | `854638e` (feat) + `36e7b2b` (test report) |
| **Base** | `origin/main...HEAD` |
| **Verdict** | **APPROVE** |

## Summary

Plan 014 / ADR-0002 require STT inference off the IME main thread. The change introduces `AsrCoroutineDispatcher` (`"AsrEngine"`) and makes `StreamingTranscriber.onAudioFrame` / `onSessionStart` enqueue work on that dispatcher (non-blocking for Compose callers), with composing updates posted to Main via `Handler`. Done criteria and tester evidence are green; scope is tight. **APPROVE**.

## Scope compliance

| In / out of scope | Result |
|-------------------|--------|
| `StreamingTranscriber.kt` — frame/start hop off Main; no per-frame `runBlocking` | **Met** |
| New `AsrCoroutineDispatcher.kt` (plan option B) | **Met** — justified: AudioRecord already owns `RecorderCoroutineDispatcher` via `flowOn` |
| `ImeTextCommitter.kt` | **Correctly untouched** — Main posting done in transcriber |
| `StreamingTranscriberTest.kt` — thread-affinity + rapid-frames | **Met** |
| `docs/dictus-inventory.md` — Plan 014 additions | **Met** |
| `plans/README.md` — 014 → DONE | **Met** |
| `SmartVoiceButton.kt` / `ParakeetEngine.kt` / polish path | **Untouched** (preferred) |
| `PolishingTranscriberTest.kt` | **Acceptable adjacency** — constructor + `drainAsrAndMain` for async frames; not production scope creep |

Product diff vs `origin/main` (excluding job artifacts): six files — `AsrCoroutineDispatcher.kt`, `StreamingTranscriber.kt`, two test files, inventory, plans index.

## Implementation review

### Dispatcher choice (Step 1)

Option **B** (`AsrCoroutineDispatcher`) matches the plan’s preference when AudioRecord already owns `"AudioRecorder"`. Documented in inventory and code KDoc.

### Frame path (Step 2)

```65:84:ime/src/main/java/com/gallopkeyboard/ime/asr/StreamingTranscriber.kt
    override fun onAudioFrame(session: AudioSession, frame: ShortArray) {
        val frameCopy = frame.copyOf()
        asrScope.launch {
            try {
                engine.acceptFrame(frameCopy)
                // ... currentPartial + setComposingOnMain ...
            } catch (e: Exception) { /* ... */ }
        }
    }
```

- Caller returns immediately — Compose/`rememberCoroutineScope` is not stalled by ONNX.
- `frame.copyOf()` avoids sharing mutable PCM with the ASR thread.
- `frameCount` / `lastPartial` mutate only on the single ASR thread.
- `acceptFrame` / `currentPartial` never appear outside the dispatcher hop.
- Composing uses `Handler(Looper.getMainLooper())` — correct `InputConnection` boundary.

`onSessionStart` likewise uses `asrScope.launch` (removed Main-blocking `runBlocking`). Same single-thread scope serializes start before subsequent frame jobs.

`onSessionStop` keeps `withContext(asrDispatcher)` so finalize waits behind in-flight ASR work on the same executor — addresses the plan’s race STOP concern without redesigning `Transcriber`.

### Tests (Step 3)

- Existing partial / cancel / polish-flag cases updated with `drainAsrAndMain()`.
- New `acceptFrame runs on ASR dispatcher thread` asserts thread name starts with `"AsrEngine"`.
- Optional rapid-frames case present.

### ADR-0002

Consequence “STT work must run off IME main thread” is satisfied for the streaming hot path. Polish / dual-mic remain out of scope (Plans 016+).

## Done criteria

| Criterion | Result |
|-----------|--------|
| `onAudioFrame` never calls `acceptFrame` / `currentPartial` on IME/Compose main | **PASS** |
| No new `runBlocking` on per-frame path | **PASS** (`runBlocking` only in `onSessionCancel`) |
| `:ime` ASR unit tests + thread-affinity green | **PASS** (tester) |
| `bash scripts/verify.sh` → `OK` | **PASS** (tester) |
| Inventory Plan 014 section | **PASS** |
| No out-of-scope production files | **PASS** |
| `plans/README.md` 014 → DONE | **PASS** |

## Verification evidence

| Check | Result |
|-------|--------|
| Tester report | **PASS** (`03-test-report.md`, SHA `854638e`) |
| Frame-path grep (`acceptFrame` / `runBlocking`) | `acceptFrame` inside `asrScope.launch`; no frame-path `runBlocking` |
| Product file list vs plan | Matches + justified test adjacency |
| PR | [#40](https://github.com/SheehanT98/GallopKeyboard/pull/40) OPEN (CI `build` in progress at review time) |

## STOP conditions

| Condition | Outcome |
|-----------|---------|
| Drift vs plan excerpts | **Not hit** |
| Async frames race stop/finalize without serialization | **Not hit** — single-thread ASR dispatcher |
| Main posting insufficient for `ImeTextCommitter` | **Not hit** |
| Parakeet JNI rewrite required | **Not hit** |

## Findings

None blocking.

**Nit (non-blocking):** Plan recommended drop/coalesce when the ASR queue backs up; this PR enqueues every frame. Correctness and Main freedom are fine; under slow decode the queue can grow (see Risks).

## Risks for the human reviewer

- **`onSessionCancel` still uses `runBlocking(asrDispatcher)`** and `SmartVoiceButton` invokes cancel from the gesture/UI path. If many frame jobs are already queued, Main can briefly wait for them before `cancel` runs. Pre-existing cancel pattern; Plan 016 may tighten dispose/cancel further. Not a regression of the frame hot path.
- **No frame coalescing** — sustained overload could increase latency / memory of queued `ShortArray` copies; monitor if decode is slower than realtime on low-end devices.
- **CI** on PR #40 was still in progress at review time — confirm green before merge.
- Manual hold-to-talk jank check on device remains the best validation of ADR-0002 in production conditions.

## Advance

`npm run devteam:advance -- job-017 --to double_checking`
