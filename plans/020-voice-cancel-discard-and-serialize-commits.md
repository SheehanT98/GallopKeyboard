# Plan 020: Discard composing on voice cancel and serialize InputConnection writes

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 86dfd89..HEAD -- ime/src/main/java/com/gallopkeyboard/ime/asr/ImeTextCommitter.kt ime/src/main/java/com/gallopkeyboard/ime/asr/StreamingTranscriber.kt ime/src/main/java/com/gallopkeyboard/ime/panel/SmartVoiceButton.kt ime/src/main/java/com/gallopkeyboard/ime/panel/GestureFsm.kt ime/src/test/java/com/gallopkeyboard/ime/asr/ImeTextCommitterTest.kt ime/src/test/java/com/gallopkeyboard/ime/asr/StreamingTranscriberTest.kt ime/src/test/java/com/gallopkeyboard/ime/panel/GestureFsmTest.kt docs/adr/0003-smart-button-gesture-spec.md`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED
- **Depends on**: none (orthogonal to 019; may land in parallel)
- **Category**: bug
- **Planned at**: commit `86dfd89`, 2026-07-20

## Why this matters

ADR-0003 requires cancel (`ACTION_CANCEL` / drag-off) to **discard** the
session with **no commit**. Today `ImeTextCommitter.clearComposing()` only
calls `finishComposingText()`, which **commits** the composing region — so
cancel leaves streaming partials in the host field. Separately, partial
updates are `Handler.post`ed while cancel/stop call the committer
synchronously, so a stale `setComposing(partial)` can undo cancel or polish.
`SmartVoiceButton` also never emits `GestureEvent.Cancel` on pointer cancel —
only `Up` — so system cancellations finalize instead of discard.

## Current state

```kotlin
// ImeTextCommitter.kt
open fun clearComposing() {
    ic()?.finishComposingText()  // commits composing text — wrong for cancel
}

// StreamingTranscriber.kt
private fun setComposingOnMain(text: String) {
    mainHandler.post { committer.setComposing(text) }
}
override fun onSessionCancel(session: AudioSession) {
    runBlocking(asrDispatcher.dispatcher) { engine.cancel() }
    committer.clearComposing()  // sync; pending posts can still run after
}

// SmartVoiceButton.kt pointer loop — only Up on !pressed
if (!change.pressed) {
    fsm.onEvent(GestureEvent.Up(...))
    break
}
```

ADR-0003 §6: cancel must discard — no commit, no polish.

**Conventions**: Keep ASR off the IME main thread (ADR-0002 / Plan 014). Match
existing `ImeTextCommitter` / `StreamingTranscriberTest` Fake patterns. Do not
change polish success path (`commitText`) semantics.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| IME unit tests | `./gradlew :ime:testDebugUnitTest` | BUILD SUCCESSFUL |
| Focused | `./gradlew :ime:testDebugUnitTest --tests '*ImeTextCommitter*' --tests '*StreamingTranscriber*' --tests '*GestureFsm*'` | BUILD SUCCESSFUL |
| Verify | `bash scripts/verify.sh` | `OK` |

## Scope

**In scope**:
- `ime/src/main/java/com/gallopkeyboard/ime/asr/ImeTextCommitter.kt`
- `ime/src/main/java/com/gallopkeyboard/ime/asr/StreamingTranscriber.kt`
- `ime/src/main/java/com/gallopkeyboard/ime/panel/SmartVoiceButton.kt`
- Matching tests under `ime/src/test/...`
- `docs/dictus-inventory.md` Plan 020 additions
- `plans/README.md` status

**Out of scope**:
- Removing `runBlocking` from cancel for perf (PERF follow-up; do not expand)
- Moving `onSessionStop` off `rememberCoroutineScope` (CORRECTNESS-03 — separate)
- Changing GestureFsm thresholds / ADR-0003 constants
- DictationService legacy path
- Polish accuracy / Whisper

## Git workflow

- Branch: `advisor/020-voice-cancel-discard-serialize-ic`
- Commit style: `fix(ime): discard composing on cancel; serialize IC posts`
- Do NOT push/PR unless instructed.

## Steps

### Step 1: Split finish vs discard on ImeTextCommitter

Replace/extend API:

```kotlin
/** Commit the composing region (finish). Used when keeping streaming text. */
open fun finishComposing() {
    ic()?.finishComposingText()
}

/** Discard composing text without committing it. Used on cancel / failure wipe. */
open fun discardComposing() {
    ic()?.let { connection ->
        connection.setComposingText("", 1)
        connection.finishComposingText()
    }
}

@Deprecated("Use finishComposing() or discardComposing()", ReplaceWith("finishComposing()"))
open fun clearComposing() = finishComposing()
```

Prefer updating all call sites and **deleting** `clearComposing` if the blast
radius is small (grep first). Call-site policy:

| Caller intent | Method |
|---------------|--------|
| Cancel / recognition failure wipe | `discardComposing()` |
| Polish-disabled stop that keeps final streaming text | `finishComposing()` |
| Success polish | unchanged `commitText` / `commitFinal` |

**Verify**: update `ImeTextCommitterTest`:
- `discardComposing` → `setComposingText("")` then `finishComposing`
- `finishComposing` → finish only (no empty setComposing)
Extend `FakeInputConnection` to record both.

`./gradlew :ime:testDebugUnitTest --tests '*ImeTextCommitter*'` → pass.

### Step 2: Serialize all committer ops through one Main queue with generation

In `StreamingTranscriber`:

1. Add `@Volatile private var commitGeneration = 0` (or `AtomicInteger`).
2. On `onSessionCancel` and at the start of successful/failed stop paths that
   must win, increment generation.
3. Replace raw `mainHandler.post` with:

```kotlin
private fun postCommit(op: () -> Unit) {
    val gen = commitGeneration
    mainHandler.post {
        if (gen != commitGeneration) return@post
        op()
    }
}
```

4. For cancel specifically: increment generation **first**, then
   `mainHandler.removeCallbacksAndMessages(null)` (or track Runnable tokens),
   then `discardComposing()` on Main (post with new gen, or run if already on
   Main). Goal: no earlier `setComposing(partial)` runs after cancel.

5. Update `onSessionCancel` to use `discardComposing` (not finish).

6. Update failure paths that currently `clearComposingOnMain` to discard.

7. Polish-disabled stop (`Flags.polishEnabled == false`) should **finish**
   (keep text), not discard.

**Verify**: extend `StreamingTranscriberTest` with a fake committer that
records ordered ops; simulate: setComposing posted, then cancel, assert final
state is discard and no later setComposing applied. If the existing test harness
cannot inject Handler timing, unit-test a small `CommitSerialQueue` helper
extracted from StreamingTranscriber.

`./gradlew :ime:testDebugUnitTest --tests '*StreamingTranscriber*'` → pass.

### Step 3: Emit GestureEvent.Cancel on pointer cancel

In `SmartVoiceButton` `pointerInput` / `awaitEachGesture` loop:

1. Detect cancel: `event.type == PointerEventType.Cancel` (and/or consumed
   cancel). Do **not** treat every `!change.pressed` as cancel.
2. On cancel: cancel hold timer; `fsm.onEvent(GestureEvent.Cancel(...))`
   with current elapsed time / position if the FSM event requires it — match
   existing `GestureEvent.Cancel` constructor used in `GestureFsmTest`.
3. Keep intentional release as `GestureEvent.Up`.

Confirm `GestureFsm` already maps Cancel → discard / `onSessionCancel` (tests
at `GestureFsmTest`). If UI never fed Cancel before, wiring is enough.

**Verify**: `./gradlew :ime:testDebugUnitTest --tests '*GestureFsm*'` → pass.
If a Compose pointer unit test is impractical, add a small pure function
`fun mapPointerEnd(type: PointerEventType): GestureEnd = Cancel | Up` and test it.

### Step 4: Inventory + verify

Append Plan 020 notes to `docs/dictus-inventory.md`. Update `plans/README.md`.
Run full verify.

**Verify**: `bash scripts/verify.sh` → `OK`.

## Test plan

- `ImeTextCommitterTest`: discard vs finish semantics.
- `StreamingTranscriberTest` or `CommitSerialQueueTest`: stale posts ignored
  after generation bump / cancel.
- Existing GestureFsm cancel tests remain green.
- Manual: record → drag off button past slop → host field must **not** keep
  partial text; record → release → polish still commits.

## Done criteria

- [ ] Cancel path uses `discardComposing` (empty composing + finish), not finish-only
- [ ] Stale `setComposing` Handler posts cannot apply after cancel
- [ ] Pointer cancel emits `GestureEvent.Cancel` (not Up)
- [ ] Polish / intentional stop still commits text
- [ ] `./gradlew :ime:testDebugUnitTest` exits 0
- [ ] `bash scripts/verify.sh` → `OK`
- [ ] Scope respected; inventory + plans README updated

## STOP conditions

- ADR-0003 cancel semantics appear intentionally changed in-tree — stop.
- `removeCallbacksAndMessages(null)` would break unrelated Main posts owned by
  the same Handler instance shared elsewhere — then use tokenized Runnables
  only; if still unclear, stop and report.
- Fix requires rewriting Plan 014 frame queue — stop; keep serialization local
  to committer posts.

## Maintenance notes

- Follow-up (not this plan): CORRECTNESS-03 — `onSessionStop` launched on
  Compose `rememberCoroutineScope` is cancelled when leaving voice panel;
  move stop/polish to IME/`applicationScope`.
- Follow-up: replace `runBlocking` in `onSessionCancel` (PERF-04).
- Reviewer: watch WhatsApp/Gmail composing behavior — some editors treat
  `setComposingText("")` differently; if discard fails on a host, document in
  `docs/limitations.md` and fall back to `deleteSurroundingText` of composing
  length only after measuring.
