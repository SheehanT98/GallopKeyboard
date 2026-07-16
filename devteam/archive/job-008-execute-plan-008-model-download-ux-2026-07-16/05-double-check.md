# Double-check — job-008 (Model download UX)

| Field | Value |
|-------|-------|
| **Job** | job-008 |
| **Branch** | `cursor/devteam-job-008-execute-plan-008-model-download-ux-c1fc` |
| **PR** | [#22](https://github.com/SheehanT98/GallopKeyboard/pull/22) |
| **Double-checker** | devteam-double-checker (composer-2.5) |
| **Checked at** | 2026-07-16T19:12:00Z |
| **SHA** | `b3d179869f07b8cad46df77ec609fd5840531711` |
| **Verdict** | **READY** |

## Cold verification (independent re-run)

| Check | Command / action | Result |
|-------|------------------|--------|
| Full verify gate | `bash scripts/verify.sh` | exit 0, printed `OK` (assemble + testAll + lint + package/model guards) |
| ModelDownloader unit tests | `./gradlew --no-daemon :core:testDebugUnitTest --tests 'com.gallopkeyboard.core.models.ModelDownloaderTest'` | BUILD SUCCESSFUL — **6/6** tests, 0 failures |
| Models doc present | `test -f docs/models.md` | OK |
| Registry spec count | `grep -c ModelSpec ModelRegistry.kt` | 10 (≥ 6) |
| SHA pins (64 hex) | `ModelRegistry.kt` | 6 |
| Plan 008 inventory | `docs/dictus-inventory.md` "Plan 008 additions" | present |
| Plan status | `plans/README.md` row 008 | DONE |
| `docs/models.md` hashes | 6 entries, 64 hex each | OK |

### Planned file presence

| File | Status |
|------|--------|
| `core/.../ModelSpec.kt` | OK |
| `core/.../ModelRegistry.kt` | OK |
| `core/.../ModelDownloader.kt` | OK |
| `core/.../ModelInstaller.kt` | OK |
| `app/.../OnboardingActivity.kt` | OK |
| `app/.../ModelsSettingsScreen.kt` | OK |
| `core/.../ModelDownloaderTest.kt` | OK |
| `ime/.../VoicePanelPromptBanner.kt` | OK |
| `docs/models.md` | OK |
| `docs/dictus-inventory.md` | OK |
| `scripts/verify.sh` | OK |

### ModelDownloaderTest (6/6)

| Test | Result |
|------|--------|
| `happyPath_downloadsRenamesAndVerifies` | pass |
| `shaMismatch_deletesPartAndThrows` | pass |
| `interruptedResume_sendsRangeHeader` | pass |
| `notFound_clearsPart` | pass |
| `progress_emitsMonotonicBytesDoneAndTerminalDone` | pass |
| `isMeteredNetwork_returnsWithoutException` | pass |

## Done criteria (Plan 008)

| Criterion | Result |
|-----------|--------|
| `bash scripts/verify.sh` exits 0 | **PASS** |
| `ModelDownloaderTest` (6 cases) passes | **PASS** |
| Fresh install → onboarding completes on device | **DEFERRED** (no adb device) |
| IME banner deep-links to onboarding | **DEFERRED** (no adb device) |
| Settings lists specs with statuses | **DEFERRED** (no adb device) |
| Resume-download manual test | **DEFERRED** (no adb device) |
| `docs/models.md` real SHA-256 (64 hex) | **PASS** |
| `docs/dictus-inventory.md` Plan 008 additions | **PASS** |
| `plans/README.md` DONE | **PASS** |

## Review confirmation (`04-review.md`)

- Reviewer verdict: **approve**, no blockers.
- Scope compliance: all in-scope items implemented; out-of-scope respected.
- Acceptable deviations noted (VoiceSetupPrefs/Intents, skip button UX, WhisperConfig tier selection).
- Automated evidence from review reconfirmed independently on SHA `b3d1798`.

## Blockers

**None** for automated gate / awaiting_review.

**Human pre-merge risks** (non-blocking, carried from review):

1. Manual on-device download/onboarding (~220 MB Wi-Fi flow, metered warning, resume, corrupt→Fix, delete-all→IME banner) not executed — no adb device in CI or agent environment.
2. Tier switch deletes old Whisper before new download completes — mid-failure recovery depends on Fix/Retry UX.
3. HTTP 416 on resume requires user Retry (`.part` cleared).

## Advance

Automated verification green → `npm run devteam:advance -- job-008 --to awaiting_review`

Human: `/devteam approve job-008` when CI is green (validate manual download UX on device if possible before merge).
