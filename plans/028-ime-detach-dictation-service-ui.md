# Plan 028: Stop IME from mirroring DictationService Recording UI

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat bfc7085..HEAD -- ime/src/main/java/com/gallopkeyboard/ime/DictusImeService.kt app/src/main/java/com/gallopkeyboard/service/DictationService.kt app/src/main/java/com/gallopkeyboard/MainActivity.kt docs/dictus-inventory.md`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.
>
> **Assumption**: Plan 016 mic → voice panel already shipped. Hybrid voice is
> the product path. Companion `DictationService` may remain for app test
> recording — but must not hijack the IME keyboard UI.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED
- **Depends on**: none
- **Category**: tech-debt / bug
- **Planned at**: commit `bfc7085`, 2026-07-20

## Why this matters

The IME still binds `DictationService` and replaces the entire typing/voice
panel with legacy `RecordingScreen` / `TranscribingScreen` whenever the
service state is Recording/Transcribing. Companion-app or onboarding test
recording can blank QWERTY under a focused editor. Dual pipelines (float PCM
+ WhisperProvider vs hybrid SmartVoice) confuse maintenance and daily-driver
reliability. Inventory already says DictationService is companion-oriented;
the IME overlay contradicts that.

## Current state

```kotlin
// DictusImeService.kt
bindDictationService() // onCreate
when (dictationState) {
    is DictationState.Idle -> KeyboardScreen(...) // + PanelHost voice
    is DictationState.Recording -> RecordingScreen(...)
    is DictationState.Transcribing -> TranscribingScreen(...)
}
```

Hybrid path: `PanelHost` + `SmartVoiceButton` + `PolishingTranscriber`.

**Conventions**: Keep `:app` DictationService working for in-app test surface
unless you migrate it in-scope. Prefer minimal IME change: **stop observing /
rendering** service recording states.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| IME + app tests | `./gradlew :ime:testDebugUnitTest :app:testDebugUnitTest` | BUILD SUCCESSFUL |
| Verify | `bash scripts/verify.sh` | `OK` |

## Scope

**In scope**:
- `ime/.../DictusImeService.kt` — remove or gate bind + Recording/Transcribing UI
- Delete or stop calling `handleMicTap` if unused
- App-side only if needed so companion recording does not require IME insert
  (e.g. ensure test surface commits text itself — already does via service)
- `docs/dictus-inventory.md` Plan 028 additions
- `docs/limitations.md` if companion-vs-IME behavior changes
- `plans/README.md`

**Out of scope**:
- Full deletion of `DictationService` / `AudioCaptureManager` / dual
  `ModelCatalog` (larger DEBT-01 wave)
- Dual onboarding merge (separate)
- Rewriting companion ModelsScreen onto ModelRegistry (separate)

## Git workflow

- Branch: `advisor/028-ime-detach-dictation-service-ui`
- Commit: `refactor(ime): stop DictationService from replacing keyboard UI`
- Do NOT push/PR unless instructed.

## Steps

### Step 1: Always show PanelHost typing/voice/clipboard content

In `KeyboardContent`, remove the `when (dictationState)` branches that show
`RecordingScreen`/`TranscribingScreen`. Always use the Idle path content
inside `PanelHost` (KeyboardScreen + voice/clipboard panels).

Remove waveform `LaunchedEffect` tied to service recording if unused.

**Verify**: `./gradlew :ime:compileDebugKotlin` → success.

### Step 2: Stop binding DictationService from the IME

1. Remove `bindDictationService()` from `onCreate` and unbind from `onDestroy`.
2. Remove `ServiceConnection`, `_serviceState`, `dictationController`,
   reflection `getService` binder code.
3. Remove `handleMicTap` if only used by legacy UI.
4. Grep for `DICTATION_SERVICE_CLASS` / `DictationController` in `ime/` —
   should be gone.

**Verify**: `rg -n 'DictationService|DictationController|RecordingScreen' ime/src/main` →
no service bind / no RecordingScreen usage (imports of unused screens removed).

### Step 3: Confirm companion app recording still works

`:app` `MainActivity` / test recording should still start `DictationService`
and insert text in its own UI — not via IME overlay. Spot-check code paths;
fix compile breaks in app tests only if they assumed IME coupling (unlikely).

**Verify**: `./gradlew :app:testDebugUnitTest` → pass.

### Step 4: Docs + verify

Update inventory: IME no longer binds DictationService; hybrid voice is sole
IME dictation path. `bash scripts/verify.sh`.

## Test plan

- No IME unit test previously covered the bind — add a smoke comment in
  inventory for manual: start companion recording while Notes focused →
  keyboard stays QWERTY/voice panel, not legacy RecordingScreen.
- Existing panel/voice tests unchanged.

## Done criteria

- [ ] IME does not bind DictationService
- [ ] IME never shows RecordingScreen/TranscribingScreen from service state
- [ ] Mic / voice panel still open hybrid voice UI
- [ ] `verify.sh` OK; scope respected

## STOP conditions

- A production flow **requires** IME to insert DictationService transcripts
  into the host editor (not just app test surface) — stop and report with
  call sites before deleting bind.
- Removing bind breaks Hilt/AndroidManifest in unexpected ways — fix
  minimally; don’t delete the service from `:app`.

## Maintenance notes

- Follow-up wave: unify ModelCatalog → ModelRegistry; merge onboardings;
  optionally delete DictationService entirely.
- Reviewer: verify Plan 016 mic → `showVoice` still works; clipboard/emoji
  unaffected.
