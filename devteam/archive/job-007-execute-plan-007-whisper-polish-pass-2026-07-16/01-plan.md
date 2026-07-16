# Plan 007: Whisper polish pass on stop (final transcript replaces partial)

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to
> the next step. If anything in the "STOP conditions" section occurs,
> stop and report — do not improvise. When done, update the status row
> for this plan in `plans/README.md` — unless a reviewer dispatched you
> and told you they maintain the index.
>
> **Drift check (run first)**:
> ```
> bash scripts/verify.sh
> grep -q "Plan 006 additions" docs/dictus-inventory.md && echo OK
> ```
> Also confirm `Flags.polishEnabled` is present (Plan 006 introduced it).

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED
- **Depends on**: `plans/006-parakeet-streaming-pass.md`
- **Category**: feature
- **Planned at**: commit `99ca844`, 2026-07-16
- **Issue**: —

## Why this matters

Streaming (Plan 006) gives users immediate feedback but is less
accurate than a full-attention model. Whisper polish over the
complete audio buffer, on stop, replaces the streaming partial with a
higher-quality final transcript. This is the pipeline's second half
and the reason for choosing hybrid over streaming-only (see
`docs/adr/0002-hybrid-stt-pipeline.md`).

Acceptance criteria from `CONTEXT.md`:
- (#2) "Accuracy ≥ Gboard voice for everyday English (hybrid polish
  required)" — this plan is *the* accuracy delivery.
- (#4) "Latency <2 s polish on S22 after stop" — encodes as
  `POLISH_TIMEOUT_MS = 2000L` (from ADR-0003 Consequences); on timeout
  we leave the streaming partial in place rather than blocking.

## Current state

After Plan 006:

- `StreamingTranscriber` implements `Transcriber` and writes composing
  text via `ImeTextCommitter`.
- Dictus's `whisper` module wraps whisper.cpp (verified in
  `docs/dictus-inventory.md` — should be Case A; upstream Dictus's
  whole reason for being is Whisper).
- Model directory contract: `context.filesDir/models/whisper/base.en.gguf`
  (or `small.en.gguf`) — see Plan 008 for how they arrive; Plan 007
  only expects them.
- `Flags.polishEnabled` exists and is `false` (Plan 006 default).

## Commands you will need

| Purpose            | Command                                                        | Expected |
|--------------------|----------------------------------------------------------------|----------|
| Build              | `./gradlew --no-daemon assembleDebug`                          | success  |
| Tests              | `./gradlew --no-daemon :ime:testDebugUnitTest`                 | success  |
| Verify             | `bash scripts/verify.sh`                                       | OK       |
| Logcat             | `adb logcat -s WhisperPolish:* -s PolishingTranscriber:*`      | live logs |

## Scope

**In scope**:
- `ime/src/main/java/com/gallopkeyboard/ime/asr/PolishingTranscriber.kt` (new)
  — decorates `StreamingTranscriber` with a polish pass on stop.
- `whisper/src/main/java/com/gallopkeyboard/whisper/PolishEngine.kt` (new)
  — thin wrapper over Dictus's existing Whisper JNI (do not rewrite).
- `whisper/src/main/java/com/gallopkeyboard/whisper/WhisperConfig.kt` (new)
  — model path, language "en", initial prompt (empty), max tokens.
- `core/src/main/java/com/gallopkeyboard/core/flags/Flags.kt` (edit —
  set `polishEnabled = true` by default; leave the flag in place as a
  kill switch).
- DI wiring — bind `Transcriber` to `PolishingTranscriber(streaming =
  StreamingTranscriber(...))`.
- `ime/src/test/java/com/gallopkeyboard/ime/asr/PolishingTranscriberTest.kt` (new).
- `docs/models.md` — append Whisper section.
- `docs/dictus-inventory.md` — append "Plan 007 additions".
- `plans/README.md` status row.

**Out of scope**:
- Streaming behavior changes (that's Plan 006's contract).
- Model download (Plan 008).
- Language selection UI, alternative languages (English only).
- Multi-segment / long-form Whisper streaming — this plan calls
  Whisper as a single non-streaming pass on the full buffer.
- Post-processing (punctuation, capitalization beyond what Whisper
  already emits, or custom vocabulary) — potential Plan 009 follow-up
  if quality is inadequate.

## Git workflow

- Branch: `advisor/007-whisper-polish` off Plan 006.
- Commits:
  1. `feat(whisper): PolishEngine wrapping Dictus whisper.cpp binding`
  2. `feat(ime): PolishingTranscriber decorates streaming`
  3. `feat(ime): enable polish flag by default`
  4. `test(ime): PolishingTranscriber timeout + fallback`
  5. `docs: Plan 007 additions + models Whisper section`

## Steps

### Step 1: `PolishEngine`

Read `docs/dictus-inventory.md` "Whisper integration" — find the
existing Dictus class that calls whisper.cpp JNI (probably in the
`:whisper` module). Wrap it, don't replace it.

```kotlin
interface AsrPolishEngine : AutoCloseable {
    fun init(config: WhisperConfig)
    /** Full-buffer transcription of 16 kHz mono PCM shorts.
     *  Blocks until done or `cancel()` is called. */
    fun transcribe(pcm16k: ShortArray): String
    fun cancel()
}

class PolishEngine(private val dictusWhisper: <DictusWhisperClass>)
    : AsrPolishEngine { ... }
```

Do NOT add features whisper.cpp doesn't natively support (e.g. custom
punctuation, casing). Whisper's own output is fine for v1.

Model file: `context.filesDir/models/whisper/<name>.gguf`. Config
`WhisperConfig(modelPath = ..., language = "en", nThreads =
Runtime.getRuntime().availableProcessors().coerceAtMost(4))`.

**Verify**: `./gradlew --no-daemon :whisper:compileDebugKotlin` — success.

### Step 2: `PolishingTranscriber`

Decorator pattern:

```kotlin
class PolishingTranscriber @Inject constructor(
    private val streaming: StreamingTranscriber,
    private val engine: AsrPolishEngine,
    private val committer: ImeTextCommitter,
    private val dispatcher: RecorderCoroutineDispatcher,
    private val flags: Flags,
) : Transcriber {
    override fun onSessionStart(session) = streaming.onSessionStart(session)
    override fun onAudioFrame(session, frame) {
        streaming.onAudioFrame(session, frame)
    }
    override fun onSessionCancel(session) {
        streaming.onSessionCancel(session)  // also cancels polish (no-op if none)
    }
    override suspend fun onSessionStop(session) {
        streaming.onSessionStop(session)   // sets composing = streaming final
        if (!flags.polishEnabled) return
        val pcm = session.buffer.snapshotShorts()  // add this helper if needed
        val polished = try {
            withTimeout(POLISH_TIMEOUT_MS) {
                withContext(dispatcher) { engine.transcribe(pcm) }
            }
        } catch (t: TimeoutCancellationException) {
            Log.w(TAG, "polish timed out; keeping streaming partial")
            null
        } catch (t: Throwable) {
            Log.e(TAG, "polish failed", t)
            null
        }
        if (polished != null) {
            withContext(Dispatchers.Main) { committer.commitText(polished) }
        } else {
            // Streaming partial is currently composing text.
            // Commit it as-is so the user has SOMETHING typed.
            withContext(Dispatchers.Main) { committer.clearComposing() }
        }
    }
    companion object { const val POLISH_TIMEOUT_MS = 2000L }
}
```

Add `ImeTextCommitter.commitText(text)` (new method — like
`commitFinal` but always sends a `finishComposingText` after).
Reason: `setComposingText` followed by `commitText` is the atomic
replace pattern Android IME docs recommend.

### Step 3: Flip `Flags.polishEnabled` to `true`

Edit `core/.../flags/Flags.kt` — default to `true`. Keep the flag as
a runtime-mutable Boolean so a hidden dev setting could flip it back
if polish regresses. Do not add a UI for the flag in v1.

### Step 4: DI wiring

Replace the `Transcriber` binding with `PolishingTranscriber`:

- In whichever `AudioModule.kt` / manual wiring point Plan 006 used,
  provide `PolishingTranscriber` as `Transcriber` and provide
  `StreamingTranscriber` as its dependency (still concrete class, not
  interface — the decorator wraps a specific implementation).

### Step 5: Unit tests

Create `PolishingTranscriberTest.kt`. Use a fake
`AsrPolishEngine` that can be configured to return a canned string,
throw, or block.

Cases:

- `stop with polish success replaces composing text with polished result`
  (verify `commitText("polished result")` called once).
- `stop with polish timeout leaves streaming partial (clearComposing called)`
  (fake engine blocks past 2000 ms).
- `stop with polish exception falls back to streaming partial`.
- `cancel during recording cancels streaming; polish never runs`.
- `polish disabled by flag → streaming behavior only`.
- `onAudioFrame delegates to streaming exactly once per call`.

For the timeout test, use `runTest` with virtual time so the test
completes instantly.

### Step 6: Update `docs/models.md`

Append a "Whisper (polish)" section:

- Default model: `whisper-base.en.gguf` (~140 MB) or
  `whisper-small.en.gguf` (~470 MB) if user opts in via settings.
- Directory: `getFilesDir()/models/whisper/`.
- Filename convention: `<name>.gguf` (whisper.cpp ggml format).
- Source: HuggingFace `ggerganov/whisper.cpp` released ggml files.

### Step 7: Update inventory + plans README

`docs/dictus-inventory.md` — append `## Plan 007 additions`.
`plans/README.md` — Plan 007 status → `DONE`.

## Test plan

Unit: `PolishingTranscriberTest` (6 cases).

Manual on device (with both Parakeet and Whisper `base.en` sideloaded
per `docs/models.md`):

- Say "The quick brown fox jumps over the lazy dog." — partial appears
  during recording (Plan 006), final polished text replaces it on
  release. Compare accuracy vs. Gboard voice.
- Speak for 2 minutes about anything — polish should complete within
  2 s of release on S22. If it doesn't, timeout kicks in and the
  streaming partial remains (this is expected).
- Speak in a noisy cafe — polish should be clearly better than the
  streaming partial.
- Cancel mid-recording — no text appears.
- Sideload with `small.en` instead of `base.en` — same behavior,
  slower polish; timeout may fire more often. Log the observations.

## Done criteria

- [ ] `bash scripts/verify.sh` exits 0.
- [ ] `PolishingTranscriberTest` (6 cases) passes.
- [ ] `Flags.polishEnabled` default is `true`.
- [ ] DI binds `Transcriber` to `PolishingTranscriber`.
- [ ] On device, spoken sentences show partial then polished text,
      polish completing within 2 s or falling back on timeout.
- [ ] `docs/models.md` documents Whisper model layout.
- [ ] `docs/dictus-inventory.md` "Plan 007 additions" present.
- [ ] `plans/README.md` row for Plan 007 shows `DONE`.

## STOP conditions

- Dictus's whisper wrapper is a suspend/async API rather than
  synchronous — adjust `AsrPolishEngine.transcribe` to `suspend` and
  remove the manual `withContext(dispatcher)`. Do not fake sync-ness.
- Whisper polish on `small.en` regularly exceeds 2000 ms on S22 —
  keep the timeout but consider raising the *default* download to
  `base.en` in Plan 008 (leave `small.en` as opt-in). Update
  `docs/models.md` accordingly.
- Whisper output includes bracketed sound tags like `[MUSIC]`,
  `[BLANK_AUDIO]` — strip a minimal known set in `PolishEngine`
  before returning; do NOT re-invent post-processing. Document the
  strip list.
- Polish returns completely different words than the streaming
  partial, causing visible flicker — this is expected and desired
  (that's the whole point of polish). If users complain, animate the
  composing-to-committed transition; that's Plan 009 UX territory.
- `withTimeout` cancellation doesn't actually stop the JNI whisper
  call (JNI is cancellation-unaware) — the timeout returns but the
  native thread keeps running until it finishes on its own. That's
  acceptable in v1; a proper fix is `whisper_full_cancel` if the
  binding exposes it. Note as maintenance follow-up.
- Committing text via `commitText` clobbers text the user typed after
  releasing the button (rare but possible if they touched a key after
  release before polish returned) — audit the timing. If confirmed,
  scope the fix to Plan 010 hardening.

## Maintenance notes

- The 2000 ms timeout is the sole latency knob. Do not change it
  without updating ADR-0003 Consequences and `CONTEXT.md` acceptance
  criterion #4.
- `PolishingTranscriber` is a decorator, deliberately — do not fold
  it into `StreamingTranscriber`. Keeping them separate means
  disabling polish is one flag flip, and the streaming path can be
  replayed independently in tests.
- If a future plan adds a "no-streaming" mode (release-only), it
  layers by *not* wrapping in `PolishingTranscriber` and directly
  binding a `PolishOnlyTranscriber` that skips partials — do not
  branch inside these classes.
- The `[MUSIC]`-style strip list (if you add one) is a code hazard —
  document every entry with a reproduction from user reports before
  adding it. Silent transcript filtering is a common regression source.
