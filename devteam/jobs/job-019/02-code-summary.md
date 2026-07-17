# Code summary — job-019 / Plan 017

## Summary

Fixed accent popup double-commit and aligned swipe-path accent hit-testing with the
visible 44.dp popup strip geometry.

## Changes

### Single commit channel

- Removed `clickable` and `onAccentSelected` from `AccentPopup` — cells are
  display/highlight only.
- Non-swipe (`KeyButton`) and swipe (`SwipeTypingController` → `KeyboardView`)
  paths commit accents exclusively on pointer release.

### Shared geometry

- Added `AccentPopupGeometry.kt` with `ACCENT_CELL_WIDTH_DP`, `computeAccentShiftPx`,
  and `resolveAccentIndex`.
- `KeyButton` and `SwipeTypingController` both use the shared helpers.
- `KeyboardView` passes density-derived cell width and column width into the
  swipe controller.

### Tests

- Extended `SwipeTypingControllerTest` with rightmost-cell accent selection,
  swipe-before-long-press regression, and table-driven geometry cases.

## Files changed

| File | Why |
|------|-----|
| `ime/.../ui/AccentPopupGeometry.kt` | **New** — shared accent index math |
| `ime/.../ui/AccentPopup.kt` | Remove clickable commit path |
| `ime/.../ui/KeyButton.kt` | Shared geometry; release-only commit |
| `ime/.../ui/SwipeTypingController.kt` | 44.dp cells + clamp shift |
| `ime/.../ui/KeyboardView.kt` | Wire geometry params to controller |
| `ime/src/test/.../SwipeTypingControllerTest.kt` | Geometry + regression tests |
| `docs/dictus-inventory.md` | Plan 017 inventory |
| `plans/README.md` | Plan 017 → DONE |

## Verification

```
./gradlew :ime:testDebugUnitTest --tests 'com.gallopkeyboard.ime.ui.SwipeTypingControllerTest'  → SUCCESS (6 tests)
bash scripts/verify.sh  → OK
```

## Blockers

None.
