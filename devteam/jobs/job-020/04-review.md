# Review — job-020 / Plan 029

| Field | Value |
|-------|-------|
| **Job** | job-020 |
| **Branch** | `cursor/devteam-job-020-execute-plan-029-finish-privacy-backup-and-log-s-c1fc` |
| **PR** | [#48](https://github.com/SheehanT98/GallopKeyboard/pull/48) |
| **Plan** | `plans/029-finish-privacy-backup-and-log-scrub.md` |
| **Reviewer** | devteam-reviewer (cursor-grok-4.5-high) |
| **Reviewed at** | 2026-07-20T21:55:00Z |
| **SHA reviewed** | `c062299e9d0f342597a003190c1ec88b82051a0f` |
| **Verdict** | **APPROVE** |

## Summary

Approve. Plan 029 done criteria are met: Auto Backup is off with deny-all
extraction rules, keystroke/swipe/transcript PII is scrubbed from always-on
log sites, CrashHandler no longer embeds `logcat -d`, and `verify.sh` has
fail-closed guards. Scope is tight; no STOP conditions hit.

## Scope compliance

| In-scope item | Status |
|---------------|--------|
| `allowBackup="false"` + optional deny-all XML | **Done** — manifest + `backup_rules.xml` + `data_extraction_rules.xml` |
| KeyboardScreen keystroke/swipe/accent Timber | **Done** — removed |
| DictationService transcript Timber | **Done** — length-only |
| Whisper / Parakeet finalize text logs | **Done** — length-only |
| CrashHandler `logcat -d` | **Done** — removed |
| `scripts/verify.sh` greps | **Done** — four fail-closed checks |
| `docs/dictus-inventory.md` + `plans/README.md` | **Done** — Plan 029 section; 029 → DONE; 022 already `SUPERSEDED → 029` |

Out of scope respected (no pin encryption, no Timber rewrite, no log rotation).

No AGENTS.md violations (offline privacy strengthened; no cloud/telemetry).

## Done criteria checklist

| Criterion | Result |
|-----------|--------|
| `allowBackup="false"` present | **PASS** |
| No keystroke/swipe-word Timber in `ime` production sources | **PASS** |
| CrashHandler does not exec `logcat -d` | **PASS** |
| `verify.sh` guards fail if those regress | **PASS** (guards present; reviewer re-ran privacy greps) |
| `bash scripts/verify.sh` → `OK` | **PASS** (tester evidence at `350cd42`) |
| Scope respected | **PASS** |

## Verification evidence

- Tester report (`03-test-report.md`): unit tests + full `verify.sh` **PASS** at
  `350cd42`.
- Reviewer re-confirmed privacy greps locally:
  - `android:allowBackup="false"` in manifest
  - no `Key pressed` / `Swipe word committed` / `Accent selected:` in `ime`
  - no raw transcript / `finalize: "` body patterns in app/whisper/asr
  - no `logcat` in `CrashHandler.kt`
- Settings export path still exists (`LogExporter` → `dictus.log` ZIP);
  crash share path (`CrashLogsScreen` → `filesDir/crashes/*.txt`) now
  contains stack + thread metadata only (no radio log tail).

## Findings

### Non-blocking notes

1. **Manual export smoke not run on device** — plan’s Settings → export-logs
   check is correct by code inspection; confirm on S22 before trusting a
   shared `dictus.log` after typing/dictation.
2. **Guards are pattern-specific** — new PII log formats (e.g. different
   format strings) would not trip `verify.sh`; acceptable for this plan.
3. **Pins remain plaintext at rest** — explicitly out of scope; follow-up
   after backup-off.

No blocking or revise-required findings.

## Risks for the human reviewer

- Confirm on-device that exported debug logs after typing/swiping/dictating
  contain no key labels, swipe words, or transcript bodies.
- Confirm a sample crash `.txt` (if any) has no embedded logcat section.
- Residual: always-on Timber file logging still records non-PII diagnostics;
  encrypt-at-rest for pins/last transcription is still deferred.

## Advance

`npm run devteam:advance -- job-020 --to double_checking`
