# Job 010 — Test report

| Field | Value |
|-------|-------|
| **Job** | job-010 |
| **Branch** | `cursor/devteam-job-010-execute-plan-010-hardening-battery-crashes-relea-c1fc` |
| **PR** | [#25](https://github.com/SheehanT98/GallopKeyboard/pull/25) |
| **Tester** | devteam-tester (composer-2.5) |
| **Tested at** | 2026-07-16T19:35:00Z |
| **SHA tested** | `d068e978636eaa03f3c95be0367ddacb8c9e7396` |
| **Verdict** | **PASS** (automated); manual on-device deferred |

## Environment

```bash
source scripts/android-env.sh
# ANDROID_HOME=/opt/android-sdk
# JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

| Check | Command | Result |
|-------|---------|--------|
| Branch | `git branch --show-current` | on job-010 branch |
| Device | `adb devices` | no devices attached |

## Commands run

| Check | Command | Result |
|-------|---------|--------|
| Drift guard | `bash scripts/verify.sh` | exit 0, printed `OK` |
| Plan 010 inventory | `grep -q "Plan 010 additions" docs/dictus-inventory.md` | OK |
| Release build | `./gradlew --no-daemon assembleRelease` | BUILD SUCCESSFUL |
| APK sizes | `du -h app/build/outputs/apk/{debug,release}/*.apk` | debug 157M, release 88M |
| Plan status | `plans/README.md` row 010 | DONE |

### verify.sh breakdown

- `assembleDebug` — BUILD SUCCESSFUL
- `testAll` — BUILD SUCCESSFUL
- `lint` — BUILD SUCCESSFUL
- Dictus package grep guard — no hits outside `third_party/`
- Model binary grep guard — no hits outside `third_party/`
- `System.out.println` guard — no hits
- Log.d hot-path advisory — no WARN emitted

### Release APK notes

- `assembleRelease` succeeds with debug signing fallback (no `~/.gallopkeyboard/keystore.properties` in CI).
- Release APK 88M vs debug 157M — native `.so` footprint; not a ProGuard regression (R8 minify/shrink enabled).

## Done criteria (Plan 010)

| Criterion | Result |
|-----------|--------|
| `bash scripts/verify.sh` exits 0 with new guards | PASS |
| `./gradlew assembleRelease` succeeds | PASS |
| `CrashHandler` writes on forced exception | **NOT RUN** — no device |
| `ModelLifecycleManager` unloads after 60 s idle | **NOT RUN** — no device |
| `models_keep_loaded` toggles unload behavior | **NOT RUN** — no device |
| `CrashLogsScreen` lists/shares crash files | **NOT RUN** — no device |
| `docs/release-signing.md` exists | PASS |
| `docs/manual-test-matrix.md` exists | PASS |
| Battery baseline recorded in manual-test-matrix | **PENDING** — owner S22 run documented |
| `docs/dictus-inventory.md` "Plan 010 additions" | PASS |
| `plans/README.md` row 010 `DONE` | PASS |

## STOP conditions (coder notes)

| Condition | Outcome |
|-----------|---------|
| R8 breaks JNI | **Not hit** — `assembleRelease` succeeds |
| Release APK > 30 MB bare | **Noted** — 88M due to bundled native libs (expected); not disabling R8 |
| StrictMode floods logcat | **Not hit** in CI build |
| Model unload UX | **Not hit** — 60 s default retained |
| Battery > 300 mAh | **Not hit** — requires physical S22 (pending) |
| No release keystore | **Not hit** — debug signing fallback works |

## Manual on-device (deferred)

Plan acceptance tests require Galaxy S22 sideload per `docs/manual-test-matrix.md`. Not executed in this session.

| # | Action | Result |
|---|--------|--------|
| 1 | 30-min battery run (unplugged, mixed session) | not run |
| 2 | Force-kill during recording → crash log written | not run |
| 3 | Idle 90 s in voice panel → `ModelLifecycleManager` unload in logcat | not run |
| 4 | `models_keep_loaded` on → idle test → no unload | not run |
| 5 | Cross-app smoke matrix (5 apps) | not run |
| 6 | Release APK install + smoke vs debug | not run |

## Blockers

- **Manual hardening acceptance (non-blocking for automated gate):** Battery profiling, crash-handler write, model lifecycle unload, CrashLogsScreen UX, and release APK on-device smoke not run — no `adb` device attached. Reviewer or owner should validate per Plan 010 manual test plan before personal daily-use adoption.

## Advance

Automated verification passed → `npm run devteam:advance -- job-010 --to reviewing`

---

## Re-test after revise (cancel → lifecycle unload fix)

| Field | Value |
|-------|-------|
| **Trigger** | Review revise — `PolishingTranscriber.onSessionCancel` must call `lifecycleManager.onSessionStopped()` so cancel does not leave models loaded |
| **Tested at** | 2026-07-16T19:50:13Z |
| **SHA tested** | `efe8fa28113498e372145a7d88db2130d22438c6` |
| **Verdict** | **PASS** |

### Commands run

| Check | Command | Result |
|-------|---------|--------|
| Drift guard | `source scripts/android-env.sh && bash scripts/verify.sh` | exit 0, printed `OK` |
| Cancel/lifecycle unit tests | `./gradlew :ime:testDebugUnitTest --tests 'com.gallopkeyboard.ime.asr.PolishingTranscriberTest' --tests 'com.gallopkeyboard.ime.asr.StreamingTranscriberTest'` | BUILD SUCCESSFUL |

### Targeted unit tests

| Suite | Tests | Failures | Notes |
|-------|-------|----------|-------|
| `PolishingTranscriberTest` | 7 | 0 | `cancel during recording…` asserts `lifecycle.sessionStoppedCount >= 1`; `session start and stop notify lifecycle manager` asserts cancel increments `sessionStoppedCount` |
| `StreamingTranscriberTest` | 8 | 0 | baseline cancel behavior unchanged |

### Fix confirmed

`PolishingTranscriber.onSessionCancel` now delegates to `streaming.onSessionCancel(session)` then calls `lifecycleManager.onSessionStopped()`, matching stop-path lifecycle semantics.

### Job status

Left at `double_checking` — orchestrator will run double-check next.
