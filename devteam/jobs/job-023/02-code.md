# Job-023 code — Plan 032

## Step 1 — SwipeWordResolver

`SwipeWordResolver.resolve(rawPath, candidates)`:

1. Filter candidates where `rawPath` is a subsequence of `strippedLower`.
2. Score: `frequency` + first-letter (+50) + last-letter (+50) − length-gap penalty.
3. Return best word when top-two score gap ≥ 15; else return raw path.

`DictionaryEngine.candidatesForSwipe` scans the first-letter bucket only,
length band `[path.length, path.length + 3]`, last-letter match, subsequence
filter, max 150 entries.

## Step 2 — Wire engine

`DictusImeService` → `KeyboardScreen(suggestionEngine = dictionaryEngine)` →
`KeyboardView`. On `SwipeTypingResult.SwipeWord`, `resolveSwipeWord` calls
`DictionaryEngine.resolveSwipePath` on pointer-up only.

## Step 3 — Dwell for doubles

`SwipeTypingController(dwellMs = 300L, clock = …)`:

- Slide dedupe: `appendKey` skips same key while finger moves.
- Dwell: `maybeEmitDwell` re-appends same key after 300 ms on that key.
- `SwipePathHelper.pathToWord` joins letters without dedupe.

## Tests added

- `SwipeWordResolverTest` — helo→hello, short path, garbage, ambiguity, subsequence
- `DictionaryEngineTest` — candidatesForSwipe filters; resolveSwipePath helo→hello
- `SwipeTypingControllerTest` — dwell emits `hello`; jitter below dwell stays `helo`
