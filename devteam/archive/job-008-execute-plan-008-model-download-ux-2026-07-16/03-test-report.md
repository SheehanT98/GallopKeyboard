# Job 008 — Test report

| Field | Value |
|-------|-------|
| **Job** | job-008 |
| **Branch** | `cursor/devteam-job-008-execute-plan-008-model-download-ux-c1fc` |
| **PR** | [#22](https://github.com/SheehanT98/GallopKeyboard/pull/22) |
| **Tester** | devteam-tester (composer-2.5) |
| **Tested at** | 2026-07-16T19:05:00Z |
| **SHA tested** | `b157271bb8379d0bb18378c1cd00b08d85f4795e` |
| **Verdict** | **PASS** (automated); manual on-device deferred |

## Environment

```bash
npm run android:setup   # SDK marker present, toolchain ready
source scripts/android-env.sh
# ANDROID_HOME=/opt/android-sdk
# JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

| Check | Command | Result |
|-------|---------|--------|
| Branch | `git branch --show-current` | on job-008 branch |
| Device | `adb devices` | no devices attached |

## Commands run

| Check | Command | Result |
|-------|---------|--------|
| Drift guard | `bash scripts/verify.sh` | exit 0, printed `OK` |
| Models doc | `test -f docs/models.md` | OK |
| Registry specs | `grep -c ModelSpec core/.../ModelRegistry.kt` | 10 (≥ 6) |
| SHA pins | 64-hex `sha256` entries in `ModelRegistry.kt` | 6 |
| ModelDownloader unit tests | `./gradlew --no-daemon :core:testDebugUnitTest --tests 'com.gallopkeyboard.core.models.ModelDownloaderTest'` | 6 tests, 0 failures |
| Plan 008 inventory | `grep -q "Plan 008 additions" docs/dictus-inventory.md` | OK |
| Models doc hashes | `docs/models.md` SHA-256 entries | 6 (64 hex each) |
| Plan status | `plans/README.md` row 008 | DONE |
| Onboarding UI | `OnboardingActivity.kt` present | OK |
| Settings UI | `ModelsSettingsScreen.kt` present | OK |
| IME banner | `VoicePanelPromptBanner.kt` present | OK |

### verify.sh breakdown

- `assembleDebug` — BUILD SUCCESSFUL
- `testAll` — BUILD SUCCESSFUL
- `lint` — BUILD SUCCESSFUL
- Dictus package grep guard — no hits outside `third_party/`
- Model binary grep guard — no hits outside `third_party/`

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
| `bash scripts/verify.sh` exits 0 | PASS |
| `ModelDownloaderTest` (6 cases) passes | PASS |
| Fresh install → onboarding flow completes on device | **NOT RUN** — no device |
| IME banner deep-links to onboarding when models missing | **NOT RUN** — no device |
| Settings screen lists all specs with statuses | **NOT RUN** — no device |
| Resume-download manual test | **NOT RUN** — no device |
| `docs/models.md` contains real SHA-256 values (64 hex) | PASS |
| `docs/dictus-inventory.md` "Plan 008 additions" | PASS |
| `plans/README.md` row 008 `DONE` | PASS |

## Manual on-device (deferred)

Plan acceptance tests require a physical device or configured AVD with network access for ~220 MB model download. Not executed in this session.

| # | Action | Result |
|---|--------|--------|
| 1 | Fresh install → launcher icon → onboarding → Download on Wi-Fi → "Voice ready" | not run |
| 2 | Fresh install on mobile data → metered warning shown | not run |
| 3 | Kill app mid-download → reopen → resume works | not run |
| 4 | Corrupt model file → Settings shows Corrupt → Fix redownloads | not run |
| 5 | Delete all models → IME banner → tap opens onboarding | not run |

## Blockers

- **Manual download/onboarding acceptance (non-blocking for automated gate):** First-launch download UX, resume, corrupt-file fix, and IME banner deep-link not run — no `adb` device attached. Reviewer or human should validate per Plan 008 manual test plan before merge.

## Advance

Automated verification passed → `npm run devteam:advance -- job-008 --to reviewing`
