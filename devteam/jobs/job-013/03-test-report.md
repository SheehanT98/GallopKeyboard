# Job 013 — Test report

| Field | Value |
|-------|-------|
| **Job** | job-013 |
| **Branch** | `cursor/devteam-job-013-execute-plan-013-swipe-typing-c1fc` |
| **PR** | [#34](https://github.com/SheehanT98/GallopKeyboard/pull/34) |
| **Plan** | `plans/013-swipe-typing-across-letters.md` |
| **Tester** | devteam-tester (composer-2.5) |
| **Tested at** | 2026-07-17T13:24:00Z |
| **SHA tested** | `26f62f00163418920f6c13a845e63314d57a51fd` |
| **Verdict** | **PASS** (automated); manual on-device deferred |

## Environment

```bash
source scripts/android-env.sh
# ANDROID_HOME=/opt/android-sdk
# JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

| Check | Command | Result |
|-------|---------|--------|
| Branch | `git branch --show-current` | on job-013 branch |
| Sync | branch up to date with `origin/cursor/devteam-job-013-execute-plan-013-swipe-typing-c1fc` | up to date |

## Commands run

| Check | Command | Result |
|-------|---------|--------|
| Plan verify | `source scripts/android-env.sh && ./gradlew :ime:testDebugUnitTest :app:assembleDebug` | exit 0, BUILD SUCCESSFUL |
| Fresh unit tests | `source scripts/android-env.sh && ./gradlew :ime:testDebugUnitTest --rerun-tasks` | exit 0, 110 tests, 0 failures |
| Plan status | `plans/013-swipe-typing-across-letters.md` | done checkboxes marked |

### Unit test summary

- **Total**: 110 (`:ime:testDebugUnitTest`)
- **Failures**: 0
- **Errors**: 0
- **New coverage**: `SwipePathHelperTest` (4), `SwipeTypingControllerTest` (3)

## Spot-check (code vs plan)

| Requirement | Evidence | Result |
|-------------|----------|--------|
| LETTERS layer swipe across keys commits word | `KeyboardView` parent `pointerInput`; `SwipeTypingController` path tracking; `onSwipeWord` → `onCommitText` with trailing space | PASS |
| Path helper dedupes consecutive, ignores non-letters | `SwipePathHelper.pathToWord` + `SwipePathHelperTest` | PASS |
| Short taps remain single-key taps | `SwipeTypingController.onPointerUp` → `SwipeTypingResult.Tap`; controller test | PASS |
| Long-press accents preserved | `onLongPressThreshold` + accent tracking; controller accent test | PASS |
| Cross-key coordination at `KeyboardView` level | Character keys delegate gestures; bounds reported via `KeyRow` | PASS |
| Optional dictionary assist | `resolveSwipeWord` uses `SuggestionEngine` when wired | PASS (code) |
| DELETE repeat / shift double-tap unchanged | Non-character keys keep local `KeyButton` gestures | PASS (code) |
| Light theme unchanged | No theme edits in swipe diff | PASS |

## Done criteria (Plan 013)

| Criterion | Result |
|-----------|--------|
| Swipe across letters commits a word | PASS (code); **NOT RUN** on device |
| Tap typing and accents still work | PASS (unit tests + code) |
| Tests for path helper pass | PASS |
| README updated | PASS (`plans/README.md` 013 → DONE) |

## STOP conditions

| Condition | Outcome |
|-----------|---------|
| Shipping on-device neural swipe model / large binary | **Not hit** |
| Breaking delete key-repeat or shift double-tap | **Not hit** (code review) |
| Requiring network for decoding | **Not hit** |

## Manual on-device (deferred)

Swipe gesture UX was not exercised — no `adb` device attached.

| # | Action | Result |
|---|--------|--------|
| 1 | Press letter → drag across keys → release commits word + space | not run |
| 2 | Short tap still inserts single character | not run |
| 3 | Long-press accent popup still works | not run |
| 4 | Swipe only on LETTERS layer (not numbers/symbols) | not run |

## Blockers

None for automated gate. Manual swipe UX validation deferred to reviewer or owner with a sideloaded build.

## Advance

`npm run devteam:advance -- job-013 --to reviewing`
