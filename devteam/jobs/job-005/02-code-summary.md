# Code summary — job-005 (Plan 005)

## What landed

Phase 2 smart voice button + 16 kHz mono PCM recorder for the voice panel.

### Audio pipeline (`ime/.../audio/`)

- **RingByteBuffer** — thread-safe 5-minute PCM ring with overflow drop + `droppedBytes()`
- **AudioRecorderEngine** — `AudioRecord` at 16 kHz mono PCM16 (`VOICE_RECOGNITION`), cold `Flow<ShortArray>` on dedicated dispatcher
- **AudioSession** — timestamps + buffer reference; `durationMs()` for stub logging
- **Transcriber** interface + **StubTranscriber** (logs session size; Hilt-bound via `AudioModule`)

### Gesture + UI (`ime/.../panel/`)

- **GestureFsm** — pure Kotlin ADR-0003 FSM (tap-toggle, hold, 48 dp cancel slop)
- **SmartVoiceButton** — Compose button with `pointerInput` gestures, recording visual state (`MaterialTheme.colorScheme.error`), PCM collection into session buffer
- **PermissionRequester** + **PermissionProxyActivity** — IME-scoped `RECORD_AUDIO` runtime request

### Wiring

- `AndroidManifest.xml` — `RECORD_AUDIO`, microphone feature, proxy activity
- `VoicePanel` / `PanelHost` / `DictusImeService` — pass Hilt audio deps into smart button
- `DictusImeEntryPoint` — exposes `audioRecorderEngine()`, `transcriber()`, `permissionRequester()`

### Tests (11 new)

- `RingByteBufferTest` — 5 cases (capacity, overflow, concurrent stress, clear)
- `GestureFsmTest` — 6 ADR-0003 sequences

### Docs

- `docs/dictus-inventory.md` — Plan 005 additions (notes separate IME pipeline vs app `AudioCaptureManager`)
- `plans/README.md` — Plan 005 → DONE

## STOP conditions

None hit.

- `app/AudioCaptureManager` exists but uses Float32 + foreground service — different interface; new `:ime` PCM16 pipeline per plan (documented in inventory).
- `pointerInput` compiles on current Compose BOM (no bump required).
- Device manual tests (3-min record, permission dialog) not run in CI — left for testing stage.

## Verification

```
source scripts/android-env.sh && bash scripts/verify.sh  # OK
```
