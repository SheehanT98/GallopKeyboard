# Phase 06: Hybrid STT (Parakeet streaming + Whisper polish)

> Bundled phase plan (phase-06). Execute sub-plans **in order** on one branch.

---

<!-- from plans/006-parakeet-streaming-pass.md -->

# Plan 006: Parakeet streaming pass (partial commits via InputConnection)

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
> grep -q "Plan 005 additions" docs/dictus-inventory.md && echo OK
> ```
> Also read `docs/dictus-inventory.md` "Sherpa-ONNX / Parakeet" section
> — that dictates the shape of Step 2.

## Status

- **Priority**: P1
- **Effort**: L (highest-risk plan in this series)
- **Risk**: HIGH (native ASR + JNI + InputConnection timing)
- **Depends on**: `plans/005-smart-button-and-audio-recorder.md`
- **Category**: feature
- **Planned at**: commit `99ca844`, 2026-07-16
- **Issue**: —

## Why this matters

This is the "streaming" half of the hybrid pipeline
(`docs/adr/0002-hybrid-stt-pipeline.md`). While the user holds/taps
the smart button, partial transcripts appear in the host text field
so they see something happening — latency is masked. Plan 007 layers
the polish pass on top. Together they define the STT UX.

Under `HANDOFF.md`'s ranked acceptance criteria, "accuracy ≥ Gboard
voice" (#2) is the polish pass's job (Plan 007). This plan's job is
"visible progress before the polish returns", which drives the
subjective feel of the keyboard.

## Current state

Read first:

- `docs/dictus-inventory.md` — specifically what the inventory
  recorded under "Sherpa-ONNX / Parakeet". There are two very
  different scenarios:
  - **Case A: Present**. Dictus already ships sherpa-onnx bindings
    and a Parakeet integration path. This plan wires them into our
    `Transcriber` seam.
  - **Case B: Absent**. Dictus only ships whisper.cpp. Then this
    plan starts with a sub-plan (Step 1) to add sherpa-onnx as a
    git submodule and Gradle native library.
- `audio/Transcriber.kt` (from Plan 005) — the interface we implement.
- `docs/adr/0002-hybrid-stt-pipeline.md` — streaming inserts partial
  text as **composing text** so polish (Plan 007) can replace it as a
  single atomic operation.

## Commands you will need

| Purpose            | Command                                                          | Expected                    |
|--------------------|------------------------------------------------------------------|-----------------------------|
| Build              | `./gradlew --no-daemon assembleDebug`                            | BUILD SUCCESSFUL            |
| Native build       | `./gradlew --no-daemon :asr:externalNativeBuildDebug` (if native) | BUILD SUCCESSFUL           |
| Tests              | `./gradlew --no-daemon :asr:testDebugUnitTest`                    | BUILD SUCCESSFUL           |
| Verify             | `bash scripts/verify.sh`                                          | OK                          |
| Push a test model  | `adb push /tmp/parakeet-encoder.onnx /sdcard/GallopKeyboard/models/` | 100% success              |
| Logcat             | `adb logcat -s ParakeetEngine:* -s StreamingTranscriber:*`        | live logs                   |

## Scope

**In scope**:
- `asr/src/main/java/com/gallopkeyboard/asr/parakeet/ParakeetEngine.kt`
  (new) — Kotlin wrapper around sherpa-onnx's streaming Parakeet API.
- `asr/src/main/java/com/gallopkeyboard/asr/parakeet/ParakeetConfig.kt`
  (new) — data class for model paths, decoding params.
- `ime/src/main/java/com/gallopkeyboard/ime/asr/StreamingTranscriber.kt`
  (new) — implements `Transcriber`; feeds `AudioRecorderEngine` frames
  into `ParakeetEngine`; commits partials to `InputConnection`.
- `ime/src/main/java/com/gallopkeyboard/ime/asr/ImeTextCommitter.kt`
  (new) — wraps `InputConnection.setComposingText` / `commitText`;
  handles null-connection edge cases; single write path for tests to
  fake.
- The IME service — replace `StubTranscriber` binding with
  `StreamingTranscriber`.
- `settings.gradle.kts` — include `third_party/sherpa-onnx` submodule
  build if Case B applies.
- `.gitmodules` — add sherpa-onnx submodule if Case B applies.
- `asr/src/androidTest/**` — one instrumented smoke test using a
  short reference wav.
- `asr/src/test/**` — unit tests for `StreamingTranscriber` using a
  fake `ParakeetEngine`.
- `docs/dictus-inventory.md` — append "Plan 006 additions" +
  document the model file layout expected.
- `docs/models.md` (new) — where model files are expected on device
  (`/sdcard/Android/data/com.gallopkeyboard.ime/files/models/parakeet-*`).
- `plans/README.md` status row.

**Out of scope**:
- Polish pass (Whisper) — Plan 007.
- Model download UX — Plan 008.
- Any change to `SmartVoiceButton` gesture logic (Plan 005).
- Any keyboard visual polish or clipboard/emoji (Plan 009).
- Any release-signing or ProGuard/R8 rules — Plan 010.
- Language detection or multi-language switching — English only.

## Git workflow

- Branch: `advisor/006-parakeet-streaming` off Plan 005.
- Commit granularity depends on Case A vs B:
  - Case A: `feat(asr): ParakeetEngine wrapping sherpa-onnx streaming API`
  - Case B: prepend `chore: add sherpa-onnx as git submodule` +
    `build(asr): sherpa-onnx JNI bindings and native build config`
- Then in all cases:
  - `feat(ime): ImeTextCommitter (composing-text helper)`
  - `feat(ime): StreamingTranscriber implements Transcriber`
  - `feat(ime): bind StreamingTranscriber in DI`
  - `test(asr): fake-engine streaming transcriber unit tests`
  - `test(asr): instrumented smoke test with reference wav`
  - `docs: Plan 006 additions + models layout`

## Steps

### Step 1: Ensure sherpa-onnx bindings exist (Case B only)

Skip this step if inventory Case A applies. Otherwise:

- Add submodule:
  `git submodule add https://github.com/k2-fsa/sherpa-onnx.git third_party/sherpa-onnx`
  Pin to a released tag; record the tag in the commit message.
- Add the `asr` module's native build config (`externalNativeBuild` in
  `asr/build.gradle.kts`, `CMakeLists.txt` under `asr/src/main/cpp/`).
  Reference sherpa-onnx's own Android docs; do not hand-write CMake
  from scratch.
- Add a Kotlin façade in `asr/src/main/java/com/gallopkeyboard/asr/sherpa/`
  that binds only the minimal Parakeet-streaming subset we need
  (`OnlineRecognizerConfig`, `OnlineStream.acceptWaveform(...)`,
  `OnlineRecognizer.decode`, `OnlineRecognizer.getResult`). Do not
  expose the entire sherpa-onnx surface — we don't want to maintain
  that binding contract.
- Native ABIs: `arm64-v8a` is required (S22). `x86_64` is optional
  (emulator convenience). Do NOT ship `armeabi-v7a` (S22 is 64-bit
  only).

**STOP if** the sherpa-onnx build fails on ARM64 with the toolchain in
CI. Debugging native builds is not in scope for a cheap executor —
report the exact failure and defer to a specialist plan.

**Verify**:
- `./gradlew --no-daemon :asr:externalNativeBuildDebug` — `BUILD SUCCESSFUL`.
- APK includes `arm64-v8a/libsherpa-onnx-jni.so`
  (`unzip -l app/build/outputs/apk/debug/app-debug.apk | grep sherpa`).

### Step 2: `ParakeetEngine` — the streaming façade

Interface (regardless of Case A/B):

```kotlin
interface StreamingAsrEngine : AutoCloseable {
    fun init(config: ParakeetConfig)     // load model files
    fun beginStream()                    // called at session start
    fun acceptFrame(pcm16k: ShortArray)  // called per audio frame
    fun currentPartial(): String         // best-effort partial (empty ok)
    fun finalize(): String               // called at session stop
    fun cancel()                         // called at session cancel
}

class ParakeetEngine(...) : StreamingAsrEngine { ... }
```

Model files expected at:

- `context.filesDir/models/parakeet/encoder.onnx`
- `context.filesDir/models/parakeet/decoder.onnx`
- `context.filesDir/models/parakeet/joiner.onnx`
- `context.filesDir/models/parakeet/tokens.txt`

If any file is missing at `init()`, throw
`AsrModelMissingException(files = listOfMissing)`. Plan 008 handles
downloading; Plan 006 only *expects* the files.

Locking: `ParakeetEngine` is not thread-safe; wrap access in the same
`RecorderCoroutineDispatcher` used by Plan 005 so all engine calls run
serial. Do NOT introduce a second thread for ASR — one recorder
dispatcher, all serial, prevents JNI reentrancy bugs.

**Verify** (unit, with a fake): a Kotlin `FakeStreamingAsrEngine`
returning canned partials proves the interface compiles and is
usable.

### Step 3: `ImeTextCommitter`

Create `ime/asr/ImeTextCommitter.kt`:

```kotlin
class ImeTextCommitter(private val ic: () -> InputConnection?) {
    fun setComposing(text: String) { ic()?.setComposingText(text, 1) }
    fun commitFinal(text: String)  { ic()?.commitText(text, 1) }
    fun clearComposing()           { ic()?.finishComposingText() }
}
```

- Every call null-checks the connection (input focus can be lost mid-
  session; host apps rotate, users background the app, etc.).
- No batching yet; if partials arrive faster than the host can accept,
  the IME layer will handle back-pressure at the collector level
  (Step 4).

### Step 4: `StreamingTranscriber` — the `Transcriber` implementation

Create `ime/asr/StreamingTranscriber.kt`:

```kotlin
class StreamingTranscriber @Inject constructor(
    private val engine: StreamingAsrEngine,
    private val committer: ImeTextCommitter,
    private val dispatcher: RecorderCoroutineDispatcher,
) : Transcriber { ... }
```

Behavior:

- `onSessionStart(session)`:
  - `engine.beginStream()` on `dispatcher`.
  - `committer.setComposing("")` — begin a composing region so polish
    (Plan 007) can replace atomically.
- `onAudioFrame(session, frame)`:
  - `engine.acceptFrame(frame)` on `dispatcher`.
  - Every N frames (N=5, i.e. every 500 ms), poll
    `engine.currentPartial()`; if it differs from the last committed
    partial, `committer.setComposing(newPartial)`.
- `onSessionStop(session)` (suspend):
  - `final = engine.finalize()` on `dispatcher`.
  - `committer.setComposing(final)` — leave as composing text so Plan
    7's polish can `commitText(polished)` in one atomic replace.
  - If polish is not yet implemented (Plan 007 not landed), also call
    `committer.clearComposing()` after 50 ms so the streaming result
    becomes committed text. Guard this behavior behind a feature flag
    `Flags.polishEnabled` (default false until Plan 007 sets it true).
- `onSessionCancel(session)`:
  - `engine.cancel()` on `dispatcher`.
  - `committer.clearComposing()` (drops the composing region, leaves
    host field's prior text intact).

Handling failures:

- Any `AsrModelMissingException` inside `onSessionStart` → toast
  "Voice models not installed" (`R.string.asr_models_missing`) and
  call `committer.clearComposing()`. Do NOT crash.
- Any JNI exception mid-session → log, `clearComposing()`, and toast
  "Voice recognition failed — please try again". Do NOT re-throw
  through the IME.

### Step 5: Bind `StreamingTranscriber` (replace stub)

Wherever Plan 005 bound `Transcriber` to `StubTranscriber`, replace
with `StreamingTranscriber`. Keep `StubTranscriber` in-source for
tests — mark it `@VisibleForTesting`.

If Hilt, this is a one-line binding change; if manual, this is a
constructor arg change in the IME service.

### Step 6: Unit test `StreamingTranscriber` with a fake engine

Create `asr/src/test/java/com/gallopkeyboard/asr/StreamingTranscriberTest.kt`.

Use a fake `StreamingAsrEngine` whose `currentPartial()` returns a
scripted sequence: `""`, `""`, `"hello"`, `"hello world"`,
`"hello world"`, `"hello world."`.

Use a fake `ImeTextCommitter` that records every call.

Cases:

- `session start → committer receives setComposing("") once`
- `10 frames deliver 2 partial updates (every 5 frames)`
- `duplicate partials are not re-committed`
- `session stop calls finalize + setComposing(final)`
- `session cancel clears composing`
- `model-missing exception on start → toast callback fired, no
  setComposing call`
- `polish-flag off after stop → committer.clearComposing() called after 50 ms`

### Step 7: Instrumented smoke test with reference audio

Create `asr/src/androidTest/java/com/gallopkeyboard/asr/ParakeetSmokeTest.kt`.

- Bundle a short reference WAV under
  `asr/src/androidTest/assets/reference-hello-world.wav` (5 seconds,
  16 kHz mono, "hello world how are you today"). Record it yourself
  or use a public domain sample.
- The test loads the WAV, chunks into 1600-sample frames, feeds them
  to a real `ParakeetEngine` (models loaded from
  `context.filesDir/models/parakeet/` — see Step 8 for how CI populates).
- Assert `finalize()` returns a string that contains "hello" AND
  "world" (case-insensitive, whitespace-normalized).

This test is optional in CI (native models are large). Gate it behind
an environment variable `RUN_ASR_SMOKE=1` and skip otherwise.

### Step 8: Document model file layout

Create `docs/models.md`. Content:

- Directory layout on device:
  `getFilesDir()/models/parakeet/{encoder,decoder,joiner}.onnx`
  and `tokens.txt`.
- Rough sizes: Parakeet EN streaming ~80 MB total.
- Where to obtain the files (link to the sherpa-onnx model releases
  page). Do NOT commit model binaries.
- How to sideload for local dev before Plan 008 lands: `adb push` to
  `/sdcard/Android/data/com.gallopkeyboard.ime/files/models/parakeet/`.

### Step 9: Update inventory + plans README

`docs/dictus-inventory.md` — append `## Plan 006 additions`.
`plans/README.md` — Plan 006 status → `DONE`.

## Test plan

Unit: `StreamingTranscriberTest` (7 cases) + any tiny tests around
`ImeTextCommitter` null handling.

Instrumented (optional in CI, mandatory manually on device):

- With models sideloaded via `adb push`, launch the app, switch to
  voice panel, hold-and-speak "hello world" — see partial text appear
  in the host field, replaced with a final transcript on release.
- Cancel mid-recording (drag off button) — no text appears in host
  field.
- Speak in a noisy environment — partials still appear, may be
  garbled; that's expected (Whisper polish in Plan 007 improves it).
- Recording without model files present → toast about missing models,
  no crash.

## Done criteria

- [ ] `bash scripts/verify.sh` exits 0.
- [ ] `./gradlew :asr:testDebugUnitTest` and
      `./gradlew :ime:testDebugUnitTest` pass; ≥7 new tests exist.
- [ ] APK includes ARM64 sherpa-onnx `.so` (Case B) OR wraps
      pre-existing Dictus bindings (Case A).
- [ ] `StreamingTranscriber` is the bound `Transcriber` impl (grep
      DI setup).
- [ ] With models sideloaded, on-device speaking "hello world" causes
      partial text to appear in Notes.
- [ ] `docs/models.md` exists.
- [ ] `docs/dictus-inventory.md` "Plan 006 additions" present.
- [ ] `plans/README.md` row for Plan 006 shows `DONE`.

## STOP conditions

- Inventory Case B applies but sherpa-onnx doesn't cross-compile for
  Android in your environment — report the CMake/NDK failure exactly.
  Native toolchain debugging is a specialist follow-up plan.
- The APK size after adding sherpa-onnx exceeds 100 MB — reconsider
  bundling; models are already in-scope for on-device download (Plan
  008), and sherpa-onnx runtime .so alone shouldn't be this big.
  Investigate CMake `-DBUILD_SHARED_LIBS`, strip settings, and remove
  unused features.
- Streaming partials arrive at > 20/sec and the IME main thread
  starts jank-ing (`Choreographer` skipped-frames warnings in logcat)
  — throttle to 4 Hz in `StreamingTranscriber` (change N from 5 to
  20 frames, i.e. every 2 seconds) and re-test; document the change.
- Composing text is visible in the host app but polish (Plan 7,
  future) can't atomically replace it — the composing region model
  is broken. Test with Notes, WhatsApp, Gmail; if one specific app
  handles composing text poorly, document as a known issue (do not
  refactor to `commitText`-only, which would break Plan 007's atomic
  replace).
- ANR triggered during long dictation — the `dispatcher` isn't
  actually a background thread. Verify by adding a
  `Log.d(TAG, Thread.currentThread().name)` at engine entry.
- Model files present but recognition returns garbage — likely a
  sample-rate mismatch (Parakeet expects 16 kHz) or endian issue.
  Verify `SAMPLE_RATE = 16000` in Plan 005's recorder and that PCM is
  little-endian 16-bit signed.

## Maintenance notes

- The `StreamingAsrEngine` interface is intentionally minimal so we
  can swap engines (whisper-streaming, Parakeet, others) without
  touching `StreamingTranscriber` or the IME. Do not leak
  sherpa-onnx types past the `asr/parakeet` package boundary.
- The 500 ms polling interval is a UX tuning knob. If polish feels
  slow, lower it; if the host app janks, raise it. Log every change
  in the commit message so we can bisect UX regressions.
- The polish flag (`Flags.polishEnabled`) exists so Plan 006 can ship
  independently of Plan 007. Once Plan 007 lands, delete the flag —
  or keep it as a temporary rollback switch through Plan 010, then
  delete.
- If a future locale plan lands, model paths gain a language segment
  (e.g. `models/parakeet-fr/`); update `docs/models.md` and
  `ParakeetConfig` to take a locale. Do not premature-abstract this
  now — English only per `CONTEXT.md`.
- Do not add any online fallback ("if local model missing, hit
  cloud"). HANDOFF is explicit: 100% offline. Adding a cloud path
  requires a new ADR that overrides `docs/adr/0001-fork-dictus.md`
  intent.


---

<!-- from plans/007-whisper-polish-pass.md -->

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

