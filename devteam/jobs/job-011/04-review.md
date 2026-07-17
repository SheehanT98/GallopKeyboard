# Job 011 — Review

| Field | Value |
|-------|-------|
| **Job** | job-011 |
| **Branch** | `cursor/devteam-job-011-execute-plan-011-toolbar-voice-and-thin-voice-pa-c1fc` |
| **PR** | [#32](https://github.com/SheehanT98/GallopKeyboard/pull/32) |
| **Plan** | `plans/011-toolbar-voice-and-thin-voice-panel.md` |
| **Reviewed SHA** | `926e524` (code at `5f31d99` + test report) |
| **Base** | `origin/main` (`ce5c954`) |
| **Verdict** | **APPROVE** |

## Summary

Plan 011 product intent is met in code: typing toolbar is **Voice panel** | **Voice**, voice panel is a thin light bar (~140 dp) with **Hold to speak** + keyboard return, suggestions are hidden, and ADR-0003 tap/hold semantics are preserved via shared `SmartVoiceButton` / `GestureFsm`. Automated verification passed; no STOP conditions hit. Manual on-device gesture/UX remains deferred — acceptable residual risk for human sideload.

## Scope compliance

| Done criterion | Status | Evidence |
|----------------|--------|----------|
| Toolbar: **Voice panel** \| **Voice** | Met | `MicButtonRow` + `toolbar_voice_panel` / `toolbar_voice`; left → `onVoicePanelToggle`, right → `SmartVoiceButtonStyle.Toolbar` (no panel switch) |
| Voice tap/hold on typing panel commits text | Met (code) | Same `GestureFsm` + `AudioRecorderEngine` / `Transcriber` path as voice panel; wired from `DictusImeService` → `KeyboardScreen` → `MicButtonRow` |
| Thin voice panel + Hold to speak + keyboard return | Met | `VOICE_PANEL_HEIGHT_DP = 140.dp`; `GallopVoiceTheme` light surface; `PanelCompact` + "Hold to speak"; `Icons.Filled.Keyboard` `BottomEnd` |
| No suggestion bar on typing keyboard | Met | `SuggestionBar` removed from `KeyboardScreen`; suggestions default `false` |
| Unit tests + assembleDebug | Met | Tester: `:ime:testDebugUnitTest`, `assembleDebug`, `verify.sh` PASS; `VoicePanelHeightTest` added |
| `plans/README.md` → DONE for 011 | Met | Index row updated |
| No Think / Search / Plus / file-attach | Met | `VoicePanel` only hold button + return (+ optional setup banner) |

### Diff scope

Touched files align with the plan (MicButtonRow, KeyboardScreen, DictusImeService, strings, VoicePanel, SmartVoiceButton, PanelHost, theme, height test, plans README, job artifacts). No package/`applicationId` changes; no cloud STT.

## STOP conditions

| Condition | Outcome |
|-----------|---------|
| Changing package name / applicationId | Not hit |
| Adding network STT or cloud APIs | Not hit |
| Reintroducing Think/Search/Plus UI | Not hit |
| Breaking tap-toggle or hold-release (ADR-0003) | Not hit — `GestureFsm` / 400 ms hold / 48 dp cancel slop unchanged; styles only |

## Verification evidence

From `03-test-report.md` (SHA `5f31d99`):

- `./gradlew :ime:testDebugUnitTest :app:assembleDebug` — BUILD SUCCESSFUL
- `bash scripts/verify.sh` — OK (assemble + testAll + lint + grep guards)
- Spot-check of strings, heights, wiring — PASS

PR #32 open at review time; CI `build` was still in progress — human should confirm green before merge.

## Risks for the human reviewer

1. **Dual voice entry points on typing UI** — Toolbar **Voice** uses SmartVoiceButton (Plan 005 path). Bottom-row **MIC** key still calls `handleMicTap()` → `DictationService` / `RecordingScreen`. Plan did not require removing the bottom mic; both can confuse users or contend for mic if used in sequence. Sideload: try both paths once.
2. **Eager ASR deps on typing panel** — `KeyboardContent` now `remember`s recorder/transcriber/permission for the toolbar. PanelHost still lazy-loads for the dedicated voice panel. Slightly warmer typing open; watch IME jank on S22.
3. **Settings / IME switcher affordance removed** from typing toolbar (old "Keyboard" + gear). Plan allows relying on system IME picker; confirm that is acceptable.
4. **No on-device run** — tap/hold commit, thin-bar height, and panel switch not exercised on hardware. Highest residual UX risk.
5. **Suggestion preference dead for UI** — engine/DataStore still exist; bar never composed. Fine for v1; settings toggle would no-op visually.

## Findings

None that require REQUEST CHANGES. Residual items above are human-sideload / follow-up polish, not plan blockers.

## Advance

`npm run devteam:advance -- job-011 --to double_checking`
