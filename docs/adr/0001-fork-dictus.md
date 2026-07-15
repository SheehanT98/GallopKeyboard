# ADR-0001: Fork Dictus as Android foundation

## Status

Accepted

## Context

Owner wants a personal Android keyboard with DeepSeek-style voice panel, offline STT, and Gboard-like typing basics. Building STT + IME from scratch is high risk for agent execution.

## Decision

Fork [Dictus Android](https://github.com/getdictus/dictus-android) (MIT). Dictus already provides system IME, on-device Whisper + Parakeet via whisper.cpp and sherpa-onnx.

## Consequences

- Faster path to working voice dictation
- Add: panel toggle UX, hybrid pipeline, emoji, short clipboard
- iOS remains a separate future project (Dictus is Android-only)
