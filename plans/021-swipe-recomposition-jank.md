# Plan 021: Stop full-keyboard recomposition on every swipe MOVE

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 86dfd89..HEAD -- ime/src/main/java/com/gallopkeyboard/ime/ui/KeyboardView.kt ime/src/main/java/com/gallopkeyboard/ime/ui/KeyRow.kt ime/src/main/java/com/gallopkeyboard/ime/ui/KeyButton.kt ime/src/main/java/com/gallopkeyboard/ime/ui/SwipeTypingController.kt ime/src/test/java/com/gallopkeyboard/ime/ui/SwipeTypingControllerTest.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED
- **Depends on**: none
- **Category**: perf
- **Planned at**: commit `86dfd89`, 2026-07-20

## Why this matters

Swipe typing (Plan 013) bumps `gestureTick` on **every** pointer MOVE and
reads that tick in composition, forcing the **entire** key grid to recompose
at ~60–120 Hz. Combined with per-key `isPressedKey` / `isKeyHighlighted`
reads in `KeyRow`, this is the hottest typing-path jank on Galaxy S22 and
makes swipe feel lackluster even when the gesture logic is correct.

## Current state

```kotlin
 // KeyboardView.kt — inside pointer move loop
 swipeController.onPointerMove(change.position)
 gestureTick++   // every MOVE

 // later in composition:
 @Suppress("UNUSED_VARIABLE")
 val recompositionTrigger = gestureTick  // forces full tree recomposition

 // KeyRow.kt
 isExternallyPressed = swipeController?.isPressedKey(key) == true,
 isSwipeHighlighted = swipeController?.isKeyHighlighted(key) == true,
```

`SwipeTypingController` already tracks `pathKeys`, pressed key, accent popup
state — but UI discovery is via recomposing everything.

**Conventions**: Compose state (`mutableStateOf` / `mutableStateListOf` /
snapshot state). Keep accent popup geometry from Plan 017
(`AccentPopupGeometry`) intact. Match existing swipe tests.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Swipe tests | `./gradlew :ime:testDebugUnitTest --tests '*Swipe*'` | BUILD SUCCESSFUL |
| All ime tests | `./gradlew :ime:testDebugUnitTest` | BUILD SUCCESSFUL |
| Verify | `bash scripts/verify.sh` | `OK` |

## Scope

**In scope**:
- `ime/src/main/java/com/gallopkeyboard/ime/ui/KeyboardView.kt`
- `ime/src/main/java/com/gallopkeyboard/ime/ui/KeyRow.kt` (only if needed for
  narrower state)
- `ime/src/main/java/com/gallopkeyboard/ime/ui/KeyButton.kt` (only if highlight
  APIs change)
- `ime/src/main/java/com/gallopkeyboard/ime/ui/SwipeTypingController.kt`
- Swipe-related unit tests
- `docs/dictus-inventory.md` Plan 021 additions
- `plans/README.md`

**Out of scope**:
- Swipe **decoder** quality / dictionary lookup (Plan 023)
- Spatial hit-test index (PERF-08) — optional micro-opt only if trivial
- Accent commit logic (Plan 017 already done)
- Voice / ASR performance

## Git workflow

- Branch: `advisor/021-swipe-recomposition-jank`
- Commit: `perf(ime): narrow swipe UI invalidation off gestureTick`
- Do NOT push/PR unless instructed.

## Steps

### Step 1: Make SwipeTypingController expose Compose-observable discrete state

Refactor `SwipeTypingController` so UI-relevant fields are snapshot state:

Suggested fields (names may match existing private vars — convert them):

- `pathHighlightKeys: Set<KeyDefinition>` or `SnapshotStateList` — keys to
  highlight; update **only when membership changes** (append/remove), not on
  every MOVE that stays on the same key.
- `pressedKey: KeyDefinition?`
- `accentPopupVisible: Boolean`
- `highlightedAccentIndex: Int?`
- `accentPopupKey: KeyDefinition?`

Keep hit-testing / path logic in the controller. `onPointerMove` should:

1. Hit-test.
2. If same key as last → **return without notifying** (no state write).
3. If new key → update path + highlight set (state write → recompose).
4. Accent index: write state only when the index **value** changes.

Do **not** use a monotonic tick for UI.

**Verify**: unit tests that call `onPointerMove` repeatedly inside the same
key bounds do not change highlight set identity/size incorrectly.
`./gradlew :ime:testDebugUnitTest --tests '*SwipeTypingController*'` → pass.

### Step 2: Remove gestureTick from KeyboardView

1. Delete `gestureTick` / `recompositionTrigger`.
2. Drive `KeyRow` / `KeyButton` from controller state getters that read
   snapshot state (so only keys whose inputs change recompose — Compose
   should skip stable siblings if `KeyButton` params are stable).
3. Still call `updateKeyBounds` when layout coordinates change (not on MOVE).

Optional: pass `highlightedKeys: Set<KeyDefinition>` as a remembered derived
value from the controller if that helps stability.

**Verify**: `./gradlew :ime:compileDebugKotlin` → success.

### Step 3: Stabilize KeyButton inputs

Ensure `isSwipeHighlighted` / `isExternallyPressed` / accent overrides only
change when that key is affected. Avoid passing the whole controller into
every key if that captures unstable identity — prefer boolean/index params
(already the case in `KeyRow`).

**Verify**: `./gradlew :ime:testDebugUnitTest --tests '*Swipe*'` → pass.

### Step 4: Inventory + verify

Document the invalidation model in `docs/dictus-inventory.md`. Update plans
README. Run `bash scripts/verify.sh`.

## Test plan

- Existing swipe path / accent geometry tests remain green.
- New tests: MOVE within same key does not grow `pathKeys`; crossing keys
  appends once; accent index updates only on cell change.
- Manual on S22: swipe across QWERTY — trail highlight updates, no visible
  stutter; long-press accent still works (Plan 017).

## Done criteria

- [ ] No `gestureTick++` on MOVE in `KeyboardView`
- [ ] Highlight/press/accent UI updates via discrete state changes only
- [ ] Swipe + accent unit tests pass
- [ ] `bash scripts/verify.sh` → `OK`
- [ ] Scope respected

## STOP conditions

- Removing the tick breaks accent popup positioning and the fix needs
  `Popup`/`PopupPositionProvider` rewrite — stop and report.
- Controller cannot use Compose runtime state because it is constructed
  outside composition — then hold a small `SwipeUiState` class with
  `mutableStateOf` created in `KeyboardView` via `remember`, and have the
  controller update that object; do not invent a global tick again.
- Decoder / dictionary changes creep in — defer to Plan 023.

## Maintenance notes

- Plan 023 will call dictionary resolve on swipe end only — keep MOVE path
  allocation-free where possible.
- Reviewer: profile or visually confirm; also watch for missing highlight when
  path revisits a key (membership vs order).
