# GallopKeyboard — grilling handoff (feed to `/improve`)

One-shot input for planning and agent execution. Owner grills complete 2026-07-15.

## Elevator pitch

**GallopKeyboard** is a personal Android keyboard (fork of Dictus) that adds a DeepSeek-style voice panel toggle: normal QWERTY → bottom-right button → full-width smart voice button. Offline English STT with hybrid streaming + polish. Sideload on Galaxy S22; separate repo `gallopkeyboard`.

## Locked decisions

| # | Decision |
|---|----------|
| 1 | Use cases: long dictation and quick bursts equally |
| 2 | STT UX: hybrid (stream while recording, polish on stop); fallback = on-release |
| 3 | Keyboard: OSS QWERTY base + voice panel in same IME; not a floating overlay |
| 4 | Features: basics required; short clipboard + emoji nice; no swipe typing |
| 5 | Language: English only |
| 6 | Foundation: fork Dictus |
| 7 | Distribution: sideload APK v1; Play/App Store later |
| 8 | Device: Galaxy S22 — Parakeet + Whisper base/small |
| 9 | Smart voice button: tap-tap (long) + hold-threshold-release (short) |
| 10 | Deal-breakers: battery, accuracy, crashes &gt; latency |
| 11 | Repo: new `gallopkeyboard`, separate from GallopCRM |

## UX spec (voice panel)

```
┌─────────────────────────────────────┐
│  [Typing panel — QWERTY + symbols]  │
│                          [🎤 toggle]│  ← bottom-right
└─────────────────────────────────────┘
              ↓ toggle
┌─────────────────────────────────────┐
│     ┌─────────────────────────┐   │
│     │   Hold / Tap to speak   │   │  ← full-width smart button
│     └─────────────────────────┘   │
│  [Think?] [Search?]  [+]  [⌨️ back]│  ← minimal row; ⌨️ returns to typing
└─────────────────────────────────────┘
```

### Smart button logic

- `ACTION_DOWN`: start timer; begin recording + streaming pass
- If `ACTION_UP` before ~400 ms: toggle mode — first tap starts, second tap stops + polish
- If hold ≥ ~400 ms: enter hold mode — visual feedback; `ACTION_UP` stops + polish
- While recording: show partial text in host field (streaming)
- On stop: run polish pass; replace partial with final transcript

## UX spec (typing panel)

- QWERTY + numbers/symbols panel (Gboard-like layer switch)
- Shift, backspace, space, return
- Short clipboard: last 2–3 copies (not long archive)
- Basic emoji picker (v1 nice-to-have)
- Bottom-right: voice panel toggle (mic or waveform icon)
- No swipe/gesture typing in v1

## Technical architecture

```
GallopKeyboard IME (fork Dictus)
├── TypingPanel (existing Dictus keyboard + additions)
├── VoicePanel (new Compose/XML view)
├── PanelController (toggle state, keyboard height)
├── AudioRecorder (16 kHz mono PCM while active)
├── StreamingEngine (Sherpa-ONNX Parakeet)
├── PolishEngine (whisper.cpp base.en or small.en)
└── TextCommit (InputConnection insert/replace)
```

### Threading

- UI: IME main thread only
- Audio + STT: dedicated background dispatcher
- Polish pass: coroutine with timeout; never ANR

### Battery

- No wake locks beyond recording session
- Unload or idle models when voice panel inactive (configurable)
- Reuse Dictus thermal/threading patterns where present

## Suggested `/improve` work breakdown

### Phase 0 — Repo bootstrap
- Create `gallopkeyboard` repo; fork Dictus; rename package/app; MIT attribution retained
- Document build + sideload in README
- Galaxy S22 debug target

### Phase 1 — Panel toggle (no STT changes yet)
- Add `VoicePanel` view with placeholder button
- Bottom-right toggle on typing panel
- `TYPE_INPUT_METHOD` layer correct; no keyboard dismiss bugs
- Acceptance: toggle works in Notes, WhatsApp, Gmail

### Phase 2 — Smart button + recording
- Implement tap-tap / hold-threshold logic
- Record PCM buffer for session duration
- No paragraph cutoff while recording (unlimited until user stops)
- Acceptance: 3-minute recording without auto-stop

### Phase 3 — Hybrid STT
- Wire Parakeet streaming partials to `InputConnection`
- Wire Whisper polish on stop
- Acceptance: hybrid works on S22; accuracy spot-check vs Gboard voice

### Phase 4 — Keyboard polish
- Short clipboard (2–3 items)
- Emoji picker (basic)
- Visual polish toward DeepSeek (large rounded voice button, clean toolbar)

### Phase 5 — Hardening
- Battery profiling (30 min mixed use)
- Crash pass across top 5 apps
- Model download UX on first launch

## Model defaults (S22)

| Role | Model | ~Size |
|------|-------|-------|
| Streaming | Sherpa-ONNX Parakeet (English) | ~80 MB |
| Polish | Whisper `small.en` Q5 or `base.en` | ~140–470 MB |

Download on first launch; user picks quality tier in settings if needed.

## Reference projects

- [Dictus](https://github.com/getdictus/dictus-android) — fork base
- [Peyokeys](https://github.com/devabhixda/peyokeys) — hold-to-transcribe IME patterns
- [FlorisBoard](https://github.com/florisboard/florisboard) — clipboard/emoji reference only
- [whisper.cpp Android example](https://github.com/ggerganov/whisper.cpp/tree/master/examples/whisper.android)

## Prompt for `/improve`

```
/improve deep

Project: GallopKeyboard — personal Android keyboard, fork of Dictus.
Read: .scratch/gallop-keyboard/HANDOFF.md and CONTEXT.md (or copy to new repo).
Produce an implementation plan with phased tickets for cheap agents.
Constraints: English only, Galaxy S22, hybrid STT, smart voice button, sideload v1.
Do not scope iOS or Play Store for v1.
```
