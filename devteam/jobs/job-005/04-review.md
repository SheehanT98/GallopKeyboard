# Job 005 — Review

| Field | Value |
|-------|-------|
| **Job** | job-005 |
| **Branch** | `cursor/devteam-job-005-execute-plan-005-smart-button-audiorecorder-c1fc` |
| **PR** | https://github.com/SheehanT98/GallopKeyboard/pull/16 (OPEN) |
| **Base** | `origin/main` |
| **Reviewed at tip** | `62d712e` |
| **Verdict** | **approve** |

## Summary

Plan 005 smart voice button + 16 kHz mono PCM recorder matches scope: `RingByteBuffer`, `AudioRecorderEngine`, `Transcriber`/`StubTranscriber`, `GestureFsm`/`SmartVoiceButton`, IME permission proxy, 11 unit tests, inventory + plans index. Automated verification **PASS** (`verify.sh`, ring + gesture tests). Manual on-device (permission dialog, gesture record, 3-min hold) was deferred — **risk for human, not an auto-block**. PR is merge-conflicting with `main` on **devteam meta only** (`devteam/README.md`, `meta.json`); product `:ime` paths merge cleanly — sync before merge. **Approve** for double-check.

## Scope compliance

| Area | Status |
|------|--------|
| Manifest `RECORD_AUDIO` + mic feature; no `WAKE_LOCK` / `FOREGROUND_SERVICE` / `INTERNET` | Present |
| `RingByteBuffer` + capacity 5 min PCM16 + overflow drop | Present |
| `AudioRecorderEngine` — 16 kHz mono PCM16, `VOICE_RECOGNITION`, cold `Flow`, dedicated dispatcher | Present |
| `AudioSession` + `durationMs()` | Present |
| `Transcriber` + `StubTranscriber` Hilt-bound (`AudioModule`) | Present |
| `GestureFsm` — ADR-0003 (400 ms hold, 48 dp slop, tap-toggle / hold / cancel) | Present |
| `SmartVoiceButton` — `pointerInput`, recording visual (`error` + waveform dot) | Present |
| `PermissionRequester` + `PermissionProxyActivity` (IME proxy) | Present (activity co-located in `PermissionRequester.kt`) |
| `VoicePanel` wires `SmartVoiceButton` | Present |
| Unit tests — `RingByteBufferTest` (5) + `GestureFsmTest` (6) = 11 | Present |
| `docs/dictus-inventory.md` Plan 005 additions | Present |
| `plans/README.md` row 005 → DONE | Present |
| ASR / InputConnection commit / VAD / file recording | Out of scope (correctly absent) |

Minor deviations (acceptable):

- Single squashed `feat(ime)` commit vs plan’s eight granular commits — fine for quick mode.
- `PermissionProxyActivity` lives in `PermissionRequester.kt` (inventory still lists a separate `.kt` path — doc nit only).
- Inventory notes separate IME PCM16 pipeline vs app `AudioCaptureManager` (Float32 + FGS) — correct STOP handling.

## Verification evidence

Relies on tester artifact (`03-test-report.md`, SHA `748c8f1`) plus spot-check of sources at tip `62d712e`:

| Criterion | Result |
|-----------|--------|
| `bash scripts/verify.sh` | PASS (tester) |
| `:ime` unit tests ≥10 new (gesture + ring) | PASS — 11/11 (tester) |
| Manifest `RECORD_AUDIO` only (forbidden perms absent) | Confirmed |
| `Transcriber` + `StubTranscriber` bound | Confirmed (`AudioModule`) |
| Inventory Plan 005 + plans `DONE` | Confirmed |
| PR #16 | OPEN (do not recreate) |
| Manual 3-min / permission / gesture on device | **NOT RUN** — no adb device |

## Risks for the human reviewer

1. **Manual IME/audio acceptance deferred**: Permission dialog, tap-toggle, hold-release, drag-cancel, and 3-min recording not exercised on device. Sideload `app-debug.apk` per `docs/sideload-galaxy-s22.md` before merge if possible.
2. **PR merge conflict with `main`**: Conflicts only in `devteam/README.md` and `devteam/jobs/job-005/meta.json` (job-004 archive landed on main). Product code is clean — run `npm run devteam:sync -- job-005` (or equivalent) before merge.
3. **`runBlocking` in gesture handler**: First-press permission request blocks the pointer coroutine via `runBlocking` — may jank if the system dialog is slow; worth watching on device (not an auto-block).
4. **Panel switch mid-record**: `DisposableEffect` cancels the recording job on dispose but does not explicitly call `Transcriber.onSessionCancel`; plan allows deferring UX polish to Plan 009.
5. **Inventory path nit**: Lists `PermissionProxyActivity.kt` as its own file; implementation is in `PermissionRequester.kt`.

## Blockers

None.

## Advance

`npm run devteam:advance -- job-005 --to double_checking`
