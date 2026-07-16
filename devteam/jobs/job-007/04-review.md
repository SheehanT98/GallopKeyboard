# Review — job-007 (Whisper polish pass)

| Field | Value |
|-------|-------|
| **Job** | job-007 |
| **Branch** | `cursor/devteam-job-007-execute-plan-007-whisper-polish-pass-c1fc` |
| **PR** | [#20](https://github.com/SheehanT98/GallopKeyboard/pull/20) |
| **Reviewer** | devteam-reviewer (cursor-grok-4.5-high) |
| **Reviewed at** | 2026-07-16T18:50:00Z |
| **Feature SHA** | `5374a718171b2522353a7cbf7360bc6a57357377` |
| **Verdict** | **approve** |

## Summary

**Approve.** Plan 007 scope is implemented: `PolishingTranscriber` decorates streaming, `PolishEngine` wraps Dictus `WhisperContext`, DI binds `Transcriber` → `PolishingTranscriber`, `polishEnabled` defaults to `true`, six unit tests pass, docs/inventory/README updated. Automated verification reconfirmed green. Manual on-device polish acceptance remains deferred (no device / models) — treat as human risk, not a merge blocker for the automated gate.

**Blockers:** none.

## Scope compliance

| Plan item | Status |
|-----------|--------|
| `PolishEngine` / `AsrPolishEngine` wrapping Dictus Whisper | Done — suspend `transcribe` (STOP: async API hit correctly) |
| `WhisperConfig` (path, en, threads) | Done |
| `PolishingTranscriber` decorator, 2 s timeout, fallback | Done |
| `ImeTextCommitter.commitText` atomic replace | Done |
| `RingByteBuffer.snapshotShorts()` | Done |
| `Flags.polishEnabled = true` (kill switch retained) | Done |
| DI: `Transcriber` → `PolishingTranscriber` | Done (`AudioModule` + `WhisperPolishModule`) |
| `PolishingTranscriberTest` (6 cases) | Done |
| `docs/models.md` Whisper section | Done |
| `docs/dictus-inventory.md` Plan 007 additions | Done |
| `plans/README.md` → DONE | Done |
| Out of scope (streaming changes, download UX, langs, post-process) | Respected |

### Acceptable deviations

- Committer calls omit `withContext(Dispatchers.Main)` — matches `StreamingTranscriber`; avoids Robolectric Main deadlock; documented in code summary.
- `AsrPolishEngine.transcribe` is `suspend` (Dictus STOP condition) — no fake sync wrapper.

## Verification evidence

Re-run during review:

| Check | Result |
|-------|--------|
| `bash scripts/verify.sh` | exit 0, `OK` (assemble + testAll + lint + package/model guards) |
| `./gradlew :ime:testDebugUnitTest --tests '*PolishingTranscriberTest'` | BUILD SUCCESSFUL |
| Tester report (`03-test-report.md`) | automated PASS; 6/6 named cases |
| PR #20 | open, base `main` |

### Done criteria

| Criterion | Result |
|-----------|--------|
| `verify.sh` exits 0 | PASS |
| `PolishingTranscriberTest` 6 cases | PASS |
| `polishEnabled` default `true` | PASS |
| DI binds `PolishingTranscriber` | PASS |
| On-device partial → polish / timeout fallback | **DEFERRED** (no adb device / models) |
| `docs/models.md` Whisper layout | PASS |
| Plan 007 inventory section | PASS |
| `plans/README.md` DONE | PASS |

## Risks for the human reviewer

1. **Manual IME/ASR not run** — live partial→polish replace, 2 s timeout on long utterances, cancel, and accuracy vs Gboard need a device with Parakeet + Whisper `base.en` sideloaded (`docs/models.md`).
2. **First polish may load the model** — `PolishEngine.ensureContext` loads on first `transcribe`; cold start can exceed 2 s and hit timeout until context is warm.
3. **`WhisperConfig.nThreads` is log-only** — inference uses `WhisperCpuConfig.preferredThreadCount` inside `WhisperContext`; config field is unused for JNI (harmless for v1).
4. **JNI cancellation-unaware** — documented STOP/maintenance; timeout returns while native work may continue.
5. **Post-release typing race** — plan STOP notes polish `commitText` can clobber keys typed after release before polish returns (Plan 010 territory if confirmed).

## PR

Already open: https://github.com/SheehanT98/GallopKeyboard/pull/20 — no `devteam:open-pr` needed.
