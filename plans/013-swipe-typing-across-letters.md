# Plan 013: Swipe typing (write across letters)

> **Executor instructions**: Follow this plan step by step. Verify each
> step. STOP conditions apply. Update `plans/README.md` when done.

## Status

- **Priority**: P2
- **Effort**: L
- **Risk**: MED (touch handling vs key tap conflict)
- **Depends on**: `plans/011-toolbar-voice-and-thin-voice-panel.md`
- **Category**: feature
- **Planned at**: 2026-07-17

## Why this matters

User asked for “writing between letters” — glide/swipe typing across the
letter keys so a path over keys produces a word without lifting the finger
for each letter. Classic phone keyboard expectation.

## Product requirements

1. On **LETTERS** layer only: if the user presses a letter and **drags**
   across other letter keys without releasing, treat as swipe typing.
2. Commit strategy (v1, pragmatic — not full ML decoder):
   - Collect ordered unique keys hit along the path (dedupe consecutive
     duplicates).
   - On finger up, join characters into a candidate string and commit
     with a trailing space (or feed suggestion engine if available).
   - Optional: if `DictionaryEngine` can rank, pick best match for the
     path string; otherwise commit raw path letters.
3. Short taps without significant movement remain normal single-key taps.
4. Must not break long-press accents on character keys.
5. Light theme unchanged.

## Implementation approach

- In `KeyButton` / `KeyRow` / new `SwipeTypingController`:
  - Detect movement beyond a small slop after down on CHARACTER key.
  - Switch from tap mode to swipe mode; highlight keys under pointer.
  - On up: finalize word.
- Coordinate at `KeyboardView` level so the pointer can move across keys
  (parent `pointerInput` or shared drag state) — per-key isolation will
  fail for cross-key swipes.

## STOP conditions

- Shipping an on-device neural swipe model / large new binary.
- Breaking delete key-repeat or shift double-tap.
- Requiring network for decoding.

## Verification

```bash
./gradlew :ime:testDebugUnitTest :ime:assembleDebug
```

Unit-test the path → string helper (dedupe consecutive, ignore non-letter).

## Done when

- [ ] Swipe across letters commits a word
- [ ] Tap typing and accents still work
- [ ] Tests for path helper pass
- [ ] README updated
