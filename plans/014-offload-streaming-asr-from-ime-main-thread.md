# Plan 014: Offload streaming ASR frame work from the IME main thread

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**:
> ```
> git diff --stat 3571aab..HEAD -- \
>   ime/src/main/java/com/gallopkeyboard/ime/asr/StreamingTranscriber.kt \
>   ime/src/main/java/com/gallopkeyboard/ime/panel/SmartVoiceButton.kt \
>   asr/src/main/java/com/gallopkeyboard/asr/parakeet/ParakeetEngine.kt \
>   ime/src/test/java/com/gallopkeyboard/ime/asr/StreamingTranscriberTest.kt \
>   docs/adr/0002-hybrid-stt-pipeline.md
> ```
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED
- **Depends on**: none
- **Category**: perf | bug
- **Planned at**: commit `3571aab`, 2026-07-17
- **Issue**: —

## Why this matters

ADR-0002 requires STT work off the IME main thread. Today
`StreamingTranscriber.onAudioFrame` calls `ParakeetEngine.acceptFrame` /
`decode` **synchronously on whatever thread invokes it**. The caller is
`SmartVoiceButton`, which collects `AudioRecorderEngine` frames on
`rememberCoroutineScope()` (Compose main dispatcher). ONNX decode on the
IME main thread risks jank and ANRs while the user holds the mic —
exactly the failure mode Plan 010 / `CONTEXT.md` acceptance criterion #3
care about.

After this plan: every `acceptFrame` / `currentPartial` / `beginStream` /
`cancel` / `finalize` call runs on `RecorderCoroutineDispatcher` (or an
equivalent single-thread ASR dispatcher), and composing-text updates are
posted back to the main thread only at the `ImeTextCommitter` boundary.

## Current state

### ADR constraint (must honor)

From `docs/adr/0002-hybrid-stt-pipeline.md`:

```
- STT work must run off IME main thread
```

Also `AGENTS.md` Coding conventions:

```
- **Threading**: STT inference and audio I/O off the IME main thread (see ADR-0002).
```

### Hot path (broken)

`ime/src/main/java/com/gallopkeyboard/ime/panel/SmartVoiceButton.kt` —
recording job launched on Compose scope; frames call transcriber directly:

```kotlin
recordingJob = scope.launch {
    try {
        audioRecorderEngine.start().collect { frame ->
            val sessionRef = activeSession ?: return@collect
            writeFrameToBuffer(sessionRef.buffer, frame)
            // ...
            transcriber.onAudioFrame(sessionRef, frame)
        }
    } catch (e: Exception) { /* ... */ }
}
```

`ime/src/main/java/com/gallopkeyboard/ime/asr/StreamingTranscriber.kt` —
`onSessionStart` / `onSessionCancel` use `runBlocking(dispatcher)`, but
`onAudioFrame` does **not** hop dispatchers:

```kotlin
override fun onAudioFrame(session: AudioSession, frame: ShortArray) {
    try {
        engine.acceptFrame(frame)
        frameCount++
        if (frameCount % PARTIAL_POLL_INTERVAL_FRAMES == 0) {
            val partial = engine.currentPartial()
            if (partial != lastPartial) {
                lastPartial = partial
                committer.setComposing(partial)
            }
        }
    } catch (e: Exception) { /* ... */ }
}
```

`asr/.../ParakeetEngine.kt` — `acceptFrame` runs Sherpa-ONNX decode in a
loop (CPU-heavy):

```kotlin
s.acceptWaveform(samples, SAMPLE_RATE)
while (r.isReady(s)) {
    r.decode(s)
}
```

### Existing conventions to match

- `RecorderCoroutineDispatcher` (`ime/.../audio/RecorderCoroutineDispatcher.kt`)
  is a singleton single-thread executor named `"AudioRecorder"`. Reuse it
  for ASR frame work **or** introduce a sibling `AsrCoroutineDispatcher`
  with the same shape if you need to keep AudioRecord I/O and ONNX decode
  from starving each other. Prefer **one** dedicated ASR single-thread
  dispatcher if AudioRecord already owns `RecorderCoroutineDispatcher`
  exclusively — check `AudioRecorderEngine` before deciding.
- `onSessionStop` already uses `withContext(dispatcher.dispatcher)` —
  mirror that pattern for frames.
- `ImeTextCommitter` must remain main-thread-safe for `InputConnection`
  (existing tests in `ImeTextCommitterTest.kt`). If `setComposing` is not
  already main-safe, post via `Handler(Looper.getMainLooper())` like
  `showToast` in `StreamingTranscriber`.
- Tests: model after `ime/src/test/.../StreamingTranscriberTest.kt`
  (Robolectric, fake engine).

### Vocabulary

- Use existing terms: **streaming pass**, **polish pass**, **composing
  text**, **AudioSession** (see ADR-0002 / `CONTEXT.md`).

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Drift check | see header | empty or only expected unrelated noise |
| Unit tests (ime ASR) | `./gradlew :ime:testDebugUnitTest --tests 'com.gallopkeyboard.ime.asr.*'` | BUILD SUCCESSFUL, all pass |
| Full local verify | `bash scripts/verify.sh` | ends with `OK` |
| Grep guard | `rg -n "override fun onAudioFrame" ime/src/main/java/com/gallopkeyboard/ime/asr/` | body hops off main (see Done criteria) |

## Scope

**In scope** (the only files you should modify):

- `ime/src/main/java/com/gallopkeyboard/ime/asr/StreamingTranscriber.kt`
- `ime/src/main/java/com/gallopkeyboard/ime/audio/RecorderCoroutineDispatcher.kt`
  **or** a new sibling dispatcher file under `ime/.../audio/` / `ime/.../asr/`
  (only if you split AudioRecord vs ASR threads)
- `ime/src/main/java/com/gallopkeyboard/ime/asr/ImeTextCommitter.kt`
  (only if composing commits need an explicit main-thread post)
- `ime/src/test/java/com/gallopkeyboard/ime/asr/StreamingTranscriberTest.kt`
- `docs/dictus-inventory.md` — append a short "Plan 014 additions" section
- `plans/README.md` — status row only

**Out of scope** (do NOT touch):

- `PolishingTranscriber.kt` polish timeout / Whisper path (already
  `withTimeout` + suspend) except as an unchanged pass-through of
  `onAudioFrame`
- `SmartVoiceButton.kt` gesture FSM / UI (optional: you may switch
  `scope.launch` to `dispatcher` **only** if required after the
  transcriber fix; prefer fixing inside `StreamingTranscriber` so all
  callers benefit)
- `ParakeetEngine.kt` algorithm / model paths
- Bottom-row `KeyType.MIC` / `DictationService` path (Plan 016)
- Dispose/cancel leak (Plan 016)
- Model SHA verify on IME `onCreate` (Plan 015)

## Git workflow

- Branch: `cursor/offload-streaming-asr-main-thread-1534` (or the
  operator's `/devteamquick` job branch)
- Commit style (from recent history): `feat(ime): ...` / `fix(ime): ...`
- Do NOT push or open a PR unless the operator / devteam flow instructs it

## Steps

### Step 1: Confirm which dispatcher owns AudioRecord

Read `AudioRecorderEngine` (and any Hilt module that binds
`RecorderCoroutineDispatcher`). Decide:

- **A.** Reuse `RecorderCoroutineDispatcher` for ASR frames (simplest;
  OK if AudioRecord collect already hops off main and decode can share
  the thread), **or**
- **B.** Add `AsrCoroutineDispatcher` (single-thread `"AsrEngine"`) and
  inject it into `StreamingTranscriber` only.

Document the choice in the commit message body in one sentence.

**Verify**: `rg -n "RecorderCoroutineDispatcher|AudioRecorder" ime/src/main/java/com/gallopkeyboard/ime/audio/` → you can name the chosen dispatcher.

### Step 2: Make `onAudioFrame` hop to the ASR dispatcher

Change `StreamingTranscriber.onAudioFrame` so `engine.acceptFrame` and
`engine.currentPartial` run on the chosen dispatcher.

Recommended shape (adjust to match existing style — avoid introducing
unstructured concurrency):

- Prefer making the frame path **non-blocking for the caller**: enqueue
  work with `scope.launch(dispatcher)` / `dispatcher.dispatcher.executor`
  and drop or coalesce frames if the queue is backed up (IME must not
  block Compose).
- **Do not** wrap every frame in `runBlocking` on the main thread — that
  still stalls the IME.
- Keep `frameCount` / `lastPartial` mutations on the ASR thread only
  (single-thread dispatcher makes this safe without locks).
- When a new partial is ready, call `committer.setComposing` on the
  **main** thread (Handler post or `withContext(Main)` from a coroutine
  that already left Main).

Also move `onSessionStart`'s engine work fully onto the dispatcher
**without** `runBlocking` on the Compose caller if feasible. If you must
keep a blocking start for ordering, block only on a background caller —
never on Main. Prefer:

```kotlin
// conceptual — match repo style
override fun onSessionStart(session: AudioSession) {
    // launch or execute on ASR dispatcher; await only if caller is already off-main
}
```

If changing `onSessionStart` signature would ripple, keep the function
sync but ensure SmartVoiceButton invokes start from a background job
**or** use `runBlocking` only after confirming the caller is the ASR
thread. Prefer updating SmartVoiceButton's `onSessionStart` callback to
`scope.launch(asrDispatcher) { transcriber.onSessionStart(...) }` **only
if** Step 2 alone cannot guarantee Main freedom — and if you touch
SmartVoiceButton, limit the diff to dispatcher hops (still allowed only
if required; otherwise leave UI alone and fix inside StreamingTranscriber
with an internal `CoroutineScope(dispatcher + SupervisorJob())`).

**Verify**:
```
rg -n "fun onAudioFrame|acceptFrame|runBlocking|withContext" \
  ime/src/main/java/com/gallopkeyboard/ime/asr/StreamingTranscriber.kt
```
→ `acceptFrame` appears only inside a dispatcher hop; no
`runBlocking` on the frame path.

### Step 3: Characterization / regression tests

Update `StreamingTranscriberTest.kt`:

1. Keep existing partial-commit tests passing (may need
   `Shadows.shadowOf(Looper.getMainLooper()).idle()` if composing posts
   to Main).
2. Add a test that records the thread name (or a `Thread.currentThread()`
   flag) inside a fake engine's `acceptFrame` and asserts it is **not**
   named like the main/Robolectric main thread when `onAudioFrame` is
   invoked from the test thread after the async hop settles. Practical
   approach with the existing fake:
   - Fake engine stores `Thread.currentThread().name` on `acceptFrame`.
   - Call `onAudioFrame` then `runBlocking`/`advanceUntilIdle` /
     shadow Looper idle until the fake saw the frame.
   - Assert the recorded name equals the ASR dispatcher thread name
     (e.g. `"AudioRecorder"` or `"AsrEngine"`).

**Verify**:
```
./gradlew :ime:testDebugUnitTest --tests 'com.gallopkeyboard.ime.asr.StreamingTranscriberTest'
```
→ BUILD SUCCESSFUL.

### Step 4: Inventory + verify

Append to `docs/dictus-inventory.md` a "Plan 014 additions" bullet list
naming the dispatcher choice and the threading guarantee.

**Verify**: `bash scripts/verify.sh` → ends with `OK`.

## Test plan

- File: `ime/src/test/java/com/gallopkeyboard/ime/asr/StreamingTranscriberTest.kt`
- Pattern: existing fake `FakeStreamingAsrEngine` in that file
- Cases:
  - existing: ten frames → two partials; duplicate partials suppressed;
    cancel clears composing
  - **new**: `acceptFrame` runs on ASR dispatcher thread
  - **new** (optional): rapid frames do not throw; composing still
    eventually updates on Main (idle Looper)

## Done criteria

- [ ] `StreamingTranscriber.onAudioFrame` never calls
      `engine.acceptFrame` / `currentPartial` on the IME/Compose main
      thread
- [ ] No new `runBlocking` on the per-frame path
- [ ] `./gradlew :ime:testDebugUnitTest --tests 'com.gallopkeyboard.ime.asr.*'`
      exits 0 with the new thread-affinity test green
- [ ] `bash scripts/verify.sh` exits 0 / prints `OK`
- [ ] `docs/dictus-inventory.md` has a "Plan 014 additions" section
- [ ] No files outside the in-scope list are modified (`git status`)
- [ ] `plans/README.md` status row for 014 → `DONE`

## STOP conditions

Stop and report back (do not improvise) if:

- Live code no longer matches the excerpts (drift).
- Making frames async breaks ordering such that `onSessionStop` /
  `finalize` races with in-flight frames and you cannot serialize them
  on the single-thread dispatcher without redesigning `Transcriber`.
- `ImeTextCommitter` / `InputConnection` crashes unless called from Main
  and posting to Main is insufficient (then report with stack traces).
- Fix appears to require rewriting `ParakeetEngine` JNI bindings.

## Maintenance notes

- Any new `Transcriber` implementation must hop off Main for inference.
- Reviewers: watch for accidental `runBlocking` on Compose scope.
- Deferred: dual mic path (`DictationService`) threading — Plan 016;
  dispose cancel — Plan 016.
