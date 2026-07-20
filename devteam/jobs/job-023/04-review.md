# Job 023 — Review (Plan 032)

| Field | Value |
|-------|-------|
| **Job** | job-023 |
| **Plan** | `plans/032-swipe-dictionary-decoder-and-dwell.md` |
| **Branch** | `cursor/devteam-job-023-execute-plan-032-swipe-dictionary-decoder-and-dw-c1fc` |
| **PR** | [#52](https://github.com/SheehanT98/GallopKeyboard/pull/52) |
| **SHA reviewed** | `1cd3f41687fe5ab3fe58cc8b1c7fe37edd43f944` |
| **Verdict** | **APPROVE** |

## Summary

Approve. Plan 032 done criteria are met: swipe commits a dictionary word when the subsequence scorer is confident (else raw path), dwell at 300 ms makes double-letter paths reachable, candidate gather is bounded (first-letter bucket + length band + cap 150), docs/README updated, Plan 023 marked SUPERSEDED → 032, and tester + reviewer-spot-check evidence show focused IME tests green with prior `verify.sh` OK.

## Scope compliance

| In scope | Status |
|----------|--------|
| `SwipeWordResolver` — pure score/rank | **Met** — subsequence filter; first/last +50; frequency; length-gap penalty; `MIN_SCORE_GAP = 15` → raw |
| `DictionaryEngine.candidatesForSwipe` / `resolveSwipePath` | **Met** — first-letter bucket, `[path, path+3]`, last-letter + subsequence, max 150 |
| Wire IME → Screen → View; resolve on pointer-up | **Met** — `DictusImeService` passes `dictionaryEngine`; `resolveSwipeWord` → `resolveSwipePath` |
| Dwell for doubles (~250–400 ms) | **Met** — `dwellMs = 300`; injected clock; `pathToWord` no longer dedupes |
| Unit tests (`helo`→`hello`, short/raw, dwell/jitter) | **Met** |
| `docs/dictus-inventory.md` Plan 032 + `plans/README.md` | **Met** — 032 DONE; 023 SUPERSEDED → 032 |

| Out of scope (must not expand) | Status |
|--------------------------------|--------|
| Full LatinIME geometric JNI decoder | Untouched |
| Cloud lexicon | Untouched |
| Plan 031 highlight / reintroduce `gestureTick` | Untouched (`rg gestureTick` → no matches) |

No STOP conditions hit. Drift anchor `32b0d20` absent from history (same as tester); Plan 030 English default present (`dictionaryAssetForLanguage` → `dict_en.txt`).

## Done criteria

| Criterion | Result |
|-----------|--------|
| Swipe commits dictionary word when confident, else raw path | **PASS** |
| Double-letter words achievable via dwell | **PASS** |
| No main-thread unbounded dict scan | **PASS** (bucket + band + cap 150; resolve on pointer-up only) |
| Tests + `verify.sh` OK | **PASS** (tester `03-test-report.md`; reviewer re-ran focused swipe/dict tests) |
| Scope respected | **PASS** |

## Verification evidence

From `03-test-report.md` (SHA `e9b0517` implementation tip; review tip `1cd3f41` is advance-only after that):

- `./gradlew :ime:testDebugUnitTest --tests '*Swipe*' --tests '*Dictionary*'` → BUILD SUCCESSFUL
- `./gradlew :ime:testDebugUnitTest` → BUILD SUCCESSFUL
- `bash scripts/verify.sh` → exit 0, `OK`

Reviewer spot-check (tip `1cd3f41`):

- `./gradlew :ime:testDebugUnitTest --tests '*SwipeWordResolver*' --tests '*SwipeTypingController*' --tests '*SwipePathHelper*' --tests 'com.gallopkeyboard.ime.suggestion.DictionaryEngineTest'` → BUILD SUCCESSFUL

Implementation spot-checks:

- `helo`→`hello` via resolver + engine path; ambiguous/garbage → raw
- Dwell emits `hello`; jitter below 300 ms stays `helo`
- Prefix index remains frequency-sorted within first-letter buckets (load-time sort + `groupBy`)

## Risks for the human reviewer

1. **Dwell needs MOVE** — `maybeEmitDwell` runs only from `onPointerMove`. A perfectly still finger after entering a key will not emit a double until another MOVE arrives (finger jitter usually covers this; true hold-still is residual).
2. **Ambiguity test weakness** — `SwipeWordResolverTest` “ambiguous top two” uses path `teh` with `the`/`tee`, which are not subsequences of `teh`, so it exercises no-match fallback rather than the `MIN_SCORE_GAP` gate. Gap logic is present in code; hostility-test close pairs on device.
3. **Cap-150 early break** — gather takes the first 150 bucket matches (freq-desc). A rarer better length-fit beyond the cap could be missed; under-match is preferred per plan.
4. **Orphaned KDoc** — Plan 026 `candidatesNear` doc block sits above `candidatesForSwipe` (cosmetic only).
5. **Manual S22** — swipe `hello`, `the`, `book` (plan test plan); prefer under-correct on proper nouns.
6. **CI** — PR #52 build check was still in progress at review time; merge should wait for green CI.

## PR

Already open: https://github.com/SheehanT98/GallopKeyboard/pull/52 — no `devteam:open-pr` needed.

## Advance

`npm run devteam:advance -- job-023 --to double_checking`
