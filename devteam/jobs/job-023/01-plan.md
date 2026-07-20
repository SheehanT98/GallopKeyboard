# Plan 032: Decode swipe paths with DictionaryEngine + dwell (finish 023)

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 32b0d20..HEAD -- ime/src/main/java/com/gallopkeyboard/ime/ui/KeyboardView.kt ime/src/main/java/com/gallopkeyboard/ime/ui/KeyboardScreen.kt ime/src/main/java/com/gallopkeyboard/ime/ui/SwipeTypingController.kt ime/src/main/java/com/gallopkeyboard/ime/ui/SwipePathHelper.kt ime/src/main/java/com/gallopkeyboard/ime/suggestion/DictionaryEngine.kt ime/src/main/java/com/gallopkeyboard/ime/DictusImeService.kt`
> If Plan 030 not merged (SuggestionBar still unwired **or** dictionary still
> defaults to French), STOP and run 030 first — English dict is required.
>
> **Supersedes incomplete Plan 023**: marked DONE; no `SwipeWordResolver`;
> production still commits raw paths like `helo`.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED
- **Depends on**: Plan 030 (English dictionary + engine available to UI)
- **Category**: bug / direction
- **Planned at**: commit `32b0d20`, 2026-07-20

## Why this matters

Swipe on LETTERS still commits the raw key path (`helo` not `hello`).
`KeyboardView.resolveSwipeWord` only prefix-matches when a `suggestionEngine`
is passed — `KeyboardScreen` never passes one. `appendKey` / `dedupeConsecutive`
drop double letters, so `hello`/`better` are structurally unreachable.
Plan 023 specified a subsequence resolver + dwell follow-up; neither landed.

## Current state

```kotlin
// KeyboardView.kt
fun resolveSwipeWord(rawPath: String): String {
    val engine = currentSuggestionEngine.value ?: return rawPath
    val suggestions = engine.getSuggestions(rawPath, maxResults = 1) // prefix!
    return suggestions.firstOrNull() ?: rawPath
}
// KeyboardScreen never passes suggestionEngine → always raw path

// SwipeTypingController.appendKey — skips same key while finger stays
// SwipePathHelper.dedupeConsecutive — collapses doubles
// Tests still expect "helo"
```

**Conventions**: Offline-only lexicon (`AGENTS.md`). Prefer under-matching over
random words. Pure resolver + unit tests first (Plan 023 approach).

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Focused | `./gradlew :ime:testDebugUnitTest --tests '*Swipe*' --tests '*Dictionary*'` | BUILD SUCCESSFUL |
| IME | `./gradlew :ime:testDebugUnitTest` | BUILD SUCCESSFUL |
| Verify | `bash scripts/verify.sh` | `OK` |

## Scope

**In scope**:
- New `ime/.../suggestion/SwipeWordResolver.kt` (or under `ui/` if you must —
  prefer `suggestion/`) — pure score/rank
- `DictionaryEngine` helper to list first-letter / length-banded candidates
  for swipe (reuse index; cap work)
- `KeyboardView` / `KeyboardScreen` / `DictusImeService` — pass engine; call
  resolver on pointer-up only
- `SwipeTypingController` / `SwipePathHelper` — dwell re-emit for double
  letters (time threshold ~250–400 ms on same key before dedupe)
- Unit tests: `helo`→`hello`, short path unchanged, dwell emits double
- `docs/dictus-inventory.md` Plan 032
- `plans/README.md`

**Out of scope**:
- Full LatinIME geometric JNI decoder
- Cloud lexicon
- Changing Plan 031 highlight architecture (don’t reintroduce gestureTick)

## Git workflow

- Branch: `cursor/032-swipe-dictionary-decoder-and-dwell`
- Commit: `feat(ime): swipe dictionary decode + dwell for doubles`
- Do NOT push/PR unless instructed.

## Steps

### Step 1: Pure SwipeWordResolver + tests

Implement scoring roughly:

1. Candidate must contain path letters as subsequence (casefold).
2. Prefer first/last letter match to path ends.
3. Prefer higher dictionary frequency; reject if top two too close.
4. If no candidate clears bar → return raw path (or empty→raw).

**Verify**: table tests with small fake word list — `helo`→`hello`, `teh` path
behavior documented, garbage path → raw.

### Step 2: Wire engine into KeyboardView

Pass `DictionaryEngine` / `SuggestionEngine` from IME → KeyboardScreen →
KeyboardView. On `SwipeWord`, call resolver (not prefix `getSuggestions`).

**Verify**: integration-style unit with fake engine; compile.

### Step 3: Dwell for double letters

Before `dedupeConsecutive`, if finger remains on same key ≥ dwellMs, append
an extra occurrence. Tune so `hello` path can include two `l`s. Update tests
that currently expect `"helo"`.

**Verify**: controller test with virtual time / injected clock if needed.

### Step 4: Docs + verify

Inventory: decoder rules + dwell ms. `bash scripts/verify.sh`.

## Test plan

- Resolver matrix (must be thorough — trust).
- Dwell emits double; jitter below dwell does not.
- Manual: swipe `hello`, `the`, `book` on S22.

## Done criteria

- [ ] Swipe commits dictionary word when confident, else raw path
- [ ] Double-letter words achievable via dwell
- [ ] No main-thread unbounded dict scan (cap candidates; reuse first-letter bucket)
- [ ] Tests + `verify.sh` OK
- [ ] Scope respected

## STOP conditions

- Worst-letter bucket microbench >5 ms on JVM for swipe candidate gather —
  stop; add tighter bands before shipping.
- Quality randomly wrong in smoke — ship raw-path fallback and document;
  don’t tune forever.

## Maintenance notes

- Depends on 030 English dict — French default would poison rankings.
- Reviewer: hostility-test proper nouns; prefer under-correct.
- Mark Plan 023 superseded by 032.
