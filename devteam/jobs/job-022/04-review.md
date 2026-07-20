# Job 022 — Review (Plan 033)

| Field | Value |
|-------|-------|
| **Job** | job-022 |
| **Plan** | `plans/033-async-mic-permission-and-polish-ic.md` |
| **Branch** | `cursor/devteam-job-022-execute-plan-033-async-mic-permission-and-polish-c1fc` |
| **PR** | [#51](https://github.com/SheehanT98/GallopKeyboard/pull/51) |
| **SHA reviewed** | `a901b4a0c590b24bb9640441d2b60ab1871874ee` |
| **Verdict** | **APPROVE** |

## Summary

Approve. Plan 033 done criteria are met: mic permission no longer blocks the pointer thread with `runBlocking`, InputConnection is pinned through Whisper polish so hide-keyboard mid-polish can still commit when the IC is valid, Phase 9 stop-scope behavior is preserved, scope is respected, and tester evidence shows focused + full IME tests and `verify.sh` OK.

## Scope compliance

| In scope | Status |
|----------|--------|
| `SmartVoiceButton` — async permission; no `runBlocking` on pointer thread | **Met** — Compose `scope.launch`; second-press after grant; `permissionRequestInFlight` guard; `rg` shows no `runBlocking` |
| Pin IC for in-flight stop/polish (`InputConnectionSupplier` / committer / `DictusImeService`) | **Met** — `beginPolishCommit` / `endPolishCommit`; `clearSupplierIfIdle()` from `onFinishInputView`; committer uses `connection()` |
| Matching unit tests | **Met** — `InputConnectionSupplierTest` (pin + deferred clear); `PolishingTranscriberTest` mid-polish supplier null still commits |
| `docs/dictus-inventory.md` Plan 033 | **Met** |
| `plans/README.md` → DONE | **Met** |

| Out of scope (must not expand) | Status |
|--------------------------------|--------|
| Permission receiver exported fix | Untouched |
| Whisper preload / ModelLifecycleManager redesign | Untouched |
| `StreamingTranscriber.onSessionCancel` `runBlocking` | Untouched (noted follow-up; acceptable per plan) |

No STOP conditions hit. Drift anchor `32b0d20` absent from history; Phase 9 (`voiceStopScope` / `stoppingJob`) present — consistent with coder/tester notes.

## Done criteria

| Criterion | Result |
|-----------|--------|
| No `runBlocking` in `SmartVoiceButton` gesture path | **PASS** |
| Hide-keyboard mid-polish does not drop successful polish commit when IC still valid | **PASS** (pin + defer clear; unit test) |
| Phase 9 stop-scope behavior preserved | **PASS** (`sessionScope` / `stoppingJob` unchanged) |
| Tests + `verify.sh` OK | **PASS** (per `03-test-report.md`) |
| Scope respected | **PASS** |

## Verification evidence

From `03-test-report.md` (SHA `5a0ff71`; tip still includes that implementation commit):

- `rg -n 'runBlocking' …/SmartVoiceButton.kt` → no matches
- `./gradlew :ime:testDebugUnitTest --tests '*Polishing*' --tests '*VoiceSession*' --tests '*Streaming*' --tests '*InputConnection*'` → BUILD SUCCESSFUL
- `./gradlew :ime:testDebugUnitTest` → BUILD SUCCESSFUL
- `bash scripts/verify.sh` → exit 0, `OK`

Implementation spot-checks on tip:

- Pin after streaming finalize, commit via pinned IC, `finally` → `endPolishCommit`
- Inactive / failed IC ops log and drop (non-PII); no crash path
- DI wires `ImeTextCommitter(inputConnectionSupplier::connection)`

## Risks for the human reviewer

1. **Pin timing window** — `beginPolishCommit()` runs *after* `streaming.onSessionStop`. Hide-keyboard during that short streaming-finalize window can clear the live supplier before pin (`polishInFlight` still false), so polish may pin `null` and drop. Mid-polish (plan done criterion) is covered; this pre-pin race is residual.
2. **Wrong-field STOP mitigation** — dead IC is inferred from `setComposingText` / `finishComposingText` / `commitText` returning false, not an explicit “target changed” check. Typical hide/switch invalidates the IC; residual risk if a stale IC still accepts commits after app switch.
3. **Manual S22** — grant mic on first use (second tap to record); release mic then immediately hide IME — polish should still land in Notes when IC remains valid (plan maintenance note).
4. **Follow-up** — `StreamingTranscriber.onSessionCancel` still uses `runBlocking` on Main (explicitly out of scope).

## PR

Already open: https://github.com/SheehanT98/GallopKeyboard/pull/51 — no `devteam:open-pr` needed.

## Advance

`npm run devteam:advance -- job-022 --to double_checking`
