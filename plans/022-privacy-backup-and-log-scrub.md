# Plan 022: Disable Auto Backup and scrub keystroke/transcript logging

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 86dfd89..HEAD -- app/src/main/AndroidManifest.xml core/src/main/java/com/gallopkeyboard/core/logging/TimberSetup.kt core/src/main/java/com/gallopkeyboard/core/log/CrashHandler.kt ime/src/main/java/com/gallopkeyboard/ime/ui/KeyboardScreen.kt ime/src/main/java/com/gallopkeyboard/ime/DictusImeService.kt app/src/main/java/com/gallopkeyboard/service/DictationService.kt scripts/verify.sh`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: security
- **Planned at**: commit `86dfd89`, 2026-07-20

## Why this matters

GallopKeyboard markets 100% on-device privacy, but:

1. `app/src/main/AndroidManifest.xml` omits `android:allowBackup`, so the
   platform **default allows backup** — DataStore pinned clipboard plaintext
   and `filesDir/dictus.log` can leave the device via ADB/cloud backup.
2. Always-on `FileLoggingTree` plus Timber/Log call sites record **raw keys,
   swipe words, and transcripts**, which Settings can export.

This was deferred in the 2026-07-17 audit; it is now in the default top set
because it is small, high confidence, and undermines the product story.

**Do not reproduce any secret values** if found — reference `file:line` and
type only.

## Current state

```xml
<!-- app/src/main/AndroidManifest.xml — no allowBackup / dataExtractionRules -->
<application
    android:name="com.gallopkeyboard.DictusApplication"
    ...>
```

```kotlin
// KeyboardScreen.kt
Timber.d("Key pressed: %s", key.label)
Timber.d("Swipe word committed: %s", word)

// DictusImeService.kt (legacy dictation path)
Timber.d("Transcribed text inserted: '%s'", text)
```

`PinnedClipboardStore` KDoc already documents on-device plaintext pins —
encrypting pins is **out of scope** here (larger migration); backup disable
is the cheap containment.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Grep guard (after change) | `rg -n 'Timber\.d\(.*(Key pressed|Swipe word|Transcribed text)' ime app` | no matches |
| Unit tests | `./gradlew :core:testDebugUnitTest :ime:testDebugUnitTest` | BUILD SUCCESSFUL |
| Verify | `bash scripts/verify.sh` | `OK` |

## Scope

**In scope**:
- `app/src/main/AndroidManifest.xml`
- Optional: `app/src/main/res/xml/data_extraction_rules.xml` and/or
  `backup_rules.xml` if you set explicit rules instead of only
  `allowBackup="false"`
- Call sites that log raw key/swipe/transcript strings in `ime/` and `app/`
  (and ASR `Log.d` transcript lines if trivially found)
- `core/.../CrashHandler.kt` — stop embedding full `logcat -d` dumps **or**
  document + strip; prefer remove logcat dump
- `scripts/verify.sh` — add a fail-fast grep for forbidden PII log patterns
- `docs/limitations.md` — one short note that backup is disabled intentionally
- `docs/dictus-inventory.md` Plan 022 additions
- `plans/README.md`

**Out of scope**:
- Encrypting `PinnedClipboardStore` at rest (follow-up)
- Legacy `ModelDownloader` SHA gap (SEC-01 — separate plan)
- Release signing fail-closed (SEC-06 — separate)
- Turning off all Timber in debug builds (keep non-PII diagnostics)

## Git workflow

- Branch: `advisor/022-privacy-backup-and-log-scrub`
- Commit: `fix(security): disable backup; scrub keystroke/transcript logs`
- Do NOT push/PR unless instructed.

## Steps

### Step 1: Disable Auto Backup

On the `<application>` element in `app/src/main/AndroidManifest.xml`:

```xml
android:allowBackup="false"
android:fullBackupContent="false"
```

For API 31+ devices, also add:

```xml
android:dataExtractionRules="@xml/data_extraction_rules"
```

Create `app/src/main/res/xml/data_extraction_rules.xml` that disables cloud
backup/transfer (standard empty deny template). If the project already has
backup XML, update rather than duplicate.

**Verify**: `rg -n 'allowBackup' app/src/main/AndroidManifest.xml` shows
`false`.

### Step 2: Scrub PII from production logs

1. Remove or rewrite call sites that interpolate user text:
   - `KeyboardScreen.kt` — key label / swipe word Timber lines → delete or
     log only key **type** / non-text metadata (`CHARACTER`, length).
   - `DictusImeService.kt` — transcribed text Timber → log
     `transcribed_chars=${text.length}` only.
   - Grep for similar patterns:
     `rg -n 'Timber\.(d|v|i|w)\(.*%.s' ime app asr whisper` and
     `rg -n 'Log\.d\([^)]*(partial|transcript|text)' ime app asr whisper`
   - Fix high-confidence transcript/key sites; leave unrelated debug alone.

2. Prefer **no** raw character content in release-reachable trees. Debug-only
   logs must be wrapped in `if (BuildConfig.DEBUG)` **and** still avoid
   full transcripts if possible.

**Verify**: greps in Commands table return no matches for the known bad
strings.

### Step 3: CrashHandler — drop logcat dump

In `CrashHandler.kt`, remove the `Runtime.exec("logcat ...")` capture into
crash files (or replace with a short in-app breadcrumb that never includes
editor text). Keep exception stack + app version metadata.

**Verify**: `./gradlew :core:testDebugUnitTest --tests '*Crash*'` if present;
else compile `:core:compileDebugKotlin`.

### Step 4: verify.sh guard

Add a fail-fast section (like the existing `com.dictus` grep):

```bash
echo "==> no keystroke/transcript PII in Timber/Log"
if rg -n 'Timber\.d\("Key pressed:|Timber\.d\("Swipe word committed:|Transcribed text inserted:' \
     ime/src/main app/src/main 2>/dev/null; then
  echo "FAIL: PII log pattern found"; exit 1
fi
```

Use patterns that match what you removed so the guard stays meaningful.

**Verify**: `bash scripts/verify.sh` → `OK`.

### Step 5: Docs + index

Note in `docs/limitations.md` under a short Privacy section: Auto Backup is
disabled; clipboard pins remain on-device plaintext until a future encrypt
plan. Append Plan 022 to inventory; mark DONE in `plans/README.md`.

## Test plan

- No new flaky device tests required.
- Existing `TimberSetupTest` / crash tests still pass after CrashHandler change.
- Manual: Settings → export logs after typing — exported file must not contain
  recent key contents.

## Done criteria

- [ ] `allowBackup="false"` (and data-extraction rules if added)
- [ ] Known keystroke/transcript Timber patterns removed
- [ ] Crash files no longer append raw logcat tails
- [ ] `scripts/verify.sh` fails if patterns return
- [ ] `bash scripts/verify.sh` → `OK`
- [ ] Scope respected

## STOP conditions

- Product owner intentionally relies on backup restore for pinned clipboard —
  stop and report (switch to exclusion rules instead of full disable).
- Scrubbing requires redesigning the entire logging architecture — do the
  known call sites + guard only; stop if scope balloons.

## Maintenance notes

- Follow-up: encrypt pins (SEC-03); SHA-verify legacy downloader (SEC-01);
  release signing fail-closed (SEC-06).
- Reviewer: ensure debug builds remain debuggable without reintroducing
  transcript logs in hot paths.
