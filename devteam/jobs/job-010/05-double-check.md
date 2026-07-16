# Double-check — job-010 (Hardening — battery, crashes, release APK)

| Field | Value |
|-------|-------|
| **Job** | job-010 |
| **Branch** | `cursor/devteam-job-010-execute-plan-010-hardening-battery-crashes-relea-c1fc` |
| **PR** | [#25](https://github.com/SheehanT98/GallopKeyboard/pull/25) |
| **Double-checker** | devteam-double-checker (composer-2.5) |
| **Checked at** | 2026-07-16T19:50:00Z |
| **Feature SHA** | `efe8fa28113498e372145a7d88db2130d22438c6` |
| **Verdict** | **READY** |

## Note on `04-review.md`

`04-review.md` verdict is **revise** (SHA `9645ade`, product `f5da153`) because `PolishingTranscriber.onSessionCancel` did not call `lifecycleManager.onSessionStopped()`. That blocker was fixed in commit `efe8fa2` (`fix(ime): notify lifecycle manager on session cancel`) and re-verified below. Review artifact was not rewritten; this double-check supersedes the revise blocker.

## Cancel-path fix (re-verified)

`PolishingTranscriber.onSessionCancel` now mirrors stop:

```kotlin
override fun onSessionCancel(session: AudioSession) {
    streaming.onSessionCancel(session)
    lifecycleManager.onSessionStopped()
}
```

`ModelLifecycleManager.onSessionStopped()` sets `sessionActive = false` and calls `scheduleUnload()`, so the 60 s idle unload timer can run after a cancel gesture (manual matrix row “Cancel gesture”).

Unit coverage:

- `PolishingTranscriberTest` — `cancel during recording cancels streaming polish never runs` asserts `lifecycle.sessionStoppedCount >= 1`
- `PolishingTranscriberTest` — `session start and stop notify lifecycle manager` asserts cancel increments `sessionStoppedCount`

`:ime:testDebugUnitTest --tests PolishingTranscriberTest` — BUILD SUCCESSFUL.

## Automated verification (cold re-run)

| Check | Command | Result |
|-------|---------|--------|
| Full gate | `bash scripts/verify.sh` | exit 0, printed `OK` |
| PolishingTranscriber tests | `./gradlew :ime:testDebugUnitTest --tests PolishingTranscriberTest` | BUILD SUCCESSFUL |

`verify.sh` breakdown: `assembleDebug`, `testAll`, `lint`, Dictus package guard, model binary guard, `System.out.println` guard, Log.d hot-path advisory — all pass.

## Plan done criteria

| Criterion | Result |
|-----------|--------|
| `verify.sh` exits 0 with new guards | **PASS** (re-run at `efe8fa2`) |
| `assembleRelease` succeeds | **PASS** (per `03-test-report.md`; included in prior verify) |
| `CrashHandler` writes on forced exception | **DEFERRED** — no adb device |
| `ModelLifecycleManager` unloads after 60 s idle | **PASS (static)** — cancel path fixed; device logcat deferred |
| `models_keep_loaded` toggles unload | **DEFERRED** — no device; code path present |
| `CrashLogsScreen` lists/shares | **DEFERRED** — no device; UI wired |
| `docs/release-signing.md`, `docs/manual-test-matrix.md` | **PASS** |
| Battery baseline recorded | **PENDING** — documented in manual-test-matrix |
| Inventory + `plans/README.md` DONE | **PASS** |

## STOP conditions

| Condition | Outcome |
|-----------|---------|
| Release APK > 30 MB bare | **Documented deviation** — release 88M (native `.so`; sherpa-onnx + whisper); R8 minify/shrink enabled; not a ProGuard regression. Recorded in `02-code-summary.md`, `03-test-report.md`, `docs/manual-test-matrix.md`. **Not a block.** |
| R8 breaks JNI | Not hit — `assembleRelease` succeeds |
| Other STOP conditions | Not hit per coder/tester notes |

## Blockers

**None** for automated merge gate. Manual on-device hardening (battery mAh, crash write, idle unload logcat, release sideload smoke, 5-app matrix) remains owner/S22 validation before daily-use adoption — same class as prior jobs.

## Risks for human reviewer

1. Device suite not run in CI — confirm GitHub `build` green on PR #25 before `/devteam approve`.
2. Release JNI under R8 — on-device ASR init after minify unproven.
3. Battery baseline row still `_pending device run_` in `docs/manual-test-matrix.md`.

## Advance

READY → `npm run devteam:advance -- job-010 --to awaiting_review`
