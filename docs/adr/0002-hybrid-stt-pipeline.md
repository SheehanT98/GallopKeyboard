# ADR-0002: Hybrid STT pipeline

## Status

Accepted

## Context

Owner needs both quick bursts and long dictation. Streaming alone is less accurate; on-release alone feels slow for shorts.

## Decision

Two-stage pipeline while voice panel is recording:

1. **Streaming pass** — Sherpa-ONNX Parakeet inserts partial text into the host field via `InputConnection`
2. **Polish pass** — on stop, Whisper `base.en` or `small.en` re-transcribes full audio buffer and replaces partial with final text

If hybrid cannot ship in v1, **on-release only** is acceptable fallback (not streaming-only).

## Consequences

- Two models to bundle/download (~200–350 MB)
- STT work must run off IME main thread
- Battery: inference only during active recording
