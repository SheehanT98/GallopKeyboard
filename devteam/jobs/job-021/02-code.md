# Job-021 code — Plan 031

## Implementation notes

### Step 1 — Replace tick with highlight state

- Added `GestureUiState` (`highlightedKeys`, `pressedKey`, `accentPopupKey`,
  `highlightedAccentIndex`).
- `KeyboardView` calls `syncGestureUi()` after each `SwipeTypingController`
  mutation; assigns only when `gestureUiStateIfChanged` returns non-null.
- Deleted all `gestureTick++` and `recompositionTrigger`.
- `refreshKeyBounds()` runs from `onCharacterBoundsChanged`, not every composition.

### Step 2 — KeyRow / KeyButton props

- `KeyRow` takes `gestureUi: GestureUiState?` instead of `swipeController`.
- Per-key booleans derived from stable set membership; accent index passed only
  to the popup key so other keys skip recomposition on accent drag.

### Step 3 — Accent popup

- Long-press still drives `accentPopupKey` / `highlightedAccentIndex` via
  controller snapshot; no `gestureTick` side channel.
- Existing `SwipeTypingControllerTest` accent cases remain green.

## Tests added

`GestureUiStateTest`:

- `gestureUiStateIfChanged` skips equal states
- `snapshotGestureUi` pressed key before slop; highlights after swipe activates
