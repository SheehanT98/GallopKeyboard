# Plan 029: Finish privacy — disable Auto Backup and scrub PII logs

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 32b0d20..HEAD -- app/src/main/AndroidManifest.xml core/src/main/java/com/gallopkeyboard/core/log/CrashHandler.kt core/src/main/java/com/gallopkeyboard/core/logging/TimberSetup.kt ime/src/main/java/com/gallopkeyboard/ime/ui/KeyboardScreen.kt app/src/main/java/com/gallopkeyboard/service/DictationService.kt whisper/src/main/java/com/gallopkeyboard/whisper/WhisperContext.kt asr/src/main/java/com/k2fsa/sherpa/onnx/ scripts/verify.sh`
> If Phase 9 code is missing on your branch (`voiceStopScope` absent), merge
> PRs #43–#47 (or equivalent) before continuing — this plan assumes 024–028
> landed. If in-scope files drifted vs excerpts below, STOP.
>
> **Supersedes**: Plan 022 was marked DONE in the index but **never merged**
> (deep #3 audit). Do not skip this because 022 says DONE.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none (after Phase 9 on main)
- **Category**: security
- **Planned at**: commit `32b0d20` (post–Phase 9 audit merge of #43–#47 onto `489699a`), 2026-07-20

## Why this matters

The product claims 100% on-device privacy, but:

1. `AndroidManifest.xml` omits `android:allowBackup` → platform **default
   allows backup** of DataStore (pins, last transcription, personal dict) and
   `filesDir/dictus.log`.
2. Timber/Log call sites still record **raw keys, swipe words, and
   transcripts** into an always-on file log; CrashHandler appends `logcat -d`
   tails into shareable crash files.

Plan 022 specified this work; deep #3 confirmed it is still absent.

## Current state

```xml
<!-- app/src/main/AndroidManifest.xml — <application> has no allowBackup -->
<application
    android:name="com.gallopkeyboard.DictusApplication"
    ...
    android:theme="@style/Theme.AppCompat.NoActionBar">
```

```kotlin
// ime/.../ui/KeyboardScreen.kt (~99, ~176)
Timber.d("Swipe word committed: %s", word)
Timber.d("Key pressed: %s", key.label)

// core/.../log/CrashHandler.kt (~76-78)
Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", RADIO_LOG_LINES.toString()))
```

`rg allowBackup app/` → no matches. Settings can export `dictus.log`.

**Conventions**: Offline privacy (`AGENTS.md` Do NOT / CONTEXT). Match
`scripts/verify.sh` grep-guard style for new checks. Never log secret values
in plans or commits.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Verify | `bash scripts/verify.sh` | `OK` |
| Grep backup | `rg -n 'allowBackup' app/src/main/AndroidManifest.xml` | shows `false` |
| Grep PII logs | `rg -n 'Key pressed|Swipe word committed' ime/src/main` | no matches |
| Unit | `./gradlew :app:testDebugUnitTest :core:testDebugUnitTest :ime:testDebugUnitTest` | BUILD SUCCESSFUL |

## Scope

**In scope**:
- `app/src/main/AndroidManifest.xml` — `allowBackup="false"` (+ optional
  deny-all `dataExtractionRules` / `fullBackupContent` XML if easy)
- `ime/.../ui/KeyboardScreen.kt` — remove keystroke/swipe word Timber.d
- `app/.../service/DictationService.kt` — scrub raw transcript Timber if present
- `whisper/.../WhisperContext.kt` and/or `asr/...` finalize Log.d of full text —
  remove or gate behind `BuildConfig.DEBUG`
- `core/.../log/CrashHandler.kt` — drop `logcat -d` dump from crash files
- `scripts/verify.sh` — fail-closed greps for `allowBackup="false"` and
  forbidden PII log patterns
- `docs/dictus-inventory.md` Plan 029 additions
- `plans/README.md`

**Out of scope**:
- Encrypting pins at rest (follow-up after backup-off)
- Replacing Timber entirely
- Log file rotation (nice-to-have; note if easy)

## Git workflow

- Branch: `cursor/029-finish-privacy-backup-and-log-scrub`
- Commit: `fix(security): disable backup; scrub keystroke/transcript logs`
- Do NOT push/PR unless instructed.

## Steps

### Step 1: Disable Auto Backup

On `<application>` in `app/src/main/AndroidManifest.xml`:

```xml
android:allowBackup="false"
```

Optionally add `android:fullBackupContent="@xml/backup_rules"` /
`android:dataExtractionRules="@xml/data_extraction_rules"` with deny-all
stubs under `app/src/main/res/xml/` if targeting API 31+ extraction cleanly.

**Verify**: `rg -n 'allowBackup' app/src/main/AndroidManifest.xml` → `false`.

### Step 2: Scrub PII Timber/Log call sites

1. Remove or redact `Timber.d("Key pressed…")` and `Timber.d("Swipe word…")`
   (and accent char dumps) in `KeyboardScreen.kt`.
2. Scrub DictationService / Whisper / Parakeet sites that log full transcript
   strings — prefer delete; if diagnostics needed, `if (BuildConfig.DEBUG)`
   with length-only message (no body).
3. In `CrashHandler`, remove `readRadioLogTail()` / `logcat -d` from crash
   file content; keep stack trace + thread metadata.

**Verify**:
`rg -n 'Key pressed|Swipe word committed|logcat' ime/src/main app/src/main/java/com/gallopkeyboard/service core/src/main/java/com/gallopkeyboard/core/log`
→ no keystroke/transcript dumps; no logcat exec in CrashHandler.

### Step 3: verify.sh guards

Add fail-closed checks (mirror style of existing greps):

- Manifest contains `android:allowBackup="false"`
- Forbidden patterns in production sources (adjust list to match removed sites)

**Verify**: `bash scripts/verify.sh` → `OK`.

### Step 4: Inventory

Document Plan 029: backup off; which log sites removed; crash files no longer
embed logcat.

## Test plan

- No new unit tests required if purely config/log deletion; smoke compile.
- Manual: Settings → export logs after typing — must not contain key labels /
  swipe words / full transcripts.

## Done criteria

- [ ] `allowBackup="false"` present
- [ ] No keystroke/swipe-word Timber in `ime` production sources
- [ ] CrashHandler does not exec `logcat -d`
- [ ] `verify.sh` guards fail if those regress
- [ ] `bash scripts/verify.sh` → `OK`
- [ ] Scope respected

## STOP conditions

- Product owner requires backup for pin migrate — document and keep
  `allowBackup="false"` anyway unless they override in writing.
- A log site is required for a failing CI test — redact payload, don’t keep
  plaintext transcript.

## Maintenance notes

- Encrypt-at-rest for pins can follow once backup is off.
- Reviewer: confirm Settings log export + crash share paths.
- Mark Plan 022 superseded by 029 in the index (022 DONE claim was false).
