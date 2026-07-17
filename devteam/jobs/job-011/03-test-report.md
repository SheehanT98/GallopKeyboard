# Job 011 — Test report

| Field | Value |
|-------|-------|
| **Job** | job-011 |
| **Branch** | `cursor/devteam-job-011-execute-plan-011-toolbar-voice-and-thin-voice-pa-c1fc` |
| **PR** | [#32](https://github.com/SheehanT98/GallopKeyboard/pull/32) |
| **Plan** | `plans/011-toolbar-voice-and-thin-voice-panel.md` |
| **Tester** | devteam-tester (composer-2.5) |
| **Tested at** | 2026-07-17T13:10:00Z |
| **SHA tested** | `5f31d991f6839f9794ace553c5eca6f1b8ddec8b` |
| **Verdict** | **PASS** (automated); manual on-device deferred |

## Environment

```bash
source scripts/android-env.sh
# ANDROID_HOME=/opt/android-sdk
# JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

| Check | Command | Result |
|-------|---------|--------|
| Branch | `git branch --show-current` | on job-011 branch |
| Sync | `git pull origin cursor/devteam-job-011-execute-plan-011-toolbar-voice-and-thin-voice-pa-c1fc` | up to date |

## Commands run

| Check | Command | Result |
|-------|---------|--------|
| Plan verify | `source scripts/android-env.sh && ./gradlew :ime:testDebugUnitTest :app:assembleDebug` | exit 0, BUILD SUCCESSFUL |
| Drift guard | `bash scripts/verify.sh` | exit 0, printed `OK` |
| Plan status | `plans/README.md` row 011 | DONE |

### verify.sh breakdown

- `assembleDebug` — BUILD SUCCESSFUL
- `testAll` — BUILD SUCCESSFUL
- `lint` — BUILD SUCCESSFUL
- Dictus package grep guard — no hits outside `third_party/`
- Model binary grep guard — no hits outside `third_party/`
- `System.out.println` guard — no hits
- Log.d hot-path advisory — no WARN emitted

## Spot-check (code vs plan)

| Requirement | Evidence | Result |
|-------------|----------|--------|
| Toolbar left: **Voice panel** | `strings.xml` `toolbar_voice_panel`; `MicButtonRow` left `Text` + `onVoicePanelToggle` | PASS |
| Toolbar right: **Voice** (inline, no panel switch) | `MicButtonRow` embeds `SmartVoiceButton` with `SmartVoiceButtonStyle.Toolbar`; label `toolbar_voice` | PASS |
| No **Keyboard** toolbar label | `MicButtonRow` has no settings/keyboard switcher row; no `toolbar_keyboard` string | PASS |
| Voice tap/hold on typing panel | `SmartVoiceButton` shared `GestureFsm` wired with `audioRecorderEngine` + `transcriber` from `DictusImeService` → `KeyboardScreen` → `MicButtonRow` | PASS (code) |
| Thin voice panel (~140 dp) | `VOICE_PANEL_HEIGHT_DP = 140.dp`; `PanelHost` uses voice height; `VoicePanelHeightTest` asserts 140 &lt; 346 | PASS |
| **Hold to speak** on voice panel | `voice_panel_placeholder_button` → "Hold to speak"; `SmartVoiceButtonStyle.PanelCompact` in `VoicePanel` | PASS |
| Keyboard return bottom-right | `VoicePanel` `IconButton` with `Icons.Filled.Keyboard` aligned `BottomEnd` | PASS |
| Light voice panel theme | `GallopVoiceTheme` + `GallopColors.Surface` in `VoicePanel` | PASS |
| No Think/Search/Plus on voice panel | `VoicePanel` only hold button + keyboard return (+ optional setup banner) | PASS |
| No `SuggestionBar` on typing keyboard | `KeyboardScreen` does not import or compose `SuggestionBar`; suggestions default `false` in `DictusImeService` | PASS |
| Unit tests for panel height | `VoicePanelHeightTest` (3 tests) included in `:ime:testDebugUnitTest` | PASS |

## Done criteria (Plan 011)

| Criterion | Result |
|-----------|--------|
| Typing toolbar: **Voice panel** \| **Voice** | PASS |
| Voice tap/hold works on typing panel and commits text | PASS (code); **NOT RUN** on device |
| Voice panel thin bar with Hold to speak + keyboard return | PASS (code) |
| No suggestion bar on typing keyboard | PASS |
| Unit tests + `assembleDebug` pass | PASS |
| `plans/README.md` updated to DONE for 011 | PASS |

## STOP conditions

| Condition | Outcome |
|-----------|---------|
| Changing package name / applicationId | **Not hit** |
| Adding network STT or cloud APIs | **Not hit** |
| Reintroducing Think/Search/Plus UI | **Not hit** |
| Breaking tap-toggle or hold-release semantics (ADR-0003) | **Not hit** in automated tests; gesture FSM reused unchanged |

## Manual on-device (deferred)

Tap/hold dictation UX and panel height on a physical device were not exercised — no `adb` device attached.

| # | Action | Result |
|---|--------|--------|
| 1 | Tap **Voice** on typing panel → record → tap stop → text committed | not run |
| 2 | Hold **Voice** on typing panel → release → text committed | not run |
| 3 | Tap **Voice panel** → thin bar visible (~140 dp) with Hold to speak | not run |
| 4 | Keyboard icon returns to typing panel | not run |
| 5 | Typing keyboard shows no suggestion strip | not run |

## Blockers

None for automated gate. Manual voice-gesture and panel UX validation deferred to reviewer or owner with a sideloaded build.

## Advance

Left to orchestrator: `npm run devteam:advance -- job-011 --to reviewing`
