# Plan 031: Stop full-keyboard recomposition on swipe MOVE (finish 021)

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 32b0d20..HEAD -- ime/src/main/java/com/gallopkeyboard/ime/ui/KeyboardView.kt ime/src/main/java/com/gallopkeyboard/ime/ui/KeyRow.kt ime/src/main/java/com/gallopkeyboard/ime/ui/KeyButton.kt ime/src/main/java/com/gallopkeyboard/ime/ui/SwipeTypingController.kt`
> If excerpts mismatch, STOP.
>
> **Supersedes incomplete Plan 021**: marked DONE; `gestureTick++` still on
> every MOVE.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED
- **Depends on**: none
- **Category**: perf
- **Planned at**: commit `32b0d20`, 2026-07-20

## Why this matters

Every swipe MOVE increments `gestureTick`, which is read during composition to
force the **entire** QWERTY tree to recompose at pointer rate (~60–120 Hz).
Each key re-queries press/highlight state. This is the dominant typing-path
jank risk on Galaxy S22 (CONTEXT acceptance #1/#3) and was the whole point of
Plan 021 — which never landed.

## Current state

```kotlin
// KeyboardView.kt
var gestureTick by remember { mutableIntStateOf(0) }
// on MOVE:
gestureTick++
// composition:
val recompositionTrigger = gestureTick  // forces full tree rebuild

// KeyRow / KeyButton still re-read highlight membership per key each pass
```

Plan 021 done criteria required: no MOVE tick; discrete highlight state.

**Conventions**: Compose state hoisting; keep swipe hit-test on CHARACTER keys
only (Plan 013/017). Match existing `mutableStateMapOf` for key bounds.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| IME tests | `./gradlew :ime:testDebugUnitTest --tests '*Swipe*'` | BUILD SUCCESSFUL |
| Full IME | `./gradlew :ime:testDebugUnitTest` | BUILD SUCCESSFUL |
| Verify | `bash scripts/verify.sh` | `OK` |

## Scope

**In scope**:
- `ime/.../ui/KeyboardView.kt` — remove `gestureTick`; discrete highlight state
- `ime/.../ui/KeyRow.kt` / `KeyButton.kt` — only recompose affected keys if
  needed (pass stable highlight set / per-key flag)
- Tests asserting controller highlight updates without requiring tick
- `docs/dictus-inventory.md` Plan 031
- `plans/README.md`

**Out of scope**:
- SwipeWordResolver / dwell (032)
- Spatial index rewrite beyond what’s needed for highlights
- Accent popup redesign (must keep working without tick — see STOP)

## Git workflow

- Branch: `cursor/031-stop-swipe-recomposition-jank`
- Commit: `perf(ime): remove gestureTick; discrete swipe highlights`
- Do NOT push/PR unless instructed.

## Steps

### Step 1: Replace tick with highlight state

1. Hold `var highlightedKeys by remember { mutableStateOf<Set<KeyDefinition>>(emptySet()) }`
   (or `mutableStateSetOf` pattern) updated only when membership **changes**.
2. On MOVE: compute new highlight set from `SwipeTypingController`; if equal
   to previous, **do not** assign (no recomposition).
3. Delete all `gestureTick++` and the `recompositionTrigger` read.
4. Rebuild `keyBoundsMap` only from `onGloballyPositioned` / layout changes —
   not on every MOVE (if currently tied to tick-driven composition).

**Verify**: `rg -n 'gestureTick' ime/src/main` → no matches.

### Step 2: KeyRow/KeyButton props

Pass `isSwipeHighlighted: Boolean` (or membership check against a stable
`Set` that only changes on set edits). Avoid allocating new lists every frame
in composition (`KeyboardView` bounds list rebuild).

**Verify**: `./gradlew :ime:testDebugUnitTest --tests '*Swipe*'` → pass.

### Step 3: Accent popup still invalidates correctly

Long-press accent strip must still update without relying on gestureTick.
If accent used the tick as a side channel, drive it from accent index state
instead.

**Verify**: existing accent-related tests green; manual note in inventory.

### Step 4: Docs + verify

Inventory + `bash scripts/verify.sh`.

## Test plan

- Unit: highlight set equality skips update (pure helper if extracted).
- SwipeTypingController tests remain green.
- Manual: swipe across LETTERS — UI stays smooth; trail/highlight updates.

## Done criteria

- [ ] Zero `gestureTick` in `ime/src/main`
- [ ] Highlights update only on membership change
- [ ] Accent popup still works
- [ ] Swipe tests + `verify.sh` OK
- [ ] Scope respected

## STOP conditions

- Accent selection breaks without tick and fix needs KeyboardScreen redesign —
  report with minimal repro.
- Removing tick regresses swipe commit (wrong key) — fix hit-test, don’t
  restore tick.

## Maintenance notes

- Plan 032 decoder should not reintroduce full-tree invalidation.
- Reviewer: FrameTimeline / feel on S22 during long swipe.
- Mark Plan 021 superseded by 031.
