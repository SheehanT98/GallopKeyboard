# Job 016 — Review

| Field | Value |
|-------|-------|
| **Job** | job-016 |
| **Branch** | `cursor/devteam-job-016-execute-plan-015-defer-ime-model-sha-verify-c1fc` |
| **PR** | [#39](https://github.com/SheehanT98/GallopKeyboard/pull/39) |
| **Plan** | `plans/015-defer-ime-model-sha-verify.md` |
| **Reviewer** | devteam-reviewer (cursor-grok-4.5-high) |
| **Reviewed at** | 2026-07-17T15:56:00Z |
| **Base** | `origin/main...HEAD` |
| **Verdict** | **APPROVE** |

## Summary

Plan 015 moves the once-per-day SHA-256 integrity pass off the IME `onCreate` critical path while preserving the fast `areFilesPresent` gate and corrupt-banner behavior. The implementation matches the plan's target shape, reuses the existing `bindingScope` + `Dispatchers.IO` pattern, and keeps `ModelInstaller.verifyInstalledIfDue()` semantics intact (stamp `KEY_LAST_VERIFY_MS` before hashing). Scope is tight; tests and `verify.sh` are green.

## Scope compliance

| In / out of scope | Result |
|-------------------|--------|
| `DictusImeService.kt` — defer verify; keep sync `areFilesPresent`; corrupt banner on Main | **Met** |
| `ModelInstaller.kt` — KDoc only (background-scheduled verify) | **Met** |
| `core/src/test/.../ModelInstallerTest.kt` — focused interval skip test | **Met** |
| `docs/dictus-inventory.md` — Plan 015 additions | **Met** |
| `plans/README.md` — Plan 015 → DONE | **Met** |
| No SHA pin / registry / download UX changes | **Met** |
| No verify disabled | **Met** |
| `PanelHost.kt` | **Correctly untouched** — plan notes existing cheap-check comment |

Product diff vs `origin/main`: five files, 59 insertions / 4 deletions. No unrelated modules touched.

## Implementation review

### `DictusImeService.onCreate`

```173:184:ime/src/main/java/com/gallopkeyboard/ime/DictusImeService.kt
        val installer = ModelInstaller(applicationContext)
        if (!installer.areFilesPresent(ModelRegistry.defaultVoiceBundle)) {
            entryPoint.voiceModelPromptState().showBanner()
        }
        bindingScope.launch(Dispatchers.IO) {
            val corrupt = installer.verifyInstalledIfDue()
            if (corrupt) {
                withContext(Dispatchers.Main) {
                    entryPoint.voiceModelPromptState().showBanner()
                }
            }
        }
```

- **Ordering**: synchronous missing-file banner first, then background verify — correct for keyboard bring-up.
- **Threading**: SHA work on IO; UI mutation on Main — appropriate for `VoiceModelPromptState`.
- **Scope**: `bindingScope` (`MainScope`) is cancelled in `onDestroy`; verify is launched early in service life — acceptable for daily background work.

### `ModelInstaller.verifyInstalledIfDue`

- KDoc updated to state verify is off the IME critical path.
- `KEY_LAST_VERIFY_MS` still written before `fileStatus` hashing — preserves once-per-day semantics and avoids double-hash on retry within the interval.
- No API surface change; settings or other future callers can still invoke synchronously if user-initiated.

### Tests

`ModelInstallerTest.verifyInstalledIfDue_skipsWhenRecentlyVerified` exercises the 24 h short-circuit without requiring real model blobs — reasonable minimum bar per plan.

## Done criteria

| Criterion | Result |
|-----------|--------|
| No synchronous `verifyInstalledIfDue()` on `onCreate` thread | **PASS** |
| `areFilesPresent` banner on create path | **PASS** |
| Corrupt → voice-model banner | **PASS** |
| `bash scripts/verify.sh` → `OK` | **PASS** (re-confirmed by tester) |
| Inventory + plan index updated | **PASS** |
| No out-of-scope files | **PASS** |

## Verification evidence

| Check | Result |
|-------|--------|
| `rg verifyInstalledIfDue ime/src/main` | Only inside `bindingScope.launch` |
| `./gradlew :ime:testDebugUnitTest :core:testDebugUnitTest` | BUILD SUCCESSFUL |
| `bash scripts/verify.sh` | `OK` |
| Tester report | PASS (`03-test-report.md`) |

## STOP conditions

| Condition | Outcome |
|-----------|---------|
| `bindingScope` lifecycle inadequate | **Not hit** |
| Preference key / API drift | **Not hit** |
| Daily verify removed | **Not hit** |

## Findings

None blocking.

**Nit (non-blocking):** Unit test covers interval skip only, not corrupt-file detection. Plan explicitly allows this when a full harness would be heavy; grep + `verify.sh` suffice for this PR.

## Risks for the human reviewer

- **Low risk perf fix** — behavior change is deferral only; corrupt/missing banners still appear (missing immediately, corrupt after background verify completes).
- On a verify-due day with installed models, keyboard chrome should appear without multi-second SHA stall; corrupt banner may appear slightly later — intended.
- Manual cold-start check on device (documented in inventory) remains optional validation.

## Advance

`npm run devteam:advance -- job-016 --to double_checking`
