# Job-023 code summary — Plan 032

## What changed

Swipe on LETTERS now resolves raw paths through the English dictionary on
pointer-up (`helo` → `hello` when confident), and dwell on a key for 300 ms
emits double letters for words like `hello`.

## Files changed

| File | Why |
|------|-----|
| `ime/.../suggestion/SwipeWordResolver.kt` | Pure subsequence scorer; returns raw path when under-confident |
| `ime/.../suggestion/DictionaryEngine.kt` | `candidatesForSwipe` + `resolveSwipePath` (first-letter bucket, capped) |
| `ime/.../ui/KeyboardView.kt` | Calls `resolveSwipePath` instead of prefix `getSuggestions` |
| `ime/.../ui/KeyboardScreen.kt` | Accepts `suggestionEngine`; passes to `KeyboardView` |
| `ime/.../DictusImeService.kt` | Passes `dictionaryEngine` into `KeyboardScreen` |
| `ime/.../ui/SwipeTypingController.kt` | Dwell re-emit (`dwellMs=300`, injectable clock) |
| `ime/.../ui/SwipePathHelper.kt` | `pathToWord` no longer dedupes — controller handles slide vs dwell |
| `ime/.../suggestion/SwipeWordResolverTest.kt` | Resolver matrix (hello, ambiguity, garbage) |
| `ime/.../suggestion/DictionaryEngineTest.kt` | `candidatesForSwipe` + `resolveSwipePath` integration |
| `ime/.../ui/SwipeTypingControllerTest.kt` | Dwell double-letter + jitter-below-dwell tests |
| `ime/.../ui/SwipePathHelperTest.kt` | Updated pathToWord expectations |
| `docs/dictus-inventory.md` | Plan 032 inventory section |
| `plans/README.md` | Plan 032 marked DONE |

## Verification

- `./gradlew :ime:testDebugUnitTest --tests '*Swipe*' --tests '*Dictionary*'` — BUILD SUCCESSFUL
- `bash scripts/verify.sh` — OK

## Drift check

Plan 030 present (SuggestionBar wired, English dict default). Proceeded without STOP.

## Out of scope (unchanged)

LatinIME geometric JNI decoder, cloud lexicon, Plan 031 gestureTick architecture.
