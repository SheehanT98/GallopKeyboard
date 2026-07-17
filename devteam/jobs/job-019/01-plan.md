# Plan 017: Fix accent popup double-commit and swipe accent hit-testing

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
>   ime/src/main/java/com/gallopkeyboard/ime/ui/KeyButton.kt \
>   ime/src/main/java/com/gallopkeyboard/ime/ui/AccentPopup.kt \
>   ime/src/main/java/com/gallopkeyboard/ime/ui/SwipeTypingController.kt \
>   ime/src/main/java/com/gallopkeyboard/ime/ui/KeyboardView.kt \
>   ime/src/test/java/com/gallopkeyboard/ime/ui/SwipeTypingControllerTest.kt
> ```
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P2
- **Effort**: S
- **Risk**: MED (touch UX regressions)
- **Depends on**: none (independent of 014–016)
- **Category**: bug
- **Planned at**: commit `3571aab`, 2026-07-17
- **Issue**: —

## Why this matters

Long-press accents can insert the same character twice, and swipe-mode
accent selection highlights the wrong cell because hit-testing uses the
**key** bounds instead of the **popup strip** geometry that `KeyButton`
already computes correctly for the non-swipe path.

Plan 013's review explicitly called out accent geometry risk; this plan
closes that gap.

After this plan: selecting an accent commits exactly once; swipe-path
accent index math matches the visible popup cells (44.dp wide, centered
with clamp shift).

## Current state

### Double-commit path

`AccentPopup.kt` cells are `clickable { onAccentSelected(accent) }`.

`KeyButton.kt` still wires that callback **and** commits on pointer
release when `highlightedAccentIndex` is set:

```kotlin
AccentPopup(
    accents = accentChars,
    highlightedIndex = effectiveHighlightedAccentIndex,
    onAccentSelected = { accent ->
        showAccentPopup = false
        isPressed = false
        highlightedAccentIndex = null
        currentOnAccentSelected.value?.invoke(accent)
    },
)
```

and on release:

```kotlin
if (selectedAccent != null) {
    currentOnAccentSelected.value?.invoke(selectedAccent)
} else {
    currentOnPress.value()
}
```

With `externalGesturesEnabled` (LETTERS swipe path), `KeyboardView`
commits `SwipeTypingResult.Accent` on finger up while the same
`AccentPopup` `clickable` remains active — a second commit channel.

### Geometry mismatch (swipe path)

`KeyButton.resolveAccentIndex` (non-swipe) uses popup geometry:

```kotlin
val popupLeftPx = (keyWidthPx - popupWidthPx) / 2f + accentShiftPx
val index = ((pointerX - popupLeftPx) / accentCellWidthPx).toInt()
```

with `accentCellWidthPx = 44.dp`.

`SwipeTypingController.resolveAccentIndex` uses **key** bounds width
divided by accent count:

```kotlin
val accentCellWidthPx = bounds.width / accents.size.coerceAtLeast(1)
val index = ((pointerX - bounds.left) / accentCellWidthPx).toInt()
```

That does not match the 44.dp popup cells or horizontal clamp shift.

### Exemplars

- `ime/src/test/.../ui/SwipeTypingControllerTest.kt` — extend this
- Keep Compose visual styling; do not redesign the popup look

## Commands you will need

| Purpose | Command | Expected |
|---------|---------|----------|
| Swipe/UI unit tests | `./gradlew :ime:testDebugUnitTest --tests 'com.gallopkeyboard.ime.ui.*'` | SUCCESS |
| Full verify | `bash scripts/verify.sh` | `OK` |

## Scope

**In scope**:

- `ime/src/main/java/com/gallopkeyboard/ime/ui/AccentPopup.kt`
- `ime/src/main/java/com/gallopkeyboard/ime/ui/KeyButton.kt`
- `ime/src/main/java/com/gallopkeyboard/ime/ui/SwipeTypingController.kt`
- `ime/src/main/java/com/gallopkeyboard/ime/ui/KeyboardView.kt`
  (only if needed to pass popup geometry / suppress click commits)
- `ime/src/test/java/com/gallopkeyboard/ime/ui/SwipeTypingControllerTest.kt`
- Optional small shared helper file under `ime/.../ui/` for accent index
  math used by both KeyButton and SwipeTypingController
- `docs/dictus-inventory.md` — Plan 017 additions
- `plans/README.md` — status

**Out of scope**:

- Swipe path-to-word algorithm / dictionary ranking
- Changing long-press duration (400 ms)
- Theme / key sizing redesign
- Suggestion bar behavior

## Git workflow

- Branch: `cursor/fix-accent-popup-commit-geometry-1534`
- Commit style: `fix(ime): single accent commit; align swipe hit-test`
- Do NOT push/PR unless instructed

## Steps

### Step 1: Single commit channel for accents

Pick **one** commit mechanism and disable the other:

**Recommended default:**

- Keep **pointer-up / swipe-controller** commit as the source of truth
  (works for slide-to-select without a separate tap).
- Remove `clickable` from `AccentPopup` cells **or** make
  `onAccentSelected` optional/no-op when used under external gestures /
  KeyButton long-press (pass a no-op and delete the clickable modifier).
- Ensure KeyButton's non-swipe path does not fire both clickable and
  release commit: if clickable remains for accessibility, clear
  `highlightedAccentIndex` and set a `committed` flag so release is a
  no-op.

Acceptance: one `onAccentSelected` / `SwipeTypingResult.Accent` per
gesture.

**Verify**: `rg -n "onAccentSelected|clickable" ime/src/main/java/com/gallopkeyboard/ime/ui/AccentPopup.kt ime/src/main/java/com/gallopkeyboard/ime/ui/KeyButton.kt` → only one live commit path per gesture mode.

### Step 2: Align swipe accent geometry with popup

Update `SwipeTypingController` so accent indexing uses the same model as
`KeyButton`:

- Cell width = `44.dp` in px (pass density/`accentCellWidthPx` into the
  controller constructor or `onLongPressThreshold` setup — KeyboardView
  already has density).
- Popup left = centered on key bounds + clamp shift into parent width
  (reuse the clamp idea from KeyButton's `accentShiftPx`; parent width
  can be the keyboard column width from `columnCoordinates.size`).

Extract a shared pure function if it reduces duplication, e.g.:

```kotlin
fun resolveAccentIndex(
    pointerX: Float,
    keyLeft: Float,
    keyWidth: Float,
    accentCount: Int,
    accentCellWidthPx: Float,
    accentShiftPx: Float,
): Int?
```

Place it next to the controller or in a tiny `AccentPopupGeometry.kt`.

Wire `KeyboardView` to supply screen/column width and density-derived
cell width when constructing/updating the controller.

**Verify**: unit tests below cover indices at left/middle/right cells.

### Step 3: Tests

Extend `SwipeTypingControllerTest.kt`:

1. Long-press accent + move to rightmost cell → `Accent` equals last
   char when pointer X maps to last 44.dp cell (not key-relative
   fractions).
2. Regression: swipe word path still works when movement exceeds slop
   before long-press.
3. If you add the shared geometry function, table-drive 3 indices.

**Verify**:
```
./gradlew :ime:testDebugUnitTest --tests 'com.gallopkeyboard.ime.ui.SwipeTypingControllerTest'
```

### Step 4: Inventory + full verify

**Verify**: `bash scripts/verify.sh` → `OK`.

## Test plan

- Automated: geometry + no double commit (if you can assert commit count
  via a recording callback in KeyboardView tests — optional; controller
  tests are enough for geometry)
- Manual: long-press `e` → slide to `é` → release once; LETTERS swipe
  long-press same; confirm single character inserted

## Done criteria

- [ ] Accent selection commits exactly once per gesture
- [ ] Swipe-path accent index uses 44.dp cells + center/clamp shift
      consistent with the popup
- [ ] `SwipeTypingControllerTest` covers accent index geometry
- [ ] `bash scripts/verify.sh` → `OK`
- [ ] Inventory + README updated; no out-of-scope edits

## STOP conditions

- Popup is reparented / measured differently so shared math cannot match
  without Compose layout coordinates — report with a proposed
  `onGloballyPositioned` approach before inventing a large new system
- Drift: Plan 013 swipe controller removed or accents no longer supported

## Maintenance notes

- Reviewers: compare `KeyButton` and `SwipeTypingController` math side
  by side in the PR
- Any future change to accent cell size (`44.dp`) must update the shared
  constant once
