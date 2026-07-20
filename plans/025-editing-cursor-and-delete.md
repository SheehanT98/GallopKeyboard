# Plan 025: Code-point delete, space-bar cursor drag, and word-delete accelerate

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat bfc7085..HEAD -- ime/src/main/java/com/gallopkeyboard/ime/DictusImeService.kt ime/src/main/java/com/gallopkeyboard/ime/ui/KeyboardScreen.kt ime/src/main/java/com/gallopkeyboard/ime/ui/KeyButton.kt ime/src/main/java/com/gallopkeyboard/ime/ui/KeyboardView.kt ime/src/main/java/com/gallopkeyboard/ime/model/KeyType.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.
>
> **Assumption**: Plans 019–023 merged (suggestion bar may add IME height;
> do not shrink key hit targets further without measuring).

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED
- **Depends on**: none (Plan 019 may have touched `KeyboardScreen` signature —
  rebase carefully)
- **Category**: direction / bug
- **Planned at**: commit `bfc7085`, 2026-07-20

## Why this matters

Daily editing still feels basic vs Gboard: backspace uses UTF-16 units (breaks
emoji), space cannot slide the cursor, and delete only repeats single
characters. These are high muscle-memory gaps once suggestions/swipe (Phase 8)
exist.

## Current state

```kotlin
// DictusImeService.kt
fun deleteBackward() {
    currentInputConnection?.deleteSurroundingText(1, 0)
}

// KeyboardScreen.kt — SPACE
KeyType.SPACE -> {
    // double-tap → ". " only; no drag
    onCommitText(" ")
}

// KeyButton.kt — DELETE key-repeat every 50ms after 400ms, still onDeleteBackward()
```

No `setSelection` usage in IME production sources for cursor gestures.

**Conventions**: Compose pointerInput; keep swipe on LETTERS character keys
unchanged (Plan 013/021). English-only. Match existing `KeyButton` delete
repeat structure.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| IME tests | `./gradlew :ime:testDebugUnitTest` | BUILD SUCCESSFUL |
| Verify | `bash scripts/verify.sh` | `OK` |

## Scope

**In scope**:
- `ime/.../DictusImeService.kt` — delete APIs + selection helpers
- `ime/.../ui/KeyboardScreen.kt` — wire callbacks
- `ime/.../ui/KeyButton.kt` and/or `KeyboardView.kt` — space drag + delete
  accelerate
- New pure helpers + unit tests (e.g. `EditorEditHelpers.kt`)
- `docs/dictus-inventory.md` Plan 025 additions
- `plans/README.md`

**Out of scope**:
- Autocorrect-on-space (Plan 026) — space drag must not conflict; if Plan 026
  lands first, coordinate that drag wins over autocorrect when movement >
  slop
- Number row (separate direction)
- Swipe letter gestures
- Changing ADR-0003 voice gestures

## Git workflow

- Branch: `advisor/025-editing-cursor-and-delete`
- Commit: `feat(ime): code-point delete, space cursor drag, word delete`
- Do NOT push/PR unless instructed.

## Steps

### Step 1: Code-point (and cluster-safe) deleteBackward

```kotlin
fun deleteBackward() {
    val ic = currentInputConnection ?: return
    if (!ic.deleteSurroundingTextInCodePoints(1, 0)) {
        ic.deleteSurroundingText(1, 0)
    }
}
```

Add `deleteBackwardWord()`:

```kotlin
fun deleteBackwardWord() {
    val ic = currentInputConnection ?: return
    val before = ic.getTextBeforeCursor(64, 0)?.toString().orEmpty()
    if (before.isEmpty()) return
    val deleteCount = countCharsToDeleteForWord(before) // pure
    if (deleteCount > 0) ic.deleteSurroundingText(deleteCount, 0)
}
```

Pure helper: trim trailing spaces then delete back to previous whitespace.
Unit-test with ASCII and a surrogate-pair emoji string lengths carefully —
`deleteSurroundingText` counts UTF-16 code units; document that word delete
uses `before.length` index math on the Java string (UTF-16), which is OK for
word boundaries on spaces.

**Verify**: `EditorEditHelpersTest` + compile.

### Step 2: Accelerate DELETE to word after N repeats

In `KeyButton` delete repeat loop (or KeyboardScreen handler):

- First press + early repeats: `onDeleteBackward()` (code-point).
- After **~8** char deletes **or** after hold > **900 ms**, switch to
  `onDeleteBackwardWord()` per tick (or every other tick) until release.

Expose `onDeleteBackwardWord` from `KeyboardScreen` → service.

**Verify**: pure policy function `fun deleteMode(repeatIndex: Int, heldMs: Long): CharOrWord` tested.

### Step 3: Space-bar horizontal cursor drag

On SPACE key (not character swipe layer):

1. `pointerInput`: on down, record x and whether movement exceeds slop (~8–12 dp).
2. If release with movement < slop → existing tap / double-tap period behavior.
3. If dragging: for each move, compute `dx` in chars approximately using
   `dx / (keyWidth/8)` or fixed px-per-char; call
   `ic.setSelection(newPos, newPos)` based on
   `getTextBeforeCursor`/`getTextAfterCursor` lengths.
4. Do **not** commit spaces while dragging.

Implement selection math in a pure helper for tests:

```kotlin
fun cursorOffsetAfterDrag(beforeLen: Int, afterLen: Int, deltaChars: Int): Int
```

Wire `onSpaceCursorDrag(deltaChars: Int)` from UI to
`DictusImeService.moveCursor(deltaChars)`.

**Verify**: helper unit tests; `./gradlew :ime:testDebugUnitTest` → pass.

### Step 4: Docs + verify

Inventory + `bash scripts/verify.sh`.

## Test plan

- Code-point delete prefers `deleteSurroundingTextInCodePoints`.
- Word-delete counts for `"hello world "` → deletes spaces then `world`.
- Cursor drag clamps to `[0, before+after]`.
- Manual: emoji backspace; slide on space in Gmail; hold delete through a
  sentence.

## Done criteria

- [ ] `deleteBackward` uses code-point API with fallback
- [ ] Long-hold delete eventually deletes by word
- [ ] Space horizontal drag moves cursor without inserting spaces
- [ ] Double-tap space → `. ` still works when not dragging
- [ ] Unit tests for helpers; `verify.sh` OK
- [ ] Scope respected

## STOP conditions

- Space drag fights Plan 013 swipe because SPACE is incorrectly inside
  character gesture layer — keep SPACE on KeyButton-local gestures only.
- Host app ignores `setSelection` (some WebViews) — document in
  `docs/limitations.md` and keep feature for native editors; don’t invent
  key-event hacks without reporting first.

## Maintenance notes

- Plan 026 autocorrect must treat “space with drag” as non-commit.
- Reviewer: check Samsung Notes + Chrome URL bar behavior.
