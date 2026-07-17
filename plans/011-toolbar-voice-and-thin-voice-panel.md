# Plan 011: Toolbar Voice + thin voice panel + hide suggestions

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving on.
> If anything in "STOP conditions" occurs, stop and report — do not
> improvise. When done, update `plans/README.md` status for this plan
> unless a reviewer told you they maintain the index.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED (gesture + ASR wiring on typing panel)
- **Depends on**: Plans 001–010 (merged) + PR #30 light QWERTY / crash fixes on `main`
- **Category**: feature / UX
- **Planned at**: 2026-07-17

## Why this matters

Users need voice without leaving the typing keyboard, and a thin
dedicated voice panel for voice-only use. The current toolbar labels
("Keyboard" / "Voice panel") are confusing, the voice panel is too tall,
and the suggestion bar wastes space (user does not want suggestions).

## Product requirements (authoritative)

1. **Toolbar (typing panel)**
   - Left control label: **"Voice panel"** — opens the dedicated voice panel.
   - Right control label: **"Voice"** — inline dictation on the typing panel
     (does **not** switch panels).
   - Remove any leftover "Keyboard" toolbar label for settings/switcher;
     system IME switcher remains via Android nav if needed, or keep a
     discreet settings affordance that does not say "Keyboard".

2. **"Voice" control gestures** (reuse Plan 005 `GestureFsm` / `SmartVoiceButton` logic)
   - **Tap**: start recording; **tap again**: stop → transcribe → commit text.
   - **Hold**: record while held; **release**: stop → transcribe → commit.
   - Same behavior as the voice-panel smart button (ADR-0003).

3. **Voice panel UI**
   - Thin bar at bottom (~120–160 dp), light theme, white/light surface.
   - Center: **"Hold to speak"** / recording state (tap-toggle + hold).
   - Bottom-right: **keyboard icon** → return to typing panel.
   - **No** Think / Search / Plus / file-attach controls.
   - Much shorter than current ~346 dp panel.

4. **Suggestions**
   - Hide / remove `SuggestionBar` from the typing keyboard UI.
   - Keep suggestion engine code if cheap, but default UI off and not shown.

## Current state (on `main` after PR #30)

- `MicButtonRow` shows "Keyboard" + "Voice panel".
- Bottom row has mic key; `SmartVoiceButton` lives only inside `VoicePanel`.
- `VoicePanel` height = `KEYBOARD_PANEL_HEIGHT_DP` (346.dp).
- `SuggestionBar` always shown in `KeyboardScreen`.

## Implementation steps

### Step 1 — Toolbar rename + wire Voice + Voice panel

Files:
- `ime/.../ui/MicButtonRow.kt`
- `ime/.../ui/KeyboardScreen.kt`
- `ime/.../DictusImeService.kt` (pass recorder/transcriber/permission into typing UI)
- `ime/src/main/res/values/strings.xml`

Changes:
- Left: "Voice panel" → `onVoicePanelToggle`.
- Right: "Voice" → inline `SmartVoiceButton` (or extract shared gesture surface)
  wired to the same `AudioRecorderEngine` + `Transcriber` as voice panel.
- Ensure voice deps can resolve on typing panel when Voice is used (lazy OK).

### Step 2 — Thin light voice panel

Files:
- `ime/.../panel/VoicePanel.kt`
- `ime/.../panel/SmartVoiceButton.kt` (label "Hold to speak")
- Theme: use light colors for voice panel (or `DictusTheme(LIGHT)`), not dark Gallop-only.

Height target: ~140.dp content (Hold to speak + keyboard return icon).
Update `KEYBOARD_PANEL_HEIGHT_DP` or split constants: typing height vs voice height.

### Step 3 — Hide suggestions

Files:
- `ime/.../ui/KeyboardScreen.kt`
- Optionally gate with `PreferenceKeys.SUGGESTIONS_ENABLED` default `false`
  and never compose `SuggestionBar` when false / always remove for v1 UX.

### Step 4 — Tests + verify

```bash
source scripts/android-env.sh
./gradlew :ime:testDebugUnitTest :app:assembleDebug
bash scripts/verify.sh
```

Add/adjust unit tests for string resources / panel height constant if present.
Gesture FSM already covered — add a small test only if you extract new helpers.

## STOP conditions

- Changing package name / applicationId.
- Adding network STT or cloud APIs.
- Reintroducing Think/Search/Plus UI.
- Breaking tap-toggle or hold-release semantics from ADR-0003.

## Done when

- [ ] Typing toolbar: **Voice panel** | **Voice**
- [ ] Voice tap/hold works on typing panel and commits text
- [ ] Voice panel is a thin bar with Hold to speak + keyboard return
- [ ] No suggestion bar on typing keyboard
- [ ] Unit tests + `assembleDebug` pass
- [ ] `plans/README.md` updated to DONE for 011
