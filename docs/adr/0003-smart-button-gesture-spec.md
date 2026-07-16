# ADR-0003: Smart voice button gesture spec

## Status

Accepted

## Context

The owner wants a single voice button that serves both quick bursts and long dictation without a separate mode switch or settings toggle. `HANDOFF.md` fixes the gesture semantics: tap-tap equals long dictation; hold past a threshold then release equals short/medium utterances. This ADR encodes the exact pointer-event behavior so Plan 005 (gesture implementation) and Plan 007 (polish timeout) can reference named constants rather than re-deriving UX from prose.

## Decision

Smart button behavior mirrors `HANDOFF.md` "Smart button logic":

1. On `ACTION_DOWN`: start hold timer; begin recording; start streaming pass.
2. If `ACTION_UP` before **400 ms**: enter tap-toggle mode — first tap started recording, second tap stops recording and triggers polish.
3. If pointer is still down at **400 ms**: enter hold mode — show "recording" visual state; `ACTION_UP` stops recording and triggers polish.
4. While recording (either mode): partial transcript from Parakeet is committed as composing text (see ADR-0002).
5. On stop: Whisper polish replaces the composing text with the final transcript, or falls back to the last streaming partial if polish exceeds a **2000 ms** timeout (see ADR-0002 acceptance criterion).
6. Cancel gestures (`ACTION_CANCEL`, pointer leaves button bounds by more than **48 dp** slop): discard the recording session — no commit, no polish. This prevents accidental long dictations when the user drags off the button.

Named constants for downstream plans:

- `HOLD_THRESHOLD_MS = 400`
- `POLISH_TIMEOUT_MS = 2000`
- `CANCEL_SLOP_DP = 48`

## Consequences

- Threshold values are named constants so Plan 005 can reference them directly in tests and implementation.
- The streaming/polish contract is fixed by ADR-0002; this ADR only governs when recording starts, stops, or is cancelled.
- Any future change to gesture behavior must update this ADR first, then Plan 005's tests and implementation — never the other way around.
- Cancel slop prevents accidental commits when the user's finger drifts off the button during a long hold.
