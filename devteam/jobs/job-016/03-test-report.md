# Job 016 — Test report

| Field | Value |
|-------|-------|
| **Job** | job-016 |
| **Branch** | `cursor/devteam-job-016-execute-plan-015-defer-ime-model-sha-verify-c1fc` |
| **PR** | [#39](https://github.com/SheehanT98/GallopKeyboard/pull/39) |
| **Plan** | `plans/015-defer-ime-model-sha-verify.md` |
| **Tester** | devteam-tester (composer-2.5) |
| **Tested at** | 2026-07-17T15:55:00Z |
| **SHA tested** | `f7c223672afc862833befd80b2a25788315ea2b0` |
| **Verdict** | **PASS** |

## Environment

| Check | Command | Result |
|-------|---------|--------|
| Branch | `git branch --show-current` | `cursor/devteam-job-016-execute-plan-015-defer-ime-model-sha-verify-c1fc` |
| Job status | `devteam/jobs/job-016/meta.json` | `testing` |

## Commands run

| Check | Command | Result |
|-------|---------|--------|
| IME call site | `rg -n "verifyInstalledIfDue" ime/src/main app/src/main` | Single match at `DictusImeService.kt:178` inside `bindingScope.launch(Dispatchers.IO)` |
| All Kotlin call sites | `rg -n "verifyInstalledIfDue" --glob '*.kt'` | IME background launch + `ModelInstaller` definition + unit test only |
| Core unit tests | `./gradlew :core:testDebugUnitTest` | BUILD SUCCESSFUL |
| IME unit tests | `./gradlew :ime:testDebugUnitTest` | BUILD SUCCESSFUL |
| Full verify | `bash scripts/verify.sh` | exit 0, ends with `OK` |
| Product diff scope | `git diff --stat origin/main...HEAD` (in-scope paths) | 5 files: `DictusImeService.kt`, `ModelInstaller.kt`, `ModelInstallerTest.kt`, `docs/dictus-inventory.md`, `plans/README.md` |

## Done criteria (Plan 015)

| Criterion | Result |
|-----------|--------|
| `DictusImeService.onCreate` does not call `verifyInstalledIfDue()` on the calling thread before returning | **PASS** — verify runs inside `bindingScope.launch(Dispatchers.IO)` after synchronous `areFilesPresent` |
| Cheap `areFilesPresent` banner check remains on create path | **PASS** — lines 174–176 run before background launch |
| Corrupt detection still triggers voice-model banner | **PASS** — `withContext(Dispatchers.Main) { showBanner() }` when `corrupt` is true |
| `bash scripts/verify.sh` → `OK` | **PASS** |
| Inventory section present; `plans/README.md` 015 → `DONE` | **PASS** — `## Plan 015 additions` in inventory; table row 015 = DONE |
| No out-of-scope files modified | **PASS** — diff limited to in-scope Kotlin, tests, inventory, and plan index |

## STOP conditions

| Condition | Outcome |
|-----------|---------|
| `bindingScope` cancelled too early for verify to run | **Not hit** — scope lives until `onDestroy`; verify launches in `onCreate` |
| Drift in `ModelInstaller` API / preference keys | **Not hit** — `KEY_LAST_VERIFY_MS` / `VERIFY_INTERVAL_MS` unchanged; stamp-before-hash preserved |
| Fix requires deleting daily verify entirely | **Not hit** — verify still scheduled from IME startup, off critical path |

## Blockers

None.

## Advance

`npm run devteam:advance -- job-016 --to reviewing`
