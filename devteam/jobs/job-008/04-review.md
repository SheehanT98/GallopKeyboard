# Review — job-008 (Model download UX)

| Field | Value |
|-------|-------|
| **Job** | job-008 |
| **Branch** | `cursor/devteam-job-008-execute-plan-008-model-download-ux-c1fc` |
| **PR** | [#22](https://github.com/SheehanT98/GallopKeyboard/pull/22) |
| **Reviewer** | devteam-reviewer (cursor-grok-4.5-high) |
| **Reviewed at** | 2026-07-16T19:10:00Z |
| **Feature SHA** | `3ce9a08` |
| **HEAD at review** | `749b99d` (includes test report) |
| **Verdict** | **approve** |

## Summary

**Approve.** Plan 008 scope is implemented: `ModelSpec`/`ModelRegistry` with pinned URLs + SHA-256, `ModelDownloader` (Range resume, hash verify, atomic `.part` rename), `ModelInstaller`, `OnboardingActivity` first-launch UX, `ModelsSettingsScreen` (status/Fix/tier/delete), IME `VoicePanelPromptBanner` deep-link, six MockWebServer unit tests, and docs/inventory/README updates. Automated verification reconfirmed green. Manual on-device download acceptance remains deferred (no adb device) — treat as human risk, not a merge blocker for the automated gate.

**Blockers:** none.

## Scope compliance

| Plan item | Status |
|-----------|--------|
| `ModelSpec` + `ModelRegistry` (Parakeet ×4, Whisper base/small) | Done — 6 specs, 64-hex SHA pins; default bundle ~219 MB |
| `ModelDownloader` (OkHttp, `.part`, Range, SHA-256, progress Flow, metered helper) | Done |
| `ModelInstaller` (sequential install, status, delete, disk usage, daily verify) | Done |
| `OnboardingActivity` (metered warning, progress, retry, skip, IME settings CTA) | Done — launcher `singleTop` |
| `ModelsSettingsScreen` (status/Fix, tier radio, storage, delete-all confirm) | Done via `ModelsSettingsActivity` |
| IME deep-link when models missing | Done — `VoicePanelPromptBanner` + `VoiceSetupIntents` (no auto-launch) |
| `ModelDownloaderTest` (6 MockWebServer cases) | Done |
| `docs/models.md` download section with real hashes | Done |
| `docs/dictus-inventory.md` Plan 008 additions | Done — new `:core` stack; Dictus downloader not reused (justified) |
| `plans/README.md` → DONE | Done |
| Out of scope (delta downloads, P2P, background auto-update, SAF, analytics) | Respected |

### Acceptable deviations

- Extra helpers `VoiceSetupPrefs` / `VoiceSetupIntents` — support launcher routing and IME deep-link without circular app↔ime deps.
- Skip button enabled on Ready (plan said “initially disabled”) — still gated off during download; better UX.
- Whisper polish still picks first existing candidate file (`WhisperConfig.fromModelDir`); settings deletes the other tier before download, so preference works for the intended path.

## Verification evidence

Re-run during review:

| Check | Result |
|-------|--------|
| `bash scripts/verify.sh` | exit 0, `OK` (assemble + testAll + lint + package/model guards) |
| `./gradlew :core:testDebugUnitTest --tests '*ModelDownloaderTest'` | BUILD SUCCESSFUL |
| Tester report (`03-test-report.md`) | automated PASS; 6/6 named cases |
| PR #22 | open, mergeable, base `main` |

### Done criteria

| Criterion | Result |
|-----------|--------|
| `verify.sh` exits 0 | PASS |
| `ModelDownloaderTest` 6 cases | PASS |
| Fresh install → onboarding completes on device | **DEFERRED** (no adb device) |
| IME banner deep-links to onboarding | **DEFERRED** (no adb device) |
| Settings lists specs with statuses | **DEFERRED** (no adb device) |
| Resume-download manual test | **DEFERRED** (no adb device) |
| `docs/models.md` real SHA-256 (64 hex) | PASS |
| Plan 008 inventory section | PASS |
| `plans/README.md` DONE | PASS |

## Risks for the human reviewer

1. **Manual download/onboarding not run** — Wi-Fi download to “Voice ready”, metered warning, mid-download resume, corrupt→Fix, delete-all→IME banner→onboarding need a device/AVD with network (~220 MB). Highest pre-merge confidence gap.
2. **Tier switch deletes old Whisper before new download finishes** — failure mid-switch can leave polish with no model until Retry/Fix; UX should recover via Fix/Missing status.
3. **`WhisperConfig.fromModelDir` ignores `VoiceSetupPrefs`** — prefers `base.en.gguf` over `small.en.gguf` if both exist; settings path deletes the inactive tier, so OK unless both linger from sideload.
4. **HTTP 416 on resume** — downloader emits Failed and returns without auto-restart from byte 0; user must tap Retry (`.part` cleared on 416).
5. **`isInstalled` / `fileStatus` hash full files on main paths** — onboarding `onResume` and settings refresh may briefly hitch on large installs; acceptable for v1.

## PR

Already open: https://github.com/SheehanT98/GallopKeyboard/pull/22 — no `devteam:open-pr` needed.
