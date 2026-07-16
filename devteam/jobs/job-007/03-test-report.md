# Job 007 — Test report

| Field | Value |
|-------|-------|
| **Job** | job-007 |
| **Branch** | `cursor/devteam-job-007-execute-plan-007-whisper-polish-pass-c1fc` |
| **PR** | [#20](https://github.com/SheehanT98/GallopKeyboard/pull/20) |
| **Tester** | devteam-tester (composer-2.5) |
| **Tested at** | 2026-07-16T18:46:00Z |
| **SHA tested** | `968395696cbd846f14514adbe3defee5589e89e0` |
| **Verdict** | **PASS** (automated); manual on-device deferred |

## Environment

```bash
npm run android:setup   # SDK marker present, toolchain ready
source scripts/android-env.sh
# ANDROID_HOME=/opt/android-sdk
# JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

| Check | Command | Result |
|-------|---------|--------|
| Branch | `git branch --show-current` | on job-007 branch |
| Device | `adb devices` | no devices attached |

## Commands run

| Check | Command | Result |
|-------|---------|--------|
| Drift guard | `bash scripts/verify.sh` | exit 0, printed `OK` |
| Plan 006 inventory | `grep -q "Plan 006 additions" docs/dictus-inventory.md` | OK |
| Plan 007 inventory | `grep -q "Plan 007 additions" docs/dictus-inventory.md` | OK |
| Polish flag | `Flags.polishEnabled` in `core/.../Flags.kt` | default `true` |
| Polish unit tests | `./gradlew --no-daemon :ime:testDebugUnitTest --tests '*PolishingTranscriberTest'` | 6 tests, 0 failures |
| DI binding | `AudioModule.bindTranscriber(PolishingTranscriber)` | present |
| Models doc | `docs/models.md` Whisper polish section | present |
| Plan status | `plans/README.md` row 007 | DONE |

### verify.sh breakdown

- `assembleDebug` — BUILD SUCCESSFUL
- `testAll` — BUILD SUCCESSFUL
- `lint` — BUILD SUCCESSFUL
- Dictus package grep guard — no hits outside `third_party/`
- Model binary grep guard — no hits outside `third_party/`

### PolishingTranscriberTest (6/6)

| Test | Result |
|------|--------|
| stop with polish success replaces composing text with polished result | pass |
| stop with polish timeout leaves streaming partial clearComposing called | pass |
| stop with polish exception falls back to streaming partial | pass |
| cancel during recording cancels streaming polish never runs | pass |
| polish disabled by flag streaming behavior only | pass |
| onAudioFrame delegates to streaming exactly once per call | pass |

## Done criteria (Plan 007)

| Criterion | Result |
|-----------|--------|
| `bash scripts/verify.sh` exits 0 | PASS |
| `PolishingTranscriberTest` (6 cases) passes | PASS |
| `Flags.polishEnabled` default is `true` | PASS |
| DI binds `Transcriber` to `PolishingTranscriber` | PASS |
| On device, partial then polished text within 2 s or timeout fallback | **NOT RUN** — no device |
| `docs/models.md` documents Whisper model layout | PASS |
| `docs/dictus-inventory.md` Plan 007 additions | PASS |
| `plans/README.md` row 007 `DONE` | PASS |

## Manual on-device (deferred)

Plan acceptance tests require a physical device or configured AVD with Parakeet and Whisper `base.en` models sideloaded per `docs/models.md`. Not executed in this session.

| # | Action | Result |
|---|--------|--------|
| 1 | Sideload Parakeet + Whisper models via `adb push` | not run |
| 2 | Speak sentence — partial during recording, polished on release | not run |
| 3 | 2-minute utterance — polish within 2 s or timeout fallback | not run |
| 4 | Noisy environment — polish better than streaming partial | not run |
| 5 | Cancel mid-recording — no text appears | not run |
| 6 | `small.en` sideload — slower polish, timeout observations | not run |

## Blockers

- **Manual IME/ASR acceptance (non-blocking for automated gate):** Live polish replace, timeout fallback, cancel behavior, and accuracy vs. Gboard not run — no `adb` device attached and models not sideloaded. Reviewer or human should validate per `docs/models.md` before merge.

## Advance

Automated verification passed → `npm run devteam:advance -- job-007 --to reviewing`
