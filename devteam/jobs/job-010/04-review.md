# Review — job-010 (Hardening — battery, crashes, release APK)

| Field | Value |
|-------|-------|
| **Job** | job-010 |
| **Branch** | `cursor/devteam-job-010-execute-plan-010-hardening-battery-crashes-relea-c1fc` |
| **PR** | [#25](https://github.com/SheehanT98/GallopKeyboard/pull/25) |
| **Reviewer** | devteam-reviewer (cursor-grok-4.5-high) |
| **Reviewed at** | 2026-07-16T19:40:00Z |
| **Feature SHA** | `f5da153` |
| **HEAD at review** | `9645ade` (includes test report + advance to reviewing) |
| **Verdict** | **revise** |

## Summary

**Revise.** Plan 010 hardening deliverables are largely present and automated gates are green (`verify.sh`, `assembleRelease`, logging guards, inventory, docs, `plans/README.md` DONE). Release APK ~88 MB (native `.so`) is an **acceptable documented STOP deviation** — not a block. However, `PolishingTranscriber.onSessionCancel` never clears the lifecycle session flag, so after a cancel gesture the idle unload timer can never fire. That breaks a core battery done criterion for a path covered by the manual matrix (“Cancel gesture”). Fix before merge.

**Blockers:**

1. **Cancel leaves `sessionActive = true`** — `PolishingTranscriber.onSessionCancel` does not call `lifecycleManager.onSessionStopped()`. `ModelLifecycleManager.scheduleUnload` then bails with `if (sessionActive) return` after 60 s, so engines stay loaded after cancel until a later successful stop (or process death). Fix: call `onSessionStopped()` (or equivalent) from the cancel path, mirroring stop.

## Scope compliance

| Plan item | Status |
|-----------|--------|
| `CrashHandler` → `filesDir/crashes/` (max 20, chain prior handler) | Done |
| Install in Application + IME `onCreate` | Done (`DictusApplication`, `DictusImeService`) |
| StrictMode in debug only | Done |
| `ModelLifecycleManager` 60 s idle unload | Done (structure) — **cancel path broken** (blocker) |
| `models_keep_loaded` default false | Done (DataStore `PreferenceKeys` + Settings toggle) |
| `CrashLogsScreen` list / copy / share / delete | Done |
| Release minify + shrink + keystore.properties / debug fallback | Done |
| `app/proguard-rules.pro` JNI / sherpa / whisper keeps | Done |
| `.gitignore` keystore + crashes | Done |
| `docs/release-signing.md` | Done |
| `docs/manual-test-matrix.md` (+ battery procedure) | Done; baseline row **pending device** |
| `docs/dictus-inventory.md` Plan 010 additions | Done |
| `scripts/verify.sh` println + Log.d advisory | Done |
| `plans/README.md` → DONE | Done |
| Out of scope (Play Store, Sentry, new features) | Respected |

### Acceptable deviations

- **`settings_prefs.xml` not added** — app already uses DataStore + Compose Settings; `MODELS_KEEP_LOADED` matches existing preference pattern.
- **No soft references** — DI singletons closed + lazy re-init (`ParakeetEngine.ensureRecognizer` / `ensurePolishInitialized`) is a reasonable adaptation.
- **Release APK 88 MB vs STOP 30 MB** — dominated by bundled native libs; R8 minify/shrink enabled; coded/tested as known; **not a block** per STOP intent + job note.
- **Battery mAh baseline** — procedure + pending table in matrix; no adb device in CI (same class as prior jobs’ deferred device work).
- Extra `ModelLifecycleController` interface — DI/test seam; in scope.

## Verification evidence

From `03-test-report.md` (tested SHA `d068e97` tip at test advance; product commit `f5da153`):

| Check | Result |
|-------|--------|
| `bash scripts/verify.sh` | exit 0 (`assembleDebug`, `testAll`, `lint`, new guards) |
| `./gradlew assembleRelease` | BUILD SUCCESSFUL (debug signing fallback) |
| APK sizes | debug 157M, release 88M |
| Inventory `Plan 010 additions` | OK |
| `plans/README.md` row 010 | DONE |
| PR #25 | open, mergeable; CI `build` in progress at review time |

### Done criteria

| Criterion | Result |
|-----------|--------|
| `verify.sh` exits 0 with new guards | PASS |
| `assembleRelease` succeeds | PASS |
| `CrashHandler` writes on forced exception | **DEFERRED** (no device) |
| `ModelLifecycleManager` unloads after 60 s idle | **FAIL (static)** — cancel path leaves `sessionActive` stuck; stop/hide paths look correct |
| `models_keep_loaded` toggles unload | **DEFERRED** (no device); code path present |
| `CrashLogsScreen` lists/shares | **DEFERRED** (no device); UI wired |
| Docs: release-signing + manual-test-matrix | PASS |
| Battery baseline recorded | **PENDING** (documented) |
| Inventory + plans README DONE | PASS |

## Risks for the human reviewer

1. **Blocker: cancel → no unload** — must fix `onSessionCancel` before trusting battery/lifecycle acceptance.
2. **Manual / device suite not run** — crash file write, idle unload logcat, keep-loaded toggle, 5-app matrix, release sideload smoke, 30-min battery mAh — all need S22 (or equivalent) before daily-use adoption.
3. **Release JNI under R8** — `assembleRelease` builds; on-device ASR init after minify still unproven.
4. **Crash logcat dump** — `CrashHandler` shells `logcat -d`; may be empty/restricted on some devices; stack trace still written.
5. **CI** — confirm GitHub `build` green on PR #25 before approve/merge.

## PR

Already open: https://github.com/SheehanT98/GallopKeyboard/pull/25 — no `devteam:open-pr` needed.

## Advance

Review artifact ready → `npm run devteam:advance -- job-010 --to double_checking`  
(Orchestrator / human: after double-check, prefer `/devteam revise job-010` with the cancel-path fix note rather than merge.)
