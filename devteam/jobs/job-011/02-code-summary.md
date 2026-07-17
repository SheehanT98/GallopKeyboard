# Job 011 — Code stage

## Summary

Implemented Plan 011: typing toolbar **Voice panel** | **Voice**, thin light voice panel (~140dp), and hidden suggestion bar on the typing keyboard.

## Files changed

| File | Change |
|------|--------|
| `ime/.../ui/MicButtonRow.kt` | Left **Voice panel** opens voice panel; right **Voice** embeds `SmartVoiceButton` (toolbar style) with ADR-0003 tap/hold gestures. Removed "Keyboard" label and settings row. |
| `ime/.../ui/KeyboardScreen.kt` | Removed `SuggestionBar`; pass voice deps to `MicButtonRow`; keys area 310dp (36 toolbar + 310 = 346dp total). |
| `ime/.../DictusImeService.kt` | Wire `audioRecorderEngine`, `transcriber`, `permissionRequester` into `KeyboardScreen`; suggestions default `false`. |
| `ime/.../panel/SmartVoiceButton.kt` | Added `SmartVoiceButtonStyle` (`Panel`, `PanelCompact`, `Toolbar`); shared gesture FSM for all variants. |
| `ime/.../panel/VoicePanel.kt` | Thin bar via `VOICE_PANEL_HEIGHT_DP` (140dp); compact hold button; keyboard return bottom-right; no Think/Search/Plus. |
| `ime/.../panel/PanelHost.kt` | Voice panel uses `VOICE_PANEL_HEIGHT_DP` instead of full keyboard height. |
| `ime/.../theme/GallopTheme.kt` | Light color scheme for voice panel (`GallopVoiceTheme`). |
| `ime/src/main/res/values/strings.xml` | `toolbar_voice_panel`, `toolbar_voice`; placeholder → "Hold to speak". |
| `ime/src/test/.../VoicePanelHeightTest.kt` | Unit tests for panel height constants. |
| `plans/README.md` | Plan 011 status → DONE. |

## Verification

```bash
source scripts/android-env.sh
./gradlew :ime:testDebugUnitTest :app:assembleDebug
bash scripts/verify.sh
```

All passed (BUILD SUCCESSFUL).

## Product checklist

- [x] Typing toolbar: **Voice panel** | **Voice**
- [x] Voice tap/hold on typing panel (same `GestureFsm` / `SmartVoiceButton` as voice panel)
- [x] Voice panel thin bar with Hold to speak + keyboard return
- [x] No suggestion bar on typing keyboard
- [x] Unit tests + assembleDebug pass
