# Job-020 code summary — Plan 030

## What changed

Wired the existing `SuggestionBar` into the typing UI, aligned suggestion-bar
preference defaults between IME and Settings, and fixed English dictionary
asset selection for missing/`auto` language codes.

## Files changed

| File | Why |
|------|-----|
| `ime/.../suggestion/DictionaryEngine.kt` | Added `dictionaryAssetForLanguage()`; init uses English for `null`/`auto`/unknown |
| `ime/.../DictusImeService.kt` | `SUGGESTIONS_ENABLED ?: true`; passes suggestion flows to UI; `commitSuggestion` / `commitCurrentWord`; personal-dict learning on space/suggestion |
| `ime/.../ui/KeyboardScreen.kt` | Renders `SuggestionBar` above keys when enabled |
| `ime/.../suggestion/DictionaryEngineTest.kt` | Table tests for `dictionaryAssetForLanguage` |
| `docs/dictus-inventory.md` | Plan 030 inventory section |
| `plans/README.md` | Plan 030 marked DONE |
| `devteam/jobs/job-020/*` | Job artifacts (plan copy, meta, summaries) |

## Verification

- `./gradlew :ime:testDebugUnitTest --tests '*Dictionary*' --tests '*Suggestion*'` — BUILD SUCCESSFUL
- `./gradlew :ime:testDebugUnitTest` — BUILD SUCCESSFUL
- `bash scripts/verify.sh` — OK

## Drift check note

Plan drift anchor `32b0d20` is not in this repo history (main tip `03477a3`).
Code matched plan excerpts on current main; proceeded without STOP.

## Out of scope (unchanged)

SwipeWordResolver, autocorrect default-ON, number row, SuggestionBar visual design.
