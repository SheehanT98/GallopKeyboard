# Job 020 — Test report

| Field | Value |
|-------|-------|
| **Job** | job-020 |
| **Branch** | `cursor/devteam-job-020-execute-plan-029-finish-privacy-backup-and-log-s-c1fc` |
| **PR** | [#48](https://github.com/SheehanT98/GallopKeyboard/pull/48) |
| **Plan** | `plans/029-finish-privacy-backup-and-log-scrub.md` |
| **Tester** | devteam-tester (composer-2.5) |
| **Tested at** | 2026-07-20T21:52:00Z |
| **SHA tested** | `350cd4229d21fddff48151a1778b1ef7f1d7ca51` |
| **Verdict** | **PASS** |

## Environment

| Check | Command | Result |
|-------|---------|--------|
| Branch | `git branch --show-current` | `cursor/devteam-job-020-execute-plan-029-finish-privacy-backup-and-log-s-c1fc` |
| Android env | `source scripts/android-env.sh` | `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`, `ANDROID_HOME=/opt/android-sdk` |
| Job status | `devteam/jobs/job-020/meta.json` | `testing` |

## Commands run

| Check | Command | Result |
|-------|---------|--------|
| Drift check | `git diff --stat 32b0d20..HEAD -- <in-scope files>` | Skipped — base commit `32b0d20` not in clone; Phase 9 code confirmed on branch |
| allowBackup grep | `rg -n 'allowBackup' app/src/main/AndroidManifest.xml` | `22: android:allowBackup="false"` |
| PII keystroke grep | `rg -n 'Key pressed\|Swipe word committed' ime/src/main` | No matches (exit 1) |
| PII/logcat scope grep | `rg -n 'Key pressed\|Swipe word committed\|logcat' ime/src/main app/.../DictationService.kt core/.../CrashHandler.kt` | No matches (exit 1) |
| Unit tests | `./gradlew :app:testDebugUnitTest :core:testDebugUnitTest :ime:testDebugUnitTest` | BUILD SUCCESSFUL |
| Full verify | `bash scripts/verify.sh` | exit 0, ends with `OK` |

### verify.sh guard output (privacy checks)

```
==> allowBackup disabled
==> no keystroke/swipe PII logs in ime
==> no raw transcript logs in app/whisper/asr
==> CrashHandler does not dump logcat
OK
```

## Done criteria (Plan 029)

| Criterion | Result |
|-----------|--------|
| `allowBackup="false"` present | **PASS** |
| No keystroke/swipe-word Timber in `ime` production sources | **PASS** |
| CrashHandler does not exec `logcat -d` | **PASS** |
| `verify.sh` guards fail if those regress | **PASS** — new grep guards present and passing |
| `bash scripts/verify.sh` → `OK` | **PASS** |
| Scope respected | **PASS** — backup rules XML, log scrub sites, inventory, plans index only |
| Inventory + README updated | **PASS** — `docs/dictus-inventory.md` Plan 029; `plans/README.md` row 029 → `DONE` |

## STOP conditions

| Condition | Outcome |
|-----------|---------|
| Product owner requires backup for pin migrate | **Not hit** — `allowBackup="false"` applied |
| Log site required for failing CI test | **Not hit** — logs redacted to char counts, tests pass |
| Phase 9 code missing (`voiceStopScope` absent) | **Not hit** — Phase 9 present on branch |

## Blockers

None.

## Advance

`npm run devteam:advance -- job-020 --to reviewing`
