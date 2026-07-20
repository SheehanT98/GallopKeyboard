# Plan 026: Spike opt-in on-device autocorrect on space

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat bfc7085..HEAD -- ime/src/main/java/com/gallopkeyboard/ime/suggestion/ ime/src/main/java/com/gallopkeyboard/ime/ui/KeyboardScreen.kt ime/src/main/java/com/gallopkeyboard/ime/DictusImeService.kt core/src/main/java/com/gallopkeyboard/core/preferences/PreferenceKeys.kt app/src/main/java/com/gallopkeyboard/ui/settings/`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.
>
> **Assumption**: Plan 019 merged — suggestion bar + English `DictionaryEngine`
> available. Plan 025 may add space-drag — autocorrect must not fire when
> space was a cursor drag.

## Status

- **Priority**: P1
- **Effort**: M (spike MVP) / L if quality bar fails and needs iteration
- **Risk**: HIGH
- **Depends on**: Plan 019 (required)
- **Category**: direction
- **Planned at**: commit `bfc7085`, 2026-07-20

## Why this matters

With suggestions visible (019) and swipe decoding (023), the largest remaining
“not Gboard” typing gap is **automatic correction on space**. Doing it wrong
destroys trust faster than omitting it — so this plan is an **opt-in MVP +
measurement**, not a silent always-on corrector.

## Current state (post-019 expected)

- `DictionaryEngine.getSuggestions(prefix)` — prefix/frequency only; no
  edit-distance API yet.
- Space key commits `" "` or double-tap `". "` (`KeyboardScreen`).
- `PreferenceKeys.SUGGESTIONS_ENABLED` exists; no autocorrect pref.
- Plan 019 explicitly out-scoped autocorrect.

**Conventions**: Offline only (`AGENTS.md`). English. Default **OFF**. Match
DataStore prefs + Settings toggle patterns in `SettingsViewModel`.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| IME tests | `./gradlew :ime:testDebugUnitTest --tests '*AutoCorrect*' --tests '*Dictionary*'` | BUILD SUCCESSFUL |
| App settings tests | `./gradlew :app:testDebugUnitTest --tests '*Settings*'` | BUILD SUCCESSFUL |
| Verify | `bash scripts/verify.sh` | `OK` |

## Scope

**In scope**:
- New `ime/.../suggestion/AutoCorrect.kt` (pure rank/decide helpers)
- Optional small extension on `DictionaryEngine` for candidate lookup by
  first letter + edit distance filter (keep scans bounded)
- `KeyboardScreen` / `DictusImeService` — on space, maybe replace word
- `PreferenceKeys` + Settings toggle “Autocorrect” default **false**
- Unit tests with clear fixtures
- Short ADR **or** inventory note documenting aggressiveness + undo
- `docs/manual-test-matrix.md` — add autocorrect row (unchecked)
- `docs/dictus-inventory.md` Plan 026 additions
- `plans/README.md`

**Out of scope**:
- Cloud LM / on-device neural LM download
- Autocorrect while composing mid-word underlines (Gboard-style floating) —
  space-commit only for MVP
- Multilingual
- Shipping default-ON

## Git workflow

- Branch: `advisor/026-autocorrect-on-space-spike`
- Commit: `feat(ime): opt-in autocorrect on space (MVP)`
- Do NOT push/PR unless instructed.

## Steps

### Step 1: Pure decision function + tests first

```kotlin
data class AutoCorrectDecision(
    val replaceWith: String?, // null = leave typed word
)

fun decideAutoCorrect(
    typed: String,
    candidates: List<Pair<String, Int>>, // word to frequency
    maxEditDistance: Int = 2,
): AutoCorrectDecision
```

Rules for MVP (encode in tests):

1. If `typed` blank or length < 2 → no replace.
2. If `typed` equals any candidate (casefold) → no replace (already a word).
3. Consider candidates with Levenshtein distance ≤ `maxEditDistance` and
   same first letter; prefer higher frequency; require strictly better than
   typed (typed not in dict).
4. If top two candidates are too close in score → no replace (ambiguity).
5. Never replace capitalized words differently mid-sentence beyond preserving
   initial capital if typed had one.

Implement a tiny Levenshtein in Kotlin (no new deps).

**Verify**: table-driven tests: `teh`→`the`, `helllo`→`hello`, `a`→no,
`the`→no, ambiguous pair → no.

### Step 2: Candidate source from DictionaryEngine

Add something like:

```kotlin
fun candidatesNear(typed: String, max: Int = 25): List<WordEntry>
```

Scan **only** the first-letter bucket (existing prefix index). Cap work;
must stay < few ms — unit-test with a small fake index.

**Verify**: `./gradlew :ime:testDebugUnitTest --tests '*Dictionary*'` → pass.

### Step 3: Wire space path (opt-in)

1. Pref `AUTOCORRECT_ENABLED` default `false`.
2. Settings toggle under Suggestions section.
3. On SPACE tap (not drag from Plan 025): read current word from before-cursor;
   if pref on, `decideAutoCorrect`; if replace:
   - `deleteSurroundingText(typed.length, 0)`
   - `commitText("$replacement ")`
   - Remember `lastAutoCorrect: Pair<original, replacement>` for undo.
4. On immediate DELETE after autocorrect: restore `original` (single undo).

**Verify**: service-level helper tests for replace/undo; compile IME.

### Step 4: Matrix + inventory

Add manual matrix checkboxes for autocorrect on/off. Document default OFF and
undo behavior in inventory. `verify.sh`.

## Test plan

- Decision matrix tests (must be thorough — this is the trust core).
- Undo restores original.
- Pref off → space never replaces.
- Manual S22: type `teh ` with pref on; backspace undoes; leave pref off by
  default for daily use until owner promotes.

## Done criteria

- [ ] Autocorrect pref exists, default **false**
- [ ] Space may replace via dictionary edit-distance rules when enabled
- [ ] Immediate backspace undoes one autocorrect
- [ ] No network
- [ ] Unit tests cover teh/hello/ambiguity/short
- [ ] `bash scripts/verify.sh` → `OK`
- [ ] Scope respected

## STOP conditions

- First-letter bucket scan too slow on `dict_en.txt` in a microbenchmark
  (>5 ms on JVM test host for worst letter) — stop and report; don’t ship
  main-thread thrash.
- Quality feels randomly wrong in manual smoke — leave pref off and document
  findings in inventory instead of tuning forever in this plan.
- Conflicts with Plan 025 space-drag unresolved — require drag slop gate.

## Maintenance notes

- Promoting default ON requires owner sign-off + ticked matrix cells.
- Reviewer: hostility-test with proper nouns and slang; prefer under-correct.
