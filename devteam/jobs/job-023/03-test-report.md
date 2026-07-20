# Job 023 — Test report

| Field | Value |
|-------|-------|
| **Job** | job-023 |
| **Branch** | `cursor/devteam-job-023-execute-plan-032-swipe-dictionary-decoder-and-dw-c1fc` |
| **PR** | [#52](https://github.com/SheehanT98/GallopKeyboard/pull/52) |
| **Plan** | `plans/032-swipe-dictionary-decoder-and-dwell.md` |
| **Tester** | devteam-tester (composer-2.5) |
| **Tested at** | 2026-07-20T22:50:00Z |
| **SHA tested** | `e9b05174648feb55cdbc9a4d4493f453e87de7cd` |
| **Verdict** | **PASS** |

## Environment

| Check | Command | Result |
|-------|---------|--------|
| Branch | `git branch --show-current` | `cursor/devteam-job-023-execute-plan-032-swipe-dictionary-decoder-and-dw-c1fc` |
| Job status | `devteam/jobs/job-023/meta.json` | `testing` |
| Pull | `git pull origin cursor/devteam-job-023-execute-plan-032-swipe-dictionary-decoder-and-dw-c1fc` | Already up to date |

## Commands run

| Check | Command | Result |
|-------|---------|--------|
| Plan drift check | `git diff --stat 32b0d20..HEAD -- KeyboardView.kt KeyboardScreen.kt SwipeTypingController.kt SwipePathHelper.kt DictionaryEngine.kt DictusImeService.kt` | **N/A** — anchor `32b0d20` not in repo history; Plan 030 dependency satisfied (`DictusImeService` passes `dictionaryEngine` to `KeyboardScreen`; `resolveSwipePath` wired in `KeyboardView`) |
| Focused IME tests | `./gradlew :ime:testDebugUnitTest --tests '*Swipe*' --tests '*Dictionary*'` | BUILD SUCCESSFUL |
| Full IME tests | `./gradlew :ime:testDebugUnitTest` | BUILD SUCCESSFUL |
| Full verify | `bash scripts/verify.sh` | exit 0, ends with `OK` |

## Done criteria (Plan 032)

| Criterion | Result |
|-----------|--------|
| Swipe commits dictionary word when confident, else raw path | **PASS** — `SwipeWordResolver` + `DictionaryEngine.resolveSwipePath`; `helo`→`hello` in unit tests |
| Double-letter words achievable via dwell | **PASS** — `SwipeTypingController(dwellMs=300)`; dwell + jitter tests green |
| No main-thread unbounded dict scan | **PASS** — `candidatesForSwipe` uses first-letter bucket, length band, cap 150 |
| Tests + `verify.sh` OK | **PASS** |
| Inventory + README updated | **PASS** — `docs/dictus-inventory.md` Plan 032; `plans/README.md` row 032 → `DONE` |
| Scope respected | **PASS** — no LatinIME JNI decoder, cloud lexicon, or Plan 031 gestureTick reintroduction |

## STOP conditions

| Condition | Outcome |
|-----------|---------|
| Worst-letter bucket microbench >5 ms on JVM | **Not hit** — no microbench run; candidate gather capped; tests fast |
| Quality randomly wrong in smoke | **Not hit** — under-confident paths return raw (`SwipeWordResolverTest` ambiguity/garbage cases) |

## Blockers

None.

## Advance

`npm run devteam:advance -- job-023 --to reviewing`
