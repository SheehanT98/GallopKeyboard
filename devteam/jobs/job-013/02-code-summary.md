# Job 013 — Code summary

## What shipped

Swipe typing on the **LETTERS** layer: press on a letter, drag across other letter keys, release to commit the path as a word plus trailing space.

## Approach

- **`SwipePathHelper`**: pure path → string (filter non-letters, dedupe consecutive duplicates).
- **`SwipeTypingController`**: hit-testing, swipe vs tap vs accent long-press resolution.
- **`KeyboardView`**: parent-level `pointerInput` on LETTERS layer so the finger can cross key boundaries; character keys delegate gestures externally.
- **`KeyButton` / `KeyRow`**: external gesture mode, swipe highlight, accent popup driven by controller state.
- **`KeyboardScreen`**: `onSwipeWord` commits via `onCommitText` and auto-unshifts when appropriate.
- Optional dictionary assist: if a `SuggestionEngine` is wired, first prefix suggestion replaces the raw path when length ≥ 2 (not passed from screen in v1).

## Files changed

| File | Change |
|------|--------|
| `ime/.../ui/SwipePathHelper.kt` | New path helper |
| `ime/.../ui/SwipeTypingController.kt` | New gesture controller |
| `ime/.../ui/KeyboardView.kt` | LETTERS-layer swipe handler + bounds |
| `ime/.../ui/KeyRow.kt` | Bounds reporting + controller wiring |
| `ime/.../ui/KeyButton.kt` | External gestures, swipe highlight |
| `ime/.../ui/KeyboardScreen.kt` | Swipe word commit |
| `ime/.../ui/SwipePathHelperTest.kt` | Unit tests |
| `ime/.../ui/SwipeTypingControllerTest.kt` | Unit tests |
| `plans/013-swipe-typing-across-letters.md` | Done checklist |
| `plans/README.md` | Plan 013 → DONE |

## Verification

```bash
source scripts/android-env.sh && ./gradlew :ime:testDebugUnitTest :app:assembleDebug
```

110 IME unit tests pass (7 new swipe tests).

## Preserved behavior

- Short taps on letters still commit a single character.
- Long-press accent popup still works (swipe mode does not activate while popup is open).
- DELETE key-repeat and shift double-tap unchanged (non-character keys keep local gestures).
