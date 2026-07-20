# Job 020 — Double-check

| Field | Value |
|-------|-------|
| **Job** | job-020 |
| **Branch** | `cursor/devteam-job-020-execute-plan-029-finish-privacy-backup-and-log-s-c1fc` |
| **PR** | [#48](https://github.com/SheehanT98/GallopKeyboard/pull/48) |
| **Plan** | `plans/029-finish-privacy-backup-and-log-scrub.md` |
| **Double-checker** | devteam-double-checker (composer-2.5) |
| **Checked at** | 2026-07-20T21:58:00Z |
| **SHA checked** | `3588aef` |
| **Review verdict** | APPROVE (`04-review.md`) |
| **Verdict** | **READY** |

## Summary

Cold re-verification of Plan 029 done criteria confirms Auto Backup is disabled
with deny-all extraction rules, keystroke/swipe/accent and transcript PII is
scrubbed from always-on log sites, CrashHandler no longer embeds `logcat -d`,
and `verify.sh` has fail-closed privacy guards. Reviewer findings confirmed; no
blocking issues. **READY** for human merge after CI is green.

## Done criteria (independent re-run)

| Criterion | Result | Evidence |
|-----------|--------|----------|
| `allowBackup="false"` present | **PASS** | manifest line 22; deny-all `backup_rules.xml` + `data_extraction_rules.xml` |
| No keystroke/swipe-word Timber in `ime` production sources | **PASS** | `rg` no matches for `Key pressed`, `Swipe word committed`, `Accent selected:` |
| CrashHandler does not exec `logcat -d` | **PASS** | `readRadioLogTail()` removed; no `logcat` in `CrashHandler.kt` |
| `verify.sh` guards fail if those regress | **PASS** | four new fail-closed greps at lines 36–57 |
| `bash scripts/verify.sh` → `OK` | **PASS** | exit 0, ends with `OK` (assembleDebug + testAll + lint + greps) |
| Scope respected | **PASS** | diff limited to manifest/XML, log scrub sites, verify guards, inventory, plans index |

## Review confirmation (`04-review.md`)

| Review finding | Confirmed |
|----------------|-----------|
| DictationService / Whisper / Parakeet transcript logs → char counts only | **Yes** |
| Settings export path still exists; crash share has stack + thread only | **Yes** (code inspection) |
| Manual export smoke not run on device | **Yes** — non-blocking; recommend S22 check before sharing logs |
| Guards are pattern-specific | **Yes** — acceptable for this plan |
| Pins plaintext at rest | **Yes** — explicitly out of scope |

## STOP conditions

| Condition | Outcome |
|-----------|---------|
| Product owner requires backup for pin migrate | **Not hit** — `allowBackup="false"` applied |
| Log site required for failing CI test | **Not hit** — redacted logs; tests pass |
| Phase 9 code missing (`voiceStopScope` absent) | **Not hit** — Phase 9 present on branch |

## Commands run (cold)

| Check | Command | Result |
|-------|---------|--------|
| Branch / SHA | `git branch --show-current`; `git rev-parse HEAD` | correct branch; `3588aef` |
| allowBackup grep | `rg -n 'allowBackup' app/src/main/AndroidManifest.xml` | `22: android:allowBackup="false"` |
| PII keystroke grep | `rg -n 'Key pressed\|Swipe word committed' ime/src/main` | no matches |
| PII/logcat scope grep | `rg -n 'Key pressed\|Swipe word committed\|logcat' ime/... DictationService.kt CrashHandler.kt` | no matches |
| Unit tests | `./gradlew :app:testDebugUnitTest :core:testDebugUnitTest :ime:testDebugUnitTest` | BUILD SUCCESSFUL |
| Full verify | `bash scripts/verify.sh` | exit 0, `OK` |

## CI / human gate

Confirm PR #48 CI green before `/devteam approve job-020`. Manual check on S22:
Settings → export logs after typing/swiping/dictating — no key labels, swipe
words, or transcript bodies; sample crash `.txt` has no embedded logcat section.

## Advance

`npm run devteam:advance -- job-020 --to awaiting_review`
