# Job 020 — Code stage

## Summary

Implemented Plan 029: disable Auto Backup and scrub keystroke/transcript PII
from file logs and crash artifacts.

## Files changed

| File | Change |
|------|--------|
| `app/src/main/AndroidManifest.xml` | `allowBackup="false"`; link deny-all backup XML rules |
| `app/src/main/res/xml/backup_rules.xml` | **New** — empty full-backup content |
| `app/src/main/res/xml/data_extraction_rules.xml` | **New** — exclude all domains from cloud/device transfer |
| `ime/.../ui/KeyboardScreen.kt` | Removed `Timber.d` for keys, swipe words, accents |
| `app/.../service/DictationService.kt` | Transcript log → char counts only |
| `whisper/.../WhisperContext.kt` | Transcription log → char count only |
| `asr/.../parakeet/ParakeetEngine.kt` | Finalize log → char count only |
| `core/.../log/CrashHandler.kt` | Removed `logcat -d` tail from crash files |
| `scripts/verify.sh` | Privacy grep guards |
| `docs/dictus-inventory.md` | Plan 029 additions |
| `plans/README.md` | Plan 029 → DONE |

## Verification

```bash
source scripts/android-env.sh
./gradlew :app:testDebugUnitTest :core:testDebugUnitTest :ime:testDebugUnitTest
bash scripts/verify.sh
```

All passed (`BUILD SUCCESSFUL`, `verify.sh` → `OK`).
