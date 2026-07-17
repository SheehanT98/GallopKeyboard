# Job 018 — Review

| Field | Value |
|-------|-------|
| **Job** | job-018 |
| **Branch** | `cursor/devteam-job-018-execute-plan-016-voice-dispose-cancel-and-unify--c1fc` |
| **PR** | [#41](https://github.com/SheehanT98/GallopKeyboard/pull/41) |
| **Plan** | `plans/016-voice-dispose-cancel-and-unify-mic.md` |
| **Reviewer** | devteam-reviewer (cursor-grok-4.5-high) |
| **Reviewed at** | 2026-07-17T16:28:00Z |
| **SHA reviewed** | `749301e` (feat) |
| **Base** | `origin/main...HEAD` |
| **Verdict** | **APPROVE** |

## Summary

Plan 016 fixes a dispose leak where `SmartVoiceButton` left Parakeet/hybrid ASR streams active when the composable left composition, and unifies IME mic entry so bottom-row `KeyType.MIC` opens the voice panel instead of starting legacy `DictationService` recording. Implementation is minimal, test-backed, and scope-tight. Tester evidence is green. **APPROVE**.

## Scope compliance

| In / out of scope | Result |
|-------------------|--------|
| `SmartVoiceButton.kt` — dispose calls transcriber cancel | **Met** |
| `VoiceSessionCleanup.kt` — shared `cancelActiveSession` helper | **Met** |
| `KeyboardScreen.kt` — MIC → `onVoicePanelToggle()` | **Met** |
| `DictusImeService.kt` — keyboard no longer passes `onMicTap` | **Met** — `handleMicTap` retained only for companion `RecordingScreen` |
| `GestureFsm.kt` unchanged (dispose fixed in composable) | **Met** |
| `KeyboardLayouts.kt` unchanged (mic key kept; routes to panel) | **Met** |
| `VoiceSessionCleanupTest.kt` + `GestureFsmTest` dispose note | **Met** |
| `docs/dictus-inventory.md` — Plan 016 | **Met** |
| `plans/README.md` — 016 → DONE | **Met** |
| `DictationService` / companion `RecordingScreen` | **Untouched** (correct) |

Product diff vs `origin/main` (excluding job artifacts): seven files — new helper + test, `SmartVoiceButton`, `KeyboardScreen`, `DictusImeService` (one-line wiring removal), inventory, plans index.

## Implementation review

### Dispose cancel (Step 1)

```144:152:ime/src/main/java/com/gallopkeyboard/ime/panel/SmartVoiceButton.kt
    DisposableEffect(Unit) {
        onDispose {
            recordingJob?.cancel()
            recordingJob = null
            holdTimerJob?.cancel()
            visualRecording = false
            activeSession = cancelActiveSession(transcriber, activeSession)
            fsm.reset()
        }
    }
```

- Matches plan: jobs cancelled, visual cleared, **transcriber cancel** before `fsm.reset()`.
- `onSessionCancel` gesture path refactored to the same helper — single cleanup path, no drift.

```15:22:ime/src/main/java/com/gallopkeyboard/ime/panel/VoiceSessionCleanup.kt
internal fun cancelActiveSession(
    transcriber: Transcriber,
    session: AudioSession?,
): AudioSession? {
    if (session != null) {
        transcriber.onSessionCancel(session)
    }
    return null
}
```

- Idempotent: null session no-ops; safe if dispose follows gesture cancel (session already nulled).
- Extracted helper is testable without Compose — matches plan Option A.

### Unify mic entry (Step 3)

```221:224:ime/src/main/java/com/gallopkeyboard/ime/ui/KeyboardScreen.kt
        KeyType.MIC -> {
            onVoicePanelToggle()
            Timber.d("Mic key tapped — opening voice panel")
        }
```

- Recommended product default: MIC opens voice panel (`PanelController.showVoice`).
- IME keyboard path no longer starts `DictationService` recording.
- `handleMicTap` / `controller.startRecording()` remain only on `RecordingScreen` (companion dictation UI) — in scope per plan.

### Tests (Step 2)

- `VoiceSessionCleanupTest` — transcriber invoked, null no-op, double-call idempotent.
- `GestureFsmTest` — comment documents dispose contract at FSM seam.
- `KeyboardLayoutTest` unchanged — mic key still present on bottom rows (expected).

### Plan 014 interaction

Plan 014 (async ASR dispatcher) is merged on `main`. Dispose cancel calls existing `transcriber.onSessionCancel`, which serializes via the ASR dispatcher from job-017. No new frame-path `runBlocking` introduced.

## Done criteria

| Criterion | Result |
|-----------|--------|
| Dispose cancels via `transcriber.onSessionCancel` | **PASS** |
| Single hybrid voice product for IME keyboard | **PASS** |
| `bash scripts/verify.sh` → `OK` | **PASS** (tester) |
| Inventory + README updated | **PASS** |
| No out-of-scope production files | **PASS** |

## Risks for human reviewer

| Risk | Severity | Notes |
|------|----------|-------|
| **PR #37 overlap** | Low–Med | Open PR #37 (voice panel-only UX) may conflict on panel/voice files at merge. This review is against `main` + job-018 branch only; rebase/merge conflict resolution may be needed. |
| Double cancel on dispose after gesture cancel | Low | Helper is idempotent; `onSessionCancel` on null stream should no-op in engine. |
| `handleMicTap` still present in service | None | Companion `RecordingScreen` only; not an IME dual-path regression. |
| Manual QA: mid-recording panel switch | Info | Documented in inventory; not automatable here. |

## Advance

`npm run devteam:advance -- job-018 --to double_checking`
