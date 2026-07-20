# Code summary — job-020 / Plan 029

## Summary

Finished privacy hardening: disabled Android Auto Backup and scrubbed PII from
always-on Timber/Log call sites and crash artifacts.

## Changes

### Disable Auto Backup

- Set `android:allowBackup="false"` on `<application>`.
- Added deny-all `backup_rules.xml` and `data_extraction_rules.xml`.

### Scrub PII logs

- **KeyboardScreen** — removed `Timber.d` for key labels, swipe words, and accent chars.
- **DictationService** — transcript log now reports char counts only.
- **WhisperContext** — transcription complete log reports char count only.
- **ParakeetEngine** — `finalize` log reports char count only.
- **CrashHandler** — removed `logcat -d` radio tail from crash `.txt` files.

### verify.sh guards

Fail-closed greps for `allowBackup="false"`, forbidden PII patterns, and
`logcat` in `CrashHandler`.

## Files changed

| File | Why |
|------|-----|
| `app/src/main/AndroidManifest.xml` | `allowBackup="false"` + backup rule refs |
| `app/src/main/res/xml/backup_rules.xml` | **New** — deny-all full backup |
| `app/src/main/res/xml/data_extraction_rules.xml` | **New** — deny cloud/device transfer |
| `ime/.../ui/KeyboardScreen.kt` | Remove keystroke/swipe/accent PII logs |
| `app/.../service/DictationService.kt` | Redact transcript body from logs |
| `whisper/.../WhisperContext.kt` | Redact transcript body from logs |
| `asr/.../parakeet/ParakeetEngine.kt` | Redact finalize transcript body |
| `core/.../log/CrashHandler.kt` | Drop logcat dump from crash files |
| `scripts/verify.sh` | Fail-closed privacy grep guards |
| `docs/dictus-inventory.md` | Plan 029 inventory |
| `plans/README.md` | Plan 029 status → DONE |

## Verification

```bash
source scripts/android-env.sh
rg -n 'allowBackup' app/src/main/AndroidManifest.xml
rg -n 'Key pressed|Swipe word committed' ime/src/main  # no matches
./gradlew :app:testDebugUnitTest :core:testDebugUnitTest :ime:testDebugUnitTest
bash scripts/verify.sh
```

All passed (`BUILD SUCCESSFUL`, `verify.sh` → `OK`).

## Drift check note

Plan drift check base commit `32b0d20` is not present in this clone; proceeded
after confirming Phase 9 code (`voiceStopScope`) exists on `origin/main`.

## Blockers

None.
