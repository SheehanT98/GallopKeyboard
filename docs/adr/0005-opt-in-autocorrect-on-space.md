# ADR-0005: Opt-in autocorrect on space (MVP)

## Status

Accepted (spike / opt-in MVP)

## Context

Plan 019 shipped a suggestion bar and English `DictionaryEngine`. The largest remaining
Gboard-like typing gap is automatic correction on space. Incorrect autocorrect destroys
trust faster than omitting it, so GallopKeyboard ships an **opt-in** MVP only.

## Decision

1. **Default OFF** — `PreferenceKeys.AUTOCORRECT_ENABLED` defaults to `false`. Promoting
   default-ON requires owner sign-off and ticked manual matrix cells.
2. **Space-commit only** — no mid-word underlines. Autocorrect runs when SPACE is tapped
   (the non-drag path from Plan 025). Space cursor drag never commits a space and must
   not trigger autocorrect.
3. **Conservative ranking** — `decideAutoCorrect` considers same first letter, Levenshtein
   distance ≤ 2, higher frequency; refuses blank/short typed words, exact dictionary hits,
   and ambiguous top-two frequency pairs. Preserves initial capital only.
4. **Single undo** — remember `LastAutoCorrect(original, replacement)`; immediate DELETE
   restores `original` (deletes `replacement` + trailing space). Double-tap period uses a
   dedicated path so it does not consume undo.
5. **Offline only** — candidates come from the on-device AOSP dictionary first-letter
   bucket (`DictionaryEngine.candidatesNear`). No cloud LM.

## Consequences

- Settings exposes an **Autocorrect** toggle under the keyboard section.
- Quality may still mis-correct proper nouns/slang; prefer under-correcting. If manual
  smoke feels randomly wrong, leave the pref off and document findings in
  `docs/dictus-inventory.md` rather than endless tuning in this spike.
- Worst-letter bucket scan must stay ≤ 5 ms on the JVM test host (Plan 026 STOP gate).
