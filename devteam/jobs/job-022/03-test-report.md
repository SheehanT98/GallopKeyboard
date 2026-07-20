# Job 022 — Test report

| Field | Value |
|-------|-------|
| **Job** | job-022 |
| **Branch** | `cursor/devteam-job-022-execute-plan-033-async-mic-permission-and-polish-c1fc` |
| **PR** | [#51](https://github.com/SheehanT98/GallopKeyboard/pull/51) |
| **Plan** | `plans/033-async-mic-permission-and-polish-ic.md` |
| **Tester** | devteam-tester (composer-2.5) |
| **Tested at** | 2026-07-20T22:35:00Z |
| **SHA tested** | `5a0ff712725f9480bf7099171bd64d45823f995b` |
| **Verdict** | **PASS** |

## Environment

| Check | Command | Result |
|-------|---------|--------|
| Branch | `git branch --show-current` | `cursor/devteam-job-022-execute-plan-033-async-mic-permission-and-polish-c1fc` |
| Job status | `devteam/jobs/job-022/meta.json` | `testing` |
| Pull | `git pull origin cursor/devteam-job-022-execute-plan-033-async-mic-permission-and-polish-c1fc` | Already up to date |

## Commands run

| Check | Command | Result |
|-------|---------|--------|
| Plan drift check | `git diff --stat 32b0d20..HEAD -- SmartVoiceButton.kt DictusImeService.kt PolishingTranscriber.kt ImeTextCommitter.kt AsrModule.kt VoiceSessionCleanup.kt` | **N/A** — anchor `32b0d20` not in repo history; Phase 9 (`voiceStopScope` / `stoppingJob`) present on branch |
| No `runBlocking` in SmartVoiceButton | `rg -n 'runBlocking' ime/src/main/java/com/gallopkeyboard/ime/panel/SmartVoiceButton.kt` | No matches |
| Focused IME tests | `./gradlew :ime:testDebugUnitTest --tests '*Polishing*' --tests '*VoiceSession*' --tests '*Streaming*' --tests '*InputConnection*'` | BUILD SUCCESSFUL |
| Full IME tests | `./gradlew :ime:testDebugUnitTest` | BUILD SUCCESSFUL |
| Full verify | `bash scripts/verify.sh` | exit 0, ends with `OK` |

## Done criteria (Plan 033)

| Criterion | Result |
|-----------|--------|
| No `runBlocking` in `SmartVoiceButton` gesture path | **PASS** — async `permissionRequester.request` on Compose scope; second-press after grant |
| Hide-keyboard mid-polish does not drop successful polish commit when IC still valid | **PASS** — `InputConnectionSupplier.beginPolishCommit` / `endPolishCommit`; `PolishingTranscriberTest` pins IC when supplier nulled mid-polish |
| Phase 9 stop-scope behavior preserved | **PASS** — `voiceStopScope` / `stoppingJob` unchanged; polish still on stop scope |
| Tests + `verify.sh` OK | **PASS** |
| Inventory + README updated | **PASS** — `docs/dictus-inventory.md` Plan 033; `plans/README.md` row 033 → `DONE` |
| Scope respected | **PASS** — no permission receiver export fix, Whisper preload redesign, or `StreamingTranscriber.onSessionCancel` `runBlocking` change |

## STOP conditions

| Condition | Outcome |
|-----------|---------|
| Pinning IC commits into wrong field after app switch | **Not hit** — inactive IC dropped silently (`InputConnectionSupplierTest`); deferred clear via `clearSupplierIfIdle()` |
| Permission API cannot be made async without broad rewrite | **Not hit** — minimal async wrapper; no Hilt graph explosion |

## Blockers

None.

## Advance

`npm run devteam:advance -- job-022 --to reviewing`
