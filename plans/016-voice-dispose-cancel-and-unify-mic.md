# Plan 016: Cancel ASR on SmartVoice dispose and unify dual mic entry points

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
>   ime/src/main/java/com/gallopkeyboard/ime/panel/SmartVoiceButton.kt \
>   ime/src/main/java/com/gallopkeyboard/ime/panel/GestureFsm.kt \
>   ime/src/main/java/com/gallopkeyboard/ime/DictusImeService.kt \
>   ime/src/main/java/com/gallopkeyboard/ime/ui/KeyboardScreen.kt \
>   ime/src/main/java/com/gallopkeyboard/ime/model/KeyboardLayouts.kt \
>   ime/src/test/java/com/gallopkeyboard/ime/panel/
> ```
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED
- **Depends on**: preferably after Plan 014 (threading), but **not blocked** —
  dispose/cancel can land first
- **Category**: bug | tech-debt
- **Planned at**: commit `3571aab`, 2026-07-17
- **Issue**: —

## Why this matters

Two related correctness problems in voice UX:

1. **Dispose leak**: `SmartVoiceButton`'s `DisposableEffect` cancels the
   recording coroutine and calls `fsm.reset()`, but `GestureFsm.reset()`
   only clears state flags — it does **not** invoke `onSessionCancel`.
   If the composable leaves composition mid-recording (panel switch,
   IME hide), Parakeet's stream / mic session can remain active with no
   cancel path.

2. **Dual mic paths**: Toolbar / voice panel use `SmartVoiceButton` →
   hybrid `Transcriber` (Parakeet + Whisper polish). The bottom-row
   `KeyType.MIC` key calls `DictusImeService` → `DictationService`
   (legacy Dictus dictation). Users get two different voice products
   with different accuracy, permissions UX, and composing behavior.

After this plan: dispose always cancels an active ASR session; bottom
mic is wired to the **same** hybrid path (or removed from layouts with
an explicit product note — see Step 3 recommended default).

## Current state

### Dispose without cancel

`SmartVoiceButton.kt`:

```kotlin
DisposableEffect(Unit) {
    onDispose {
        recordingJob?.cancel()
        holdTimerJob?.cancel()
        fsm.reset()
    }
}
```

`GestureFsm.reset()`:

```kotlin
fun reset() {
    state = GestureState.IDLE
    sessionAnnounced = false
}
```

Compare with the cancel path that **does** call the transcriber:

```kotlin
onSessionCancel = {
    visualRecording = false
    recordingJob?.cancel()
    recordingJob = null
    val session = activeSession
    activeSession = null
    if (session != null) {
        transcriber.onSessionCancel(session)
    }
},
```

### Dual mic

`KeyboardScreen.kt` — `KeyType.MIC` → `onMicTap()`:

```kotlin
KeyType.MIC -> {
    onMicTap()
    Timber.d("Mic key tapped")
}
```

`DictusImeService.kt` — mic tap toggles `DictationService` recording /
transcribing states (not `SmartVoiceButton`).

Layouts still place a mic key on the bottom row
(`KeyboardLayouts.kt` — `KeyType.MIC`).

Plan 011 already shipped toolbar **Voice** + thin voice panel using
`SmartVoiceButton`.

### Conventions / exemplars

- Gesture FSM tests: `ime/src/test/.../panel/GestureFsmTest.kt`
- Layout assertions: `ime/src/test/.../model/KeyboardLayoutTest.kt`
  (currently expects `KeyType.MIC` present — update if you remove the key)
- ADR-0003 governs SmartVoice gestures; ADR-0002 governs hybrid STT —
  bottom mic should not reinvent a third pipeline.

## Commands you will need

| Purpose | Command | Expected |
|---------|---------|----------|
| Panel tests | `./gradlew :ime:testDebugUnitTest --tests 'com.gallopkeyboard.ime.panel.*'` | SUCCESS |
| Layout tests | `./gradlew :ime:testDebugUnitTest --tests 'com.gallopkeyboard.ime.model.KeyboardLayoutTest'` | SUCCESS |
| Full verify | `bash scripts/verify.sh` | `OK` |

## Scope

**In scope**:

- `ime/src/main/java/com/gallopkeyboard/ime/panel/SmartVoiceButton.kt`
- `ime/src/main/java/com/gallopkeyboard/ime/panel/GestureFsm.kt`
  (only if you add `resetAndCancel`-style API — prefer fixing dispose in
  the composable without changing FSM semantics for tests)
- `ime/src/main/java/com/gallopkeyboard/ime/DictusImeService.kt`
- `ime/src/main/java/com/gallopkeyboard/ime/ui/KeyboardScreen.kt`
  (mic routing callbacks)
- `ime/src/main/java/com/gallopkeyboard/ime/model/KeyboardLayouts.kt`
  (only if removing mic key)
- Matching unit tests under `ime/src/test/...`
- `docs/dictus-inventory.md` — Plan 016 additions
- `plans/README.md` — status

**Out of scope**:

- Rewriting `DictationService` / companion-app recording screens
  (`RecordingScreen`, onboarding test recording) — those stay for the
  companion app
- ASR main-thread offload implementation details (Plan 014) — call
  `onSessionCancel` regardless
- Accent popup / swipe (Plan 017)
- Changing ADR-0003 hold threshold / cancel slop values

## Git workflow

- Branch: `cursor/voice-dispose-cancel-unify-mic-1534`
- Commit style: `fix(ime): cancel ASR on SmartVoice dispose; unify mic`
- Do NOT push/PR unless instructed

## Steps

### Step 1: Fix dispose to cancel active sessions

In `SmartVoiceButton` `onDispose`:

1. Cancel `recordingJob` / `holdTimerJob` (keep).
2. If `activeSession != null`, call the **same** cleanup as
   `onSessionCancel` (clear visual state, null session, 
   `transcriber.onSessionCancel(session)`).
3. Then `fsm.reset()` **or** send `GestureEvent.Cancel` if that already
   routes through `cancelSession()` — prefer one path to avoid double
   cancel. If double cancel is unsafe, make `onSessionCancel` idempotent
   (engine.cancel on null stream should no-op — verify
   `ParakeetEngine.cancel`).

Do **not** only call `fsm.reset()`.

**Verify**: read the updated `DisposableEffect` block; it must reference
`onSessionCancel` or `transcriber.onSessionCancel`.

### Step 2: Unit-test the cancel-on-dispose contract

Add a test at the FSM/composable seam that is practical without full
Compose UI:

- Option A (preferred if low friction): extend `GestureFsmTest` with a
  documented note that UI dispose must call cancel — and add a small
  pure helper extracted from SmartVoiceButton, e.g.
  `fun cancelActiveSession(session, transcriber)`, tested directly.
- Option B: document manual QA in inventory if Compose dispose testing
  is too heavy — still implement Step 1.

Minimum automated bar: helper or FSM cancel path covered; inventory
lists manual "switch panel mid-recording → mic stops / no orphan decode".

**Verify**: panel unit tests pass.

### Step 3: Unify dual mic entry points (pick the default)

**Recommended product default (do this unless STOP):**

- Change `KeyType.MIC` handling so it **opens the voice panel** /
  focuses the thin voice panel / toolbar Voice affordance that already
  hosts `SmartVoiceButton`, **instead of** calling
  `toggleRecordingViaDictationService`.
- Keep `DictationService` binding for companion-app / legacy screens.
- Update `KeyboardLayoutTest` only if mic key remains (it should).

**Alternative (only if opening the panel from MIC is awkwardly coupled):**

- Remove `KeyType.MIC` from LETTERS/NUMBERS/SYMBOLS bottom rows and
  update layout tests; rely on toolbar Voice + Voice panel toggle.

Do **not** leave both DictationService IME mic and SmartVoice hybrid
active as user-facing IME controls.

Wire MIC → panel in `DictusImeService` / `PanelHost` using existing
`PanelController` APIs (read `PanelController.kt` — use the same toggle
Plan 011 uses for "Voice panel").

**Verify**:
```
rg -n "KeyType.MIC|onMicTap|startRecording" ime/src/main/java/com/gallopkeyboard/ime/
```
→ IME mic path no longer starts `DictationService` recording from the
keyboard mic key.

### Step 4: Inventory + verify

Append Plan 016 notes (dispose cancel + which mic product decision).

**Verify**: `bash scripts/verify.sh` → `OK`.

## Test plan

- `GestureFsmTest` / new helper test for cancel cleanup
- `KeyboardLayoutTest` updated if layouts change
- Manual: start toolbar Voice recording → switch to typing panel /
  hide IME → confirm no continued partial commits; mic indicator off

## Done criteria

- [ ] `SmartVoiceButton` dispose cancels active `AudioSession` via
      `transcriber.onSessionCancel`
- [ ] IME keyboard exposes a **single** hybrid voice entry product
      (MIC routes to panel **or** mic key removed)
- [ ] `bash scripts/verify.sh` → `OK`
- [ ] Inventory + README status updated
- [ ] No out-of-scope files modified

## STOP conditions

- `PanelController` cannot open voice panel from MIC without a large
  UI rewrite — report and ask whether to remove the mic key instead
- `onSessionCancel` + dispose races with Plan 014's async frame queue
  and you cannot serialize cancel after in-flight frames — coordinate
  with 014 or land dispose after 014
- Drift: SmartVoiceButton no longer owns sessions as shown

## Maintenance notes

- Reviewers: ensure cancel is idempotent; watch for double
  `engine.cancel()` crashes
- Companion app dictation screens remain on `DictationService` by design
- Future: consider deleting IME→DictationService bind entirely in a
  later plan once companion flows are migrated (out of scope here)
