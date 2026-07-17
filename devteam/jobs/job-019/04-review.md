# Job 019 — Review

| Field | Value |
|-------|-------|
| **Job** | job-019 |
| **Branch** | `cursor/devteam-job-019-execute-plan-017-fix-accent-popup-commit-and-geo-c1fc` |
| **PR** | [#42](https://github.com/SheehanT98/GallopKeyboard/pull/42) |
| **Plan** | `plans/017-fix-accent-popup-commit-and-geometry.md` |
| **Reviewer** | devteam-reviewer (cursor-grok-4.5-high) |
| **Reviewed at** | 2026-07-17T16:35:00Z |
| **SHA reviewed** | `a24e9f2` |
| **Base** | `origin/main...HEAD` |
| **Verdict** | **APPROVE** |

## Summary

Plan 017 fixes accent popup double-commit (clickable + pointer-up both firing) and aligns swipe-path accent hit-testing with the visible 44.dp popup strip. Implementation extracts shared geometry, removes the duplicate commit channel, and adds focused controller tests. Tester evidence is green. **APPROVE**.

## Scope compliance

| In / out of scope | Result |
|-------------------|--------|
| `AccentPopup.kt` — remove clickable commit | **Met** — display/highlight only |
| `KeyButton.kt` — release-only commit + shared geometry | **Met** |
| `SwipeTypingController.kt` — 44.dp cells + clamp shift | **Met** |
| `KeyboardView.kt` — wire geometry params | **Met** — `accentCellWidthPx`, `parentWidthPx` |
| `AccentPopupGeometry.kt` — shared helpers | **Met** |
| `SwipeTypingControllerTest.kt` — geometry + regression | **Met** — 6 tests |
| `docs/dictus-inventory.md` — Plan 017 | **Met** |
| `plans/README.md` — 017 → DONE | **Met** |
| Swipe path-to-word algorithm / theme / suggestion bar | **Untouched** (correct) |

Product diff vs `origin/main` (excluding job artifacts): eight files — new geometry helper, four UI files, test, inventory, plans index.

## Implementation review

### Single commit channel (Step 1)

`AccentPopup` no longer accepts `onAccentSelected` or uses `clickable` — cells are purely visual with highlight state.

`KeyButton` commits on pointer release when `highlightedAccentIndex` resolves to an accent; no parallel clickable path.

`SwipeTypingController` returns `SwipeTypingResult.Accent` on pointer up; `KeyboardView` handles that as the sole swipe commit channel.

### Shared geometry (Step 2)

```35:48:ime/src/main/java/com/gallopkeyboard/ime/ui/AccentPopupGeometry.kt
fun resolveAccentIndex(
    pointerX: Float,
    keyLeftPx: Float,
    keyWidthPx: Float,
    accentCount: Int,
    accentCellWidthPx: Float,
    accentShiftPx: Float,
): Int? {
    if (accentCount <= 0 || keyWidthPx <= 0f) return null
    val popupWidthPx = accentCount * accentCellWidthPx
    val popupLeftPx = keyLeftPx + (keyWidthPx - popupWidthPx) / 2f + accentShiftPx
    val index = ((pointerX - popupLeftPx) / accentCellWidthPx).toInt()
    return index.takeIf { it in 0 until accentCount }
}
```

- `ACCENT_CELL_WIDTH_DP = 44.dp` matches `AccentPopup` cell size.
- `computeAccentShiftPx` clamps popup into parent width — same model as KeyButton's prior inline math.
- Both `KeyButton` and `SwipeTypingController` delegate to the shared function.

### KeyboardView wiring

```73:81:ime/src/main/java/com/gallopkeyboard/ime/ui/KeyboardView.kt
    val accentCellWidthPx = with(density) { ACCENT_CELL_WIDTH_DP.toPx() }
    val swipeController = remember(swipeSlopPx, accentCellWidthPx) {
        SwipeTypingController(swipeSlopPx, accentCellWidthPx)
    }
    swipeController.isShifted = isShifted

    swipeController.parentWidthPx =
        columnCoordinates?.size?.width?.toFloat() ?: view.rootView.width.toFloat()
```

### Tests (Step 3)

- Rightmost 44.dp cell selects last accent (`ê` at X=110 for key E).
- Movement beyond slop before long-press yields `SwipeWord`, not accent.
- Table-driven geometry covers left/middle/right cells via shared `resolveAccentIndex`.

## Done criteria

| Criterion | Result |
|-----------|--------|
| Single accent commit per gesture | **PASS** |
| Swipe geometry matches popup (44.dp + clamp) | **PASS** |
| Controller tests cover geometry | **PASS** |
| `bash scripts/verify.sh` → `OK` | **PASS** (tester) |
| Inventory + README updated | **PASS** |
| No out-of-scope production files | **PASS** |

## Risks for human reviewer

| Risk | Severity | Notes |
|------|----------|-------|
| Touch UX regression on edge keys | Low–Med | Clamp shift changes swipe index at keyboard margins; manual long-press on first/last column keys recommended |
| Coordinate space mismatch | Low | Swipe controller uses key bounds in parent space; `KeyboardView` supplies column width — matches KeyButton model |
| Future 44.dp change | Info | Must update `ACCENT_CELL_WIDTH_DP` and `AccentPopup` together (documented in plan maintenance notes) |
| Accessibility without clickable accents | Info | Slide-to-select still works; no separate tap-to-commit on popup cells (intentional per plan) |

## Advance

`npm run devteam:advance -- job-019 --to double_checking`
