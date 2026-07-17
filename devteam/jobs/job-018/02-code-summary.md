# Code summary — job-018 (Plan 016)

## Commit message

`fix(ime): cancel ASR on SmartVoice dispose; unify mic`

## What changed

### 1. Cancel ASR on SmartVoiceButton dispose

- Added `VoiceSessionCleanup.kt` with `cancelActiveSession(transcriber, session)` — shared cleanup that calls `transcriber.onSessionCancel`.
- `SmartVoiceButton` `DisposableEffect` now cancels jobs, clears visual state, calls `cancelActiveSession`, then `fsm.reset()` (previously only cancelled jobs + `fsm.reset()`, leaking Parakeet streams).
- `onSessionCancel` gesture path refactored to use the same helper.

### 2. Unify dual mic entry points

**Product decision:** bottom-row `KeyType.MIC` opens the voice panel (`onVoicePanelToggle` → `PanelController.showVoice`) instead of `DictationService` recording.

- `KeyboardScreen`: `KeyType.MIC` → `onVoicePanelToggle()`; removed `onMicTap` parameter.
- `DictusImeService`: removed `onMicTap = { handleMicTap() }` from `KeyboardScreen` wiring.
- `DictationService` / `handleMicTap` remain for companion `RecordingScreen` only (out of scope).

### 3. Tests

- `VoiceSessionCleanupTest` — cancel invokes transcriber, null no-op, idempotent double-call.
- `GestureFsmTest` — comment documenting dispose must call `cancelActiveSession`.

### 4. Docs

- `docs/dictus-inventory.md` — Plan 016 section.
- `plans/README.md` — Plan 016 status → DONE.

## Files touched

| File | Change |
|------|--------|
| `ime/.../panel/VoiceSessionCleanup.kt` | **new** — cancel helper |
| `ime/.../panel/SmartVoiceButton.kt` | dispose + gesture cancel use helper |
| `ime/.../ui/KeyboardScreen.kt` | MIC → voice panel |
| `ime/.../DictusImeService.kt` | drop keyboard `onMicTap` wiring |
| `ime/src/test/.../VoiceSessionCleanupTest.kt` | **new** |
| `ime/src/test/.../GestureFsmTest.kt` | dispose contract note |
| `docs/dictus-inventory.md` | Plan 016 |
| `plans/README.md` | status |

## Verification

```
./gradlew :ime:testDebugUnitTest --tests 'com.gallopkeyboard.ime.panel.*'  → SUCCESS
./gradlew :ime:testDebugUnitTest --tests 'com.gallopkeyboard.ime.model.KeyboardLayoutTest'  → SUCCESS
bash scripts/verify.sh  → OK
```

## Drift note

`DictusImeService.kt` had unrelated Plan 015 drift (async `verifyInstalledIfDue` on IO). SmartVoiceButton / mic paths matched plan baseline; proceeded.

## Blockers

None.
