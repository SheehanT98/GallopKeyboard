# Code summary — job-004

## What landed

Plan 004 panel scaffold: typing ↔ voice toggle frame for the IME without audio/STT wiring.

### New files

| File | Purpose |
|------|---------|
| `ime/.../panel/PanelState.kt` | `TYPING` / `VOICE` enum |
| `ime/.../panel/PanelController.kt` | Hot `StateFlow` panel state; `toggle()`, `showTyping()`, `showVoice()`, `reset()` |
| `ime/.../panel/VoicePanel.kt` | Compose placeholder (smart button + Think?/Search? + ⌨️ back); 346 dp height |
| `ime/.../panel/PanelHost.kt` | Switches between typing content and `VoicePanel` |
| `ime/src/test/.../PanelControllerTest.kt` | 7 unit tests for controller transitions |

### Modified files

| File | Change |
|------|--------|
| `DictusImeService.kt` | Owns `PanelController`; `PanelHost` wraps `KeyboardContent`; lifecycle `reset()` on focus |
| `KeyboardScreen.kt` | Bottom-right mic overlay (`onVoicePanelToggle`) — 40 dp, 8 dp inset |
| `ime/build.gradle.kts` | `compose-material-icons-extended` for `Icons.Filled.Mic` / `Keyboard` |
| `ime/.../strings.xml` | `panel_toggle_voice`, `panel_toggle_typing`, `voice_panel_placeholder_button` |
| `docs/dictus-inventory.md` | Plan 004 additions section |
| `plans/README.md` | Plan 004 status → DONE |

### STOP conditions

None hit. Dictus legacy `RecordingScreen` (top mic pill) coexists with the new HANDOFF voice panel scaffold; documented in inventory for Plan 005 reconciliation.

### Verification

- `bash scripts/verify.sh` — OK
- `./gradlew :ime:testDebugUnitTest` — pass (7 new `PanelControllerTest` cases)
- `grep Icons.Filled.Keyboard VoicePanel.kt` — 1 match

### Manual on-device

Not run in this environment (no Galaxy S22 / emulator attached). Orchestrator should run manual tests 1–6 from the plan on target device.
