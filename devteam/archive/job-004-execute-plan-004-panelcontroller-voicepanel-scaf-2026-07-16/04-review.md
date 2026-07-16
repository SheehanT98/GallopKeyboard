# Job 004 — Review

| Field | Value |
|-------|-------|
| **Job** | job-004 |
| **Branch** | `cursor/devteam-job-004-execute-plan-004-panelcontroller-voicepanel-scaf-c1fc` |
| **PR** | https://github.com/SheehanT98/GallopKeyboard/pull/14 (OPEN) |
| **Base** | `origin/main` |
| **Reviewed at tip** | `5c74921` |
| **Verdict** | **approve** |

## Summary

Plan 004 panel scaffold matches the plan: `PanelState` / `PanelController` / `VoicePanel` / `PanelHost`, IME lifecycle `reset()`, typing-panel mic overlay, 7 unit tests, inventory + plans index. Automated verification **PASS** (`verify.sh`, `:ime:testDebugUnitTest`). Manual on-device (Notes / WhatsApp / Gmail) was deferred — **risk for human, not an auto-block** (code matches plan; no device/AVD in CI agent). **Approve** for double-check.

## Scope compliance

| Area | Status |
|------|--------|
| `PanelState.kt` — `TYPING` / `VOICE` | Present |
| `PanelController.kt` — `StateFlow`, `toggle` / `showTyping` / `showVoice` / `reset`, no Android deps | Present |
| `VoicePanel.kt` — 64.dp / 80% placeholder button, Think?/Search? disabled, ⌨️ `Icons.Filled.Keyboard`, `@Preview`, height 346.dp | Present |
| `PanelHost.kt` — switches typing ↔ voice via `collectAsState` | Present |
| `DictusImeService` — `PanelHost` wraps content; `reset()` on `onStartInputView(!restarting)` + `onFinishInputView` | Present (Case A Compose typing panel) |
| `KeyboardScreen` — bottom-right mic overlay 40×40, 8.dp inset, `panel_toggle_voice` | Present |
| `strings.xml` — `panel_toggle_voice`, `panel_toggle_typing`, `voice_panel_placeholder_button` | Present |
| `PanelControllerTest.kt` — 7 cases | Present |
| `docs/dictus-inventory.md` — Plan 004 additions | Present |
| `plans/README.md` row 004 → DONE | Present |
| Audio / `RECORD_AUDIO` in `ime/.../panel/` | None (out of scope) |
| Key layouts / landscape / haptics | Untouched |

Minor deviations (acceptable):

- Single squashed `feat(ime)` commit vs plan’s six granular commits — fine for quick mode.
- Extra `themeMode` / `keyboardHeight` params on `PanelHost` / `VoicePanel` — needed to match Dictus theming and fixed height.
- Legacy `RecordingScreen` (top mic pill) coexists with new `VoicePanel` — documented in inventory for Plan 005; plan STOP was “do not layer blindly,” which was handled by keeping them separate.

## Verification evidence

Relies on tester artifact (`03-test-report.md`, SHA `934be4d`) plus spot-check of sources at tip `5c74921`:

| Criterion | Result |
|-----------|--------|
| `bash scripts/verify.sh` | PASS (tester) |
| `./gradlew :ime:testDebugUnitTest` — 7 `PanelControllerTest` | PASS (tester) |
| `Icons.Filled.Keyboard` in `VoicePanel.kt` | 1 match (line 95) |
| No `RECORD_AUDIO` under `ime/.../panel/` | Confirmed |
| Inventory Plan 004 section + plans `DONE` | Confirmed |
| Height parity typing ↔ voice | `KEYBOARD_PANEL_HEIGHT_DP = 346.dp` matches KeyboardScreen comment (36+46+264) |
| PR #14 | OPEN (do not recreate) |
| Manual tests 1–6 | **NOT RUN** — no adb device / AVD |

## Risks for the human reviewer

1. **Manual IME acceptance deferred**: Toggle / height / focus-reset in Notes, WhatsApp, Gmail not exercised on device. Sideload `app-debug.apk` per `docs/sideload-galaxy-s22.md` before merge if possible.
2. **Dual mic affordances**: Top `MicButtonRow` still starts legacy `RecordingScreen`; bottom-right mic opens HANDOFF `VoicePanel`. Intentional until Plan 005; may confuse testers.
3. **Mic overlay vs keys**: Bottom-right 40.dp mic overlays the typing panel corner — confirm it does not steal taps from space/IME keys on S22 portrait.
4. **Height jump**: Code pins voice panel to 346.dp; only on-device can confirm no IME resize when toggling.
5. **Untracked `package-lock.json`**: Local npm artifact; not part of this job.

## Blockers

None.

## Advance

`npm run devteam:advance -- job-004 --to double_checking`
