# Plan 033: Async mic permission + keep InputConnection alive through polish

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 32b0d20..HEAD -- ime/src/main/java/com/gallopkeyboard/ime/panel/SmartVoiceButton.kt ime/src/main/java/com/gallopkeyboard/ime/DictusImeService.kt ime/src/main/java/com/gallopkeyboard/ime/asr/PolishingTranscriber.kt ime/src/main/java/com/gallopkeyboard/ime/asr/ImeTextCommitter.kt ime/src/main/java/com/gallopkeyboard/ime/di/AsrModule.kt ime/src/main/java/com/gallopkeyboard/ime/panel/VoiceSessionCleanup.kt`
> Requires Phase 9 (`voiceStopScope` / `stoppingJob`). If absent, STOP.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED
- **Depends on**: Plans 024/027 (voice stop scope) — assumed merged
- **Category**: bug / perf
- **Planned at**: commit `32b0d20`, 2026-07-20

## Why this matters

Two post–Phase 9 voice reliability holes:

1. **`runBlocking` mic permission** inside `pointerInput` on the UI/input
   thread (`SmartVoiceButton`) — first grant can stall the IME for seconds
   (ANR-shaped; CONTEXT #3).
2. **`onFinishInputView` nulls `inputConnectionSupplier`** while Plan 024
   polish may still run on `voiceStopScope` — transcript can be dropped or
   committed nowhere when the user hides the keyboard mid-polish.

## Current state

```kotlin
// SmartVoiceButton.kt (~232-235)
if (!AudioRecorderEngine.checkPermission(context)) {
    val granted = runBlocking {
        permissionRequester.request(context)
    }
    ...
}

// DictusImeService.kt (~163-166)
override fun onFinishInputView(finishingInput: Boolean) {
    entryPoint.inputConnectionSupplier().supplier = { null }
    panelController.reset()
    ...
}
// PolishingTranscriber commits via live supplier → null mid-finalize
```

**Conventions**: ADR-0002/0003. Keep stop/polish on `voiceStopScope` (024).
Do not move PCM collect back to Main (027).

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Focused | `./gradlew :ime:testDebugUnitTest --tests '*Polishing*' --tests '*VoiceSession*' --tests '*Streaming*'` | BUILD SUCCESSFUL |
| IME | `./gradlew :ime:testDebugUnitTest` | BUILD SUCCESSFUL |
| Verify | `bash scripts/verify.sh` | `OK` |

## Scope

**In scope**:
- `ime/.../panel/SmartVoiceButton.kt` — async permission; no `runBlocking`
  on pointer thread
- `ime/.../DictusImeService.kt` and/or `ImeTextCommitter` /
  `InputConnectionSupplier` — pin IC for in-flight stop/polish
- Matching unit tests (fake permission; committer with frozen supplier)
- `docs/dictus-inventory.md` Plan 033
- `plans/README.md`

**Out of scope**:
- Permission receiver exported fix (SECURITY deferred — separate plan)
- Whisper preload / ModelLifecycleManager redesign
- `runBlocking` inside `StreamingTranscriber.onSessionCancel` — fix if
  one-line easy on same path; else note follow-up (don’t expand scope)

## Git workflow

- Branch: `cursor/033-async-mic-permission-and-polish-ic`
- Commit: `fix(ime): async mic permission; pin IC through polish`
- Do NOT push/PR unless instructed.

## Steps

### Step 1: Async mic permission in SmartVoiceButton

1. Remove `runBlocking { permissionRequester.request(context) }` from the
   gesture handler.
2. Pattern: if missing permission, `sessionScope.launch` (or Compose scope
   for UI-only) request; on grant, show toast-free continue — either
   require a second press after grant **or** arm recording if pointer still
   down (document choice; prefer **second press** if FSM races are hard).
3. Never block the pointer thread waiting on the Activity result.

**Verify**: `rg -n 'runBlocking' ime/src/main/java/com/gallopkeyboard/ime/panel/SmartVoiceButton.kt` → no matches.

### Step 2: Pin InputConnection for in-flight polish

1. When starting stop/polish (`onSessionStop` / stoppingJob), capture the
   current `InputConnection` (or a committer bound to that IC) and use it
   for finalize commits — **not** a supplier that `onFinishInputView` nulls.
2. `onFinishInputView`: if `stoppingJob` / polish in flight, defer clearing
   the supplier until polish completes (callback / finally); still reset
   panel UI as today.
3. If captured IC is dead (`!ic.isActive` or commit fails), drop commit
   silently (log non-PII); do not crash.

**Verify**: unit test — supplier set to null mid-polish still commits via
pinned IC (fake).

### Step 3: Docs + verify

Inventory notes both behaviors. `bash scripts/verify.sh`.

## Test plan

- Permission path: no `runBlocking` in SmartVoiceButton.
- Polish commit with supplier nulled still delivers text (fake IC).
- Existing polish empty/success tests remain green.

## Done criteria

- [ ] No `runBlocking` in `SmartVoiceButton` gesture path
- [ ] Hide-keyboard mid-polish does not drop a successful polish commit when
      IC still valid
- [ ] Phase 9 stop-scope behavior preserved
- [ ] Tests + `verify.sh` OK
- [ ] Scope respected

## STOP conditions

- Pinning IC across `onFinishInputView` causes commits into the **wrong**
  field after app switch — STOP; use “drop if target changed” instead of
  forcing commit.
- Permission API cannot be made async without rewriting
  `PermissionRequester` broadly — minimal async wrapper only; if Hilt graph
  explodes, STOP.

## Maintenance notes

- Follow-up: `StreamingTranscriber.onSessionCancel` `runBlocking` on Main.
- Reviewer: grant mic on first use; release mic then immediately hide IME —
  polish must still land in Notes when possible.
