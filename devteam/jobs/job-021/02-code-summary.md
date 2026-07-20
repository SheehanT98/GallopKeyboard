# Job-021 code summary — Plan 031

## What changed

Removed `gestureTick` full-keyboard invalidation on swipe MOVE. Swipe and
accent UI now use hoisted `GestureUiState` that updates Compose only when
highlight membership or accent selection changes.

## Files changed

| File | Why |
|------|-----|
| `ime/.../ui/GestureUiState.kt` | Discrete UI state + `gestureUiStateIfChanged` helper |
| `ime/.../ui/SwipeTypingController.kt` | `snapshotGestureUi()` after pointer mutations |
| `ime/.../ui/KeyboardView.kt` | Removed `gestureTick`; sync gesture UI; bounds refresh on layout only |
| `ime/.../ui/KeyRow.kt` | Per-key props from `GestureUiState` instead of controller reads |
| `ime/.../ui/GestureUiStateTest.kt` | Equality skip + snapshot tests |
| `docs/dictus-inventory.md` | Plan 031 inventory section |
| `plans/README.md` | Plan 031 marked DONE |

## Verification

- `rg -n 'gestureTick' ime/src/main` — no matches
- `./gradlew :ime:testDebugUnitTest --tests '*Swipe*' --tests '*GestureUi*'` — BUILD SUCCESSFUL
- `./gradlew :ime:testDebugUnitTest` — BUILD SUCCESSFUL (via verify.sh)
- `bash scripts/verify.sh` — OK

## Drift check note

Plan drift anchor `32b0d20` is not in this repo history. Code matched plan
excerpts on current main; proceeded without STOP.

## Out of scope (unchanged)

SwipeWordResolver / dwell (Plan 032), spatial index rewrite, accent popup redesign.
