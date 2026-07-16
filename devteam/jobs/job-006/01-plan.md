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
