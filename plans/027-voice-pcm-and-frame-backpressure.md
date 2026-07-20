# Plan 027: Move voice PCM off Main and bound ASR frame work

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat bfc7085..HEAD -- ime/src/main/java/com/gallopkeyboard/ime/panel/SmartVoiceButton.kt ime/src/main/java/com/gallopkeyboard/ime/asr/StreamingTranscriber.kt ime/src/main/java/com/gallopkeyboard/ime/audio/AudioRecorderEngine.kt ime/src/main/java/com/gallopkeyboard/ime/audio/RingByteBuffer.kt ime/src/main/java/com/gallopkeyboard/ime/audio/AsrCoroutineDispatcher.kt ime/src/main/java/com/gallopkeyboard/ime/audio/RecorderCoroutineDispatcher.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.
>
> **Assumption**: Plan 024 may have changed stop scope — preserve that
> behavior; do not re-bind stop to Compose scope. Plan 020 session epoch may
> exist — integrate with frame queue rather than fighting it.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED
- **Depends on**: Plan 024 recommended (stop scope); Plan 014 done
- **Category**: perf
- **Planned at**: commit `bfc7085`, 2026-07-20

## Why this matters

Plan 014 moved ONNX off Main, but recording still **collects** PCM on Compose
Main (`rememberCoroutineScope`), allocating and ring-writing every ~100 ms.
`StreamingTranscriber` still `launch`es unbounded per-frame jobs. Idle voice
panel runs `rememberInfiniteTransition` forever. These burn jank/battery on
S22 against CONTEXT acceptance #1/#3.

## Current state

```kotlin
// SmartVoiceButton.kt
recordingJob = scope.launch {  // Main
    audioRecorderEngine.start().collect { frame ->
        writeFrameToBuffer(...)  // ByteArray alloc + sync write
        transcriber.onAudioFrame(...)
    }
}
val pulseTransition = rememberInfiniteTransition(...) // always composed

// StreamingTranscriber.kt
override fun onAudioFrame(...) {
    val frameCopy = frame.copyOf()
    asrScope.launch { engine.acceptFrame(frameCopy); ... }
}

// AudioRecorderEngine KDoc warns: never collect on Main; flowOn only moves upstream
```

**Conventions**: ADR-0002. Use existing `RecorderCoroutineDispatcher` /
`AsrCoroutineDispatcher`. Match Plan 014 threading comments.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| IME tests | `./gradlew :ime:testDebugUnitTest` | BUILD SUCCESSFUL |
| Verify | `bash scripts/verify.sh` | `OK` |

## Scope

**In scope**:
- `SmartVoiceButton.kt` — collector dispatcher + pulse gating
- `StreamingTranscriber.kt` — bounded/serial frame consumer
- Optionally `RingByteBuffer.kt` — `write(ShortArray)` bulk helper
- Optionally shrink initial ring capacity (document tradeoff)
- Tests for queue/drop behavior with fakes
- `docs/dictus-inventory.md` Plan 027 additions
- `plans/README.md`

**Out of scope**:
- Spatial swipe hit-test index
- DictionaryEngine early-exit (tiny; may land as drive-by **only if** already
  touching DictionaryEngine — prefer skip)
- Whisper float conversion redesign
- DictationService `AudioCaptureManager`

## Git workflow

- Branch: `advisor/027-voice-pcm-and-frame-backpressure`
- Commit: `perf(ime): record PCM off Main; bound ASR frames; gate pulse`
- Do NOT push/PR unless instructed.

## Steps

### Step 1: Collect PCM on recorder dispatcher

1. Inject/pass `RecorderCoroutineDispatcher` into `SmartVoiceButton` (EntryPoint
   already can expose it — add if missing).
2. `recordingJob = sessionScope.launch(recorderDispatcher.dispatcher) { collect... }`
   or use the IME session scope from Plan 024 **with** `recorderDispatcher`.
3. Post `visualRecording` updates to Main if needed (`withContext(Main)`).
4. Add `RingByteBuffer.writeShorts(frame: ShortArray)` using bulk copy; reuse a
   thread-local/scratch `ByteArray` in the collector loop to avoid per-frame
   alloc in `writeFrameToBuffer`.

**Verify**: existing audio/ring tests pass; add writeShorts unit test.

### Step 2: Single serial ASR consumer with conflation

Replace per-frame `launch` with one of:

- `Channel<ShortArray>(capacity = 1, onBufferOverflow = DROP_OLDEST)` + single
  `asrScope` consumer looping `acceptFrame`, **or**
- Mutex + “latest frame / coalesced” flag on the single-thread dispatcher.

Keep partial polling every N frames. Preserve Plan 020/024 epoch checks.

**Verify**: fake engine slow `acceptFrame` — queue does not grow without bound
(test with counter of pending copies < threshold).

### Step 3: Gate infinite pulse to recording-only

Only call `rememberInfiniteTransition` when `isRecordingVisual` is true
(use `if (isRecordingVisual) { ... }` composable branch or `Animatable`).

**Verify**: compile; visual smoke note in inventory.

### Step 4: Optional ring capacity

If easy: start ring at 60 s capacity and grow to 5 min on demand — else skip
and note deferred. Do not break 5 min ceiling behavior from Plan 005.

**Verify**: `bash scripts/verify.sh`.

## Test plan

- `RingByteBuffer` bulk write equivalence vs byte loop.
- StreamingTranscriber backpressure: many frames + slow engine → no OOM-style
  unbounded job count (assert via instrumentation counters on fake).
- Existing streaming/polish tests green with Plan 024 semantics.

## Done criteria

- [ ] PCM collect/write not on Compose Main
- [ ] ASR frames not unbounded per-`launch`
- [ ] Idle voice panel does not run infinite pulse transition
- [ ] `verify.sh` OK; scope respected

## STOP conditions

- Moving collect breaks Plan 024 stop/cancel ordering — stop and reconcile
  with 024 first.
- Channel design drops frames so aggressively that streaming partials freeze —
  tune capacity to 2–4; if still bad, stop with measurements.

## Maintenance notes

- Reviewer: 60 s dictation on S22 — UI stays responsive; partials still update.
- Battery: idle voice panel CPU should drop vs infinite transition.
