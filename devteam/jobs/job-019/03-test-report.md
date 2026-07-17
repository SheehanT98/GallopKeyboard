# Job 019 — Test report

| Field | Value |
|-------|-------|
| **Job** | job-019 |
| **Branch** | `cursor/devteam-job-019-execute-plan-017-fix-accent-popup-commit-and-geo-c1fc` |
| **PR** | [#42](https://github.com/SheehanT98/GallopKeyboard/pull/42) |
| **Plan** | `plans/017-fix-accent-popup-commit-and-geometry.md` |
| **Tester** | devteam-tester (composer-2.5) |
| **Tested at** | 2026-07-17T16:34:49Z |
| **SHA tested** | `a24e9f23e1b20997241ec6178e85826bb0164e0d` |
| **Verdict** | **PASS** |

## Environment

| Check | Command | Result |
|-------|---------|--------|
| Branch | `git branch --show-current` | `cursor/devteam-job-019-execute-plan-017-fix-accent-popup-commit-and-geo-c1fc` |
| Job status | `devteam/jobs/job-019/meta.json` | `testing` |
| Base | `origin/main...HEAD` | 8 product files + job artifacts |

## Commands run

| Check | Command | Result |
|-------|---------|--------|
| Swipe controller tests | `./gradlew :ime:testDebugUnitTest --tests 'com.gallopkeyboard.ime.ui.SwipeTypingControllerTest'` | BUILD SUCCESSFUL (6 tests) |
| UI unit tests | `./gradlew :ime:testDebugUnitTest --tests 'com.gallopkeyboard.ime.ui.*'` | BUILD SUCCESSFUL |
| Full verify | `bash scripts/verify.sh` | exit 0, ends with `OK` |
| Single commit path | `rg -n "clickable\|onAccentSelected" ime/src/main/java/com/gallopkeyboard/ime/ui/AccentPopup.kt ime/src/main/java/com/gallopkeyboard/ime/ui/KeyButton.kt` | No `clickable` in `AccentPopup.kt`; `onAccentSelected` only on KeyButton release path |
| Product diff | `git diff --name-only origin/main...HEAD` | Scope-compliant: `AccentPopup.kt`, `AccentPopupGeometry.kt`, `KeyButton.kt`, `SwipeTypingController.kt`, `KeyboardView.kt`, tests, inventory, plans index |

## Done criteria (Plan 017)

| Criterion | Result |
|-----------|--------|
| Accent selection commits exactly once per gesture | **PASS** — `AccentPopup` is display-only; KeyButton and SwipeTypingController commit on pointer release only |
| Swipe-path accent index uses 44.dp cells + center/clamp shift | **PASS** — shared `AccentPopupGeometry.kt`; `KeyboardView` wires `accentCellWidthPx` and `parentWidthPx` |
| `SwipeTypingControllerTest` covers accent index geometry | **PASS** — rightmost cell, slop-before-long-press regression, table-driven left/middle/right |
| `bash scripts/verify.sh` → `OK` | **PASS** |
| Inventory + README updated | **PASS** — `docs/dictus-inventory.md` Plan 017; `plans/README.md` row 017 → `DONE` |
| No out-of-scope edits | **PASS** — swipe algorithm, theme, suggestion bar untouched |

## STOP conditions

| Condition | Outcome |
|-----------|---------|
| Popup reparented / measured differently so shared math cannot match | **Not hit** — shared geometry uses key bounds + parent width clamp |
| Drift: swipe controller removed or accents no longer supported | **Not hit** — accents still wired through `SwipeTypingController` |

## Blockers

None.

## Advance

`npm run devteam:advance -- job-019 --to reviewing`
