# Plan 024: Keep voice stop/polish alive after leaving the voice panel

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat bfc7085..HEAD -- ime/src/main/java/com/gallopkeyboard/ime/panel/SmartVoiceButton.kt ime/src/main/java/com/gallopkeyboard/ime/asr/PolishingTranscriber.kt ime/src/main/java/com/gallopkeyboard/ime/asr/StreamingTranscriber.kt ime/src/main/java/com/gallopkeyboard/ime/panel/VoiceSessionCleanup.kt ime/src/test/java/com/gallopkeyboard/ime/asr/PolishingTranscriberTest.kt ime/src/test/java/com/gallopkeyboard/ime/panel/VoiceSessionCleanupTest.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.
>
> **Assumption**: Plans 019–023 are merged. Plan 020’s `discardComposing` /
> commit generation APIs may exist — use them if present; do not reimplement
> Plan 020.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED
- **Depends on**: Plan 020 recommended (cancel/discard semantics); can land
  alone if `clearComposing` / `discardComposing` naming is reconciled carefully
- **Category**: bug
- **Planned at**: commit `bfc7085`, 2026-07-20

## Why this matters

After the user releases the mic, `onSessionStop` (streaming finalize + Whisper
polish) is launched on Compose `rememberCoroutineScope()`. Switching back to
the typing panel (or hiding the IME) disposes `SmartVoiceButton`, which
cancels that scope — so polish dies mid-flight and the host field can keep
unfinished composing text. Separately, blank polish results overwrite a good
streaming partial, and late `acceptFrame` jobs after finalize toast “recognition
failed.” This is the highest-trust voice bug remaining after Phase 8.

## Current state

```kotlin
// SmartVoiceButton.kt
val scope = rememberCoroutineScope()
onSessionStop = { _ ->
    ...
    scope.launch { transcriber.onSessionStop(session) }  // cancelled on dispose
}
DisposableEffect(Unit) {
    onDispose {
        ...
        activeSession = cancelActiveSession(transcriber, activeSession)
        // Also cancels in-flight stop if activeSession already null but job lives on scope
    }
}

// PolishingTranscriber.kt
if (polished != null) {
    committer.commitText(polished)  // empty string is non-null → wipes partial
} else {
    committer.clearComposing()  // or finish/discard after Plan 020
}

// StreamingTranscriber.kt — per-frame asrScope.launch; exceptions toast failure
```

`DictusImeService` already has `bindingScope = MainScope()` that outlives
composables until `onDestroy`.

**Conventions**: ADR-0002 — STT off IME main thread; ADR-0003 — stop triggers
polish. Match `VoiceSessionCleanup` / `PolishingTranscriberTest` patterns.
Apply `TextPostProcessor` (core) on successful polish — same as
`DictationService`.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| IME tests | `./gradlew :ime:testDebugUnitTest` | BUILD SUCCESSFUL |
| Focused | `./gradlew :ime:testDebugUnitTest --tests '*Polishing*' --tests '*Streaming*' --tests '*VoiceSession*'` | BUILD SUCCESSFUL |
| Verify | `bash scripts/verify.sh` | `OK` |

## Scope

**In scope**:
- `ime/.../panel/SmartVoiceButton.kt`
- `ime/.../panel/VoiceSessionCleanup.kt` (and/or new small helper)
- `ime/.../asr/PolishingTranscriber.kt`
- `ime/.../asr/StreamingTranscriber.kt` (session generation / late-frame guard)
- Matching unit tests
- `docs/dictus-inventory.md` Plan 024 additions
- `plans/README.md`

**Out of scope**:
- PCM collector off Main / frame channel backpressure (Plan 027)
- Unbinding DictationService (Plan 028)
- Permission `runBlocking` redesign (may touch SmartVoiceButton — leave alone
  unless a one-line conflict; prefer STOP if entangled)
- Autocorrect / typing UI

## Git workflow

- Branch: `advisor/024-voice-stop-outlives-panel`
- Commit: `fix(ime): keep stop/polish alive after voice panel leave`
- Do NOT push/PR unless instructed.

## Steps

### Step 1: Introduce an application/IME-scoped job owner for stop

1. Prefer launching `onSessionStop` on a scope that outlives the composable:
   - Option A (simplest): inject/pass a `@Singleton` or EntryPoint-provided
     `CoroutineScope` (e.g. expose from `ModelLifecycleManager`’s scope, or add
     `VoiceSessionScope` in Hilt) with `SupervisorJob() + Dispatchers.Main.immediate`
     or Default + main for IC writes.
   - Option B: pass `bindingScope` from `DictusImeService` through
     `PanelHost` → `VoicePanel` → `SmartVoiceButton` as `sessionScope: CoroutineScope`.

2. Track `var stoppingJob: Job?`. On `onSessionStop`:
   - Cancel **recording** job only.
   - Set `activeSession = null` **after** capturing session ref.
   - `stoppingJob = sessionScope.launch { transcriber.onSessionStop(session) }`.

3. On `DisposableEffect` dispose:
   - If `activeSession != null` (still recording) → cancel session (existing).
   - If `stoppingJob?.isActive == true` → **do not cancel it**; let polish finish.
   - Do **not** call `onSessionCancel` for a session already handed to stop.

**Verify**: unit-test a pure helper if extraction helps, e.g.
`shouldCancelOnDispose(recording, stopping)` — recording true → cancel;
stopping true → keep. Pattern after `VoiceSessionCleanupTest`.

### Step 2: Blank polish must keep streaming partial

In `PolishingTranscriber.onSessionStop`:

```kotlin
val processed = polished?.let { TextPostProcessor.process(it) }.orEmpty()
when {
    processed.isNotEmpty() -> committer.commitText(processed)
    else -> committer.finishComposing() // or clearComposing/finish after Plan 020
}
```

Never `commitText("")`. On timeout/exception (`polished == null`), finish
composing to commit the last streaming partial (Plan 020: use `finishComposing`,
not `discardComposing`).

**Verify**: extend `PolishingTranscriberTest` — empty polish keeps/finishes
partial; non-empty goes through post-processor (assert trailing `. `).

### Step 3: Session generation — ignore late frames after stop/cancel

In `StreamingTranscriber`:

1. `@Volatile var sessionEpoch = 0` (or AtomicInteger).
2. `onSessionStart` increments epoch; capture `val epoch = sessionEpoch` in
   frame launches.
3. `onSessionStop` / `onSessionCancel` increment epoch **before** finalize/cancel.
4. Frame jobs: if `epoch != sessionEpoch` return silently.
5. Catch `IllegalStateException` with “No active stream” (or engine-specific)
   when epoch already advanced → no toast.

**Verify**: `StreamingTranscriberTest` — frame after stop does not call
failure toast / discard path.

### Step 4: Inventory + verify

Append Plan 024 notes. `bash scripts/verify.sh` → `OK`.

## Test plan

- Dispose-while-stopping does not cancel polish (helper or instrumentation-free
  fake scope test).
- Empty polish → finish, not empty commit.
- TextPostProcessor applied on success.
- Late frame after stop → no failure toast.
- Existing polish timeout / success tests updated for new commit semantics.

## Done criteria

- [ ] `onSessionStop` not tied to Compose `rememberCoroutineScope` cancellation
- [ ] Dispose mid-recording still cancels; dispose mid-stop does not
- [ ] Blank polish does not wipe streaming text
- [ ] Successful polish uses `TextPostProcessor`
- [ ] Late frames after stop/cancel do not toast failure
- [ ] `./gradlew :ime:testDebugUnitTest` exits 0
- [ ] `bash scripts/verify.sh` → `OK`
- [ ] Scope respected

## STOP conditions

- Required scope injection forces large Hilt graph rewrites across app module —
  use parameter-passing from `DictusImeService.bindingScope` instead; if that
  still cannot thread through `PanelHost`, stop and report.
- Plan 020 APIs renamed differently than finish/discard — adapt names, don’t
  fork a third clear API.
- Engine exception messages differ — match on sessionEpoch alone if needed.

## Maintenance notes

- Plan 027 will move PCM collect off Main — keep stop/polish scope separate
  from recorder dispatcher.
- Reviewer: manually release mic then immediately tap keyboard icon — polish
  must still replace composing text in Notes/WhatsApp.
