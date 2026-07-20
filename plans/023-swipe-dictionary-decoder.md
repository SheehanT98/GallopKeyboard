# Plan 023: Decode swipe paths with DictionaryEngine (not raw concat)

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 86dfd89..HEAD -- ime/src/main/java/com/gallopkeyboard/ime/ui/SwipePathHelper.kt ime/src/main/java/com/gallopkeyboard/ime/ui/SwipeTypingController.kt ime/src/main/java/com/gallopkeyboard/ime/ui/KeyboardView.kt ime/src/main/java/com/gallopkeyboard/ime/ui/KeyboardScreen.kt ime/src/main/java/com/gallopkeyboard/ime/suggestion/DictionaryEngine.kt ime/src/test/java/com/gallopkeyboard/ime/ui/SwipeTypingControllerTest.kt ime/src/test/java/com/gallopkeyboard/ime/ui/SwipePathHelperTest.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P2
- **Effort**: M
- **Risk**: MED
- **Depends on**: Plan 019 recommended (English dictionary default + shared
  suggestion wiring). May proceed alone if executor injects `dict_en` via
  `DictionaryEngine` / a test double, but product quality assumes 019.
- **Category**: direction
- **Planned at**: commit `86dfd89`, 2026-07-20

## Why this matters

Plan 013 shipped swipe capture, but commit text is still
`SwipePathHelper.pathToWord` (concat + dedupe consecutive). Unit tests even
expect `"helo"` not `"hello"`. `KeyboardView.resolveSwipeWord` can call
`getSuggestions(rawPath)` but **never receives** a live `SuggestionEngine`
from `KeyboardScreen`, so production swipe commits the raw path. That makes
swipe feel gimmicky vs Gboard and reinforces a “lackluster” daily driver.

This plan upgrades end-of-swipe resolution to dictionary-backed candidates
**on-device** (no cloud lexicon — `AGENTS.md`).

## Current state

```kotlin
// SwipeTypingController.onPointerUp — raw path
val word = SwipePathHelper.pathToWord(letters)
if (word.isNotEmpty()) SwipeTypingResult.SwipeWord(word)

 // KeyboardView — optional engine often null
 fun resolveSwipeWord(rawPath: String, suggestionEngine: SuggestionEngine?): String {
     // getSuggestions(rawPath) only if engine non-null
 }

 // KeyboardScreen — onSwipeWord commits word directly; no engine passed
 onSwipeWord = { word -> onCommitText(word) }
```

**Conventions**: Pure Kotlin helpers for scoring (unit-tested). Reuse
`SuggestionEngine` / `DictionaryEngine` from Plan 019. English-only v1.
Offline only.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Swipe + suggestion tests | `./gradlew :ime:testDebugUnitTest --tests '*Swipe*' --tests '*Dictionary*'` | BUILD SUCCESSFUL |
| All ime | `./gradlew :ime:testDebugUnitTest` | BUILD SUCCESSFUL |
| Verify | `bash scripts/verify.sh` | `OK` |

## Scope

**In scope**:
- `ime/src/main/java/com/gallopkeyboard/ime/ui/SwipePathHelper.kt` (extend)
- New helper e.g. `ime/.../ui/SwipeWordResolver.kt` (preferred over bloating
  controller)
- `ime/.../ui/KeyboardView.kt` / `KeyboardScreen.kt` — pass engine / resolver
- `ime/.../DictusImeService.kt` — only if needed to pass `suggestionEngine`
  into `KeyboardScreen`
- Unit tests for resolver
- `docs/dictus-inventory.md` Plan 023 additions
- `plans/README.md`

**Out of scope**:
- Full geometric spatial decoder / neural glide model (spike later if still weak)
- Autocorrect-on-space for tap typing (separate direction)
- Plan 021 recomposition work (independent; do not mix)
- Cloud lexicon / network word lists
- Changing swipe slop / activation thresholds unless required for tests

## Git workflow

- Branch: `advisor/023-swipe-dictionary-decoder`
- Commit: `feat(ime): resolve swipe paths via English dictionary`
- Do NOT push/PR unless instructed.

## Steps

### Step 1: Define a pure SwipeWordResolver

Create `SwipeWordResolver` (or functions on `SwipePathHelper`) with:

```kotlin
fun resolve(
    pathLetters: List<Char>,
    suggestions: (prefix: String, max: Int) -> List<String>,
): String
```

Algorithm (keep it simple and testable — **v1**, not research):

1. `raw = pathToWord(pathLetters)` (existing dedupe).
2. If `raw.length < 2`, return `raw`.
3. Query `suggestions(raw, 5)` — prefix match may help for short paths.
4. Also query with first char only / progressive prefixes if needed to get a
   candidate pool from `DictionaryEngine` (prefix index). If the engine only
   supports prefix API, generate candidates by:
   - Taking `getSuggestions` for `raw` and for `raw.first().toString()`.
   - Optionally: for each dictionary word of length in
     `[raw.length, raw.length + 3]` sharing the same first and last letter as
     `raw`, score it (requires a new `DictionaryEngine` method — **only if
     needed**).

**Preferred minimal approach** (implement this first):

1. Build `raw` via `pathToWord`.
2. Ask engine for suggestions with prefix = first letter of `raw` at
   `maxResults = 20` **or** add `DictionaryEngine.candidatesForSwipe(raw): List<String>`
   that scans the first-letter bucket and filters words where:
   - `word.first() == raw.first()` (casefold)
   - `word.last() == raw.last()`
   - `word.length` between `raw.length` and `raw.length + 4`
   - every letter of `raw` appears in order as a subsequence of `word`
3. Score candidates: subsequence match + length proximity + frequency
   (WordEntry.frequency if available).
4. Return best candidate, else `raw`.

**Verify**: pure unit tests without Android:

- path `h,e,l,o` → prefers `hello` when candidate list includes it
- path that matches no word → returns raw
- empty path → `""`

Put tests in `ime/src/test/.../SwipeWordResolverTest.kt`.

### Step 2: Wire resolver at swipe commit

1. Ensure `KeyboardScreen` / `KeyboardView` receives a `SuggestionEngine`
   (or `(String) -> String` resolve lambda from `DictusImeService`).
2. On `SwipeTypingResult.SwipeWord`, resolve before `onCommitText`.
3. Do **not** resolve on the MOVE path — only on pointer up.

If Plan 019 has not landed, construct/pass `DictionaryEngine` the same way
`DictusImeService` already does for suggestions (lazy field) — do not
duplicate asset loads if avoidable (reuse the service’s engine instance).

**Verify**: `./gradlew :ime:compileDebugKotlin` → success.

### Step 3: Update outdated swipe tests

`SwipeTypingControllerTest` currently expects `"helo"`. Split concerns:

- Controller still emits path / raw `SwipeWord` **or** emits letters for
  resolver — pick one and document.
- Prefer: controller keeps returning raw path string; UI layer resolves.
  Then controller tests keep raw `"helo"`; resolver tests expect `"hello"`.

**Verify**: `./gradlew :ime:testDebugUnitTest --tests '*Swipe*'` → pass.

### Step 4: Inventory + verify

Document algorithm + offline constraint in inventory. Mark plan DONE.
`bash scripts/verify.sh`.

## Test plan

- `SwipeWordResolverTest`: hello/helo, book (double letter — best-effort;
  subsequence may still fail without dwell; document limitation), no-match
  fallback, casefold.
- Existing swipe controller / path helper tests updated for layering.
- Manual: swipe `hello`, `this`, `good` on S22 → sensible English words.

## Done criteria

- [ ] Swipe commit uses dictionary-backed resolution when engine ready
- [ ] Falls back to raw path when no candidate / engine not ready
- [ ] No network calls
- [ ] Unit tests cover hello←helo style case
- [ ] `bash scripts/verify.sh` → `OK`
- [ ] Scope respected

## STOP conditions

- Implementing a full geometric decoder / porting LatinIME gesture code —
  stop; this plan is the lightweight subsequence+dict approach only.
- Dictionary scan on swipe end is too slow on main thread (>5 ms on
  first-letter bucket in tests) — move resolve to `Dispatchers.Default` and
  commit on Main; if still too slow, stop and report with numbers.
- Plan 019 conflict on `KeyboardScreen` signature — rebase onto 019 or
  use a resolve lambda parameter to reduce merge pain.

## Maintenance notes

- Double letters (`book`, `coffee`) still weak without dwell-time path
  rules (CORRECTNESS-09) — follow-up.
- Reviewer: watch over-aggressive matches (short paths mapping to common
  words); prefer returning raw when best score is weak (add a minimum score
  threshold).
- Future: spatial key-center scoring can replace subsequence without changing
  the `SwipeWordResolver` call site.
