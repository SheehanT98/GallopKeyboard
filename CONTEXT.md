# GallopKeyboard — domain context

Personal Android keyboard IME. Fork of [Dictus](https://github.com/getdictus/dictus-android). English only. 100% on-device STT. Separate repo: `gallopkeyboard`.

## Glossary

| Term | Meaning |
|------|---------|
| **IME** | Android Input Method Editor — the system keyboard component |
| **Voice panel** | Second view of the keyboard: large voice button + minimal toolbar (DeepSeek-style), replaces QWERTY until toggled back |
| **Typing panel** | Standard QWERTY + numbers/symbols view |
| **Panel toggle** | Bottom-right control that switches typing panel ↔ voice panel within the same IME |
| **Smart voice button** | Single control: tap-tap for long dictation; hold-past-threshold → release for short/medium |
| **Streaming pass** | Live partial transcript while recording (Sherpa-ONNX / Parakeet) |
| **Polish pass** | Final accurate transcript on stop (Whisper `base.en` or `small.en`) |
| **Hybrid STT** | Streaming pass during recording + polish pass on stop; fallback if cut: on-release only |

## Destination (v1)

Installable sideload APK on owner's Galaxy S22. Default keyboard with Gboard-like basics + voice panel toggle + hybrid offline dictation. Good enough to replace Gboard for daily use.

## Out of scope (v1)

- iOS (future phase if Android succeeds)
- Play Store release (structure for later)
- Swipe/gesture typing
- Long-term clipboard archive
- Cloud STT or telemetry
- Patching Gboard itself

## Acceptance criteria (ranked)

1. No noticeable battery drain during normal use (STT only while recording)
2. Accuracy ≥ Gboard voice for everyday English (hybrid polish required)
3. No crashes / ANRs in IME or host apps
4. Latency &lt;2 s polish on S22 after stop (streaming masks wait while recording)

## Target device

Samsung Galaxy S22 — 8 GB RAM. Default models: Parakeet (streaming) + Whisper `base.en` or `small.en` (polish). Storage budget flexible (~200–500 MB model download).
