# Plan 005: Smart voice button gesture + AudioRecorder (16 kHz mono PCM)

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
> grep -q "Plan 004 additions" docs/dictus-inventory.md && echo OK
> ```
> Both must succeed. If Plan 004's additions section is missing,
> Plan 004 didn't complete Step 6 — STOP.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED (audio permission + hardware surface)
- **Depends on**: `plans/004-panel-controller-and-voice-panel-scaffold.md`
- **Category**: feature
- **Planned at**: commit `99ca844`, 2026-07-16
- **Issue**: —

## Why this matters

This is Phase 2 of `HANDOFF.md`. It gives the voice panel a real button
that:

1. Records **16 kHz mono PCM** into a bounded ring buffer for as long
   as the user wants (HANDOFF acceptance: "3-minute recording without
   auto-stop").
2. Recognizes the two gestures from `docs/adr/0003-smart-button-gesture-spec.md`:
   tap-tap toggle and hold-past-threshold-then-release. Both end by
   producing a "session" object with the recorded audio buffer.

**No STT integration in this plan** — the buffer is handed to a
`StubTranscriber` that only logs its size. Plan 006 replaces the stub
with Parakeet streaming; Plan 007 wires Whisper polish. Separating these
means we can prove the audio pipeline in isolation with a working APK
on device before touching ASR.

## Current state

After Plan 004:

- `PanelController` and `VoicePanel` exist under
  `ime/src/main/java/com/gallopkeyboard/ime/panel/`.
- `VoicePanel.kt` contains a placeholder full-width button that has
  no touch handler.
- No `RECORD_AUDIO` permission is declared or requested.
- No audio code exists.
- `docs/dictus-inventory.md` has a "Plan 004 additions" section.

Constants from `docs/adr/0003-smart-button-gesture-spec.md`:

- `HOLD_THRESHOLD_MS = 400L`
- `CANCEL_SLOP_DP = 48` (drag outside this radius from button center
  cancels)
- `POLISH_TIMEOUT_MS = 2000L` (used in Plan 007, not here)

Audio spec:

- Sample rate: **16000 Hz** (matches whisper.cpp and Parakeet).
- Channel: **mono** (`CHANNEL_IN_MONO`).
- Encoding: **`ENCODING_PCM_16BIT`**.
- Source: **`MediaRecorder.AudioSource.VOICE_RECOGNITION`** (better
  filtering than `MIC` for speech; standard for on-device ASR).
- Buffer: PCM frames streamed to an in-memory ring buffer bounded at
  **5 minutes** of audio (5 × 60 × 16000 × 2 bytes = 9.6 MB). Beyond 5
  min, drop the oldest chunk and log a `Log.w`. HANDOFF's "3 minutes
  without auto-stop" is the acceptance minimum; 5 minutes is a safety
  ceiling.

## Commands you will need

| Purpose            | Command                                                        | Expected                    |
|--------------------|----------------------------------------------------------------|-----------------------------|
| Build              | `./gradlew --no-daemon assembleDebug`                          | BUILD SUCCESSFUL            |
| Tests              | `./gradlew --no-daemon :ime:testDebugUnitTest`                 | BUILD SUCCESSFUL            |
| Full verify        | `bash scripts/verify.sh`                                       | OK                          |
| Grant mic at CLI   | `adb shell pm grant com.gallopkeyboard.ime android.permission.RECORD_AUDIO` | (silent on success) |
| Logcat             | `adb logcat -s GallopKeyboardIme:* -s AudioRecorder:*`         | live logs                   |

## Scope

**In scope** (all under `ime/src/main/`):
- `ime/src/main/AndroidManifest.xml` — add `<uses-permission android:name="android.permission.RECORD_AUDIO"/>`.
- `audio/AudioSession.kt` (new) — the recording session object (holds
  PCM buffer + metadata).
- `audio/RingByteBuffer.kt` (new) — a fixed-capacity thread-safe ring
  buffer for PCM bytes.
- `audio/AudioRecorderEngine.kt` (new) — wraps `AudioRecord`, exposes
  `start()` / `stop()` / `Flow<ShortArray>` of frames.
- `audio/RecorderCoroutineDispatcher.kt` (new) — single-thread
  dispatcher for the audio loop.
- `audio/StubTranscriber.kt` (new) — implements a `Transcriber`
  interface; logs "would transcribe N ms of audio" on stop. This is
  the seam Plans 006/007 will replace.
- `audio/Transcriber.kt` (new) — the interface.
- `panel/SmartVoiceButton.kt` (new — Compose) — gesture detector +
  visual states.
- `panel/VoicePanel.kt` (edit) — replace placeholder button with
  `SmartVoiceButton`.
- `panel/PermissionRequester.kt` (new) — one-shot helper to request
  `RECORD_AUDIO` from within an IME (special dance; see Step 5).
- `ime/src/main/res/values/strings.xml` — new strings for permission
  rationale, recording state, error toasts.
- `ime/src/test/java/com/gallopkeyboard/ime/panel/GestureFsmTest.kt` (new)
- `ime/src/test/java/com/gallopkeyboard/ime/audio/RingByteBufferTest.kt` (new)
- `docs/dictus-inventory.md` — append "Plan 005 additions".
- `plans/README.md` status row.

**Out of scope**:
- Actual ASR (Parakeet, Whisper) — Plans 006/007.
- Committing text to `InputConnection` — the stub only logs.
- VAD (voice activity detection) — the user drives start/stop.
- Noise suppression / AEC — Android's `VOICE_RECOGNITION` source
  already applies system preprocessors.
- Recording to a file — buffer stays in memory. On-disk buffering is
  Plan 010 hardening if the 5-min limit ever hurts.
- Wake locks — a foreground service is unnecessary for IME-scoped
  recording (the IME is already the foreground UI).

## Git workflow

- Branch: `advisor/005-smart-button-and-recorder` off Plan 004.
- Commits, in order:
  1. `feat(ime): declare RECORD_AUDIO permission`
  2. `feat(audio): add RingByteBuffer + tests`
  3. `feat(audio): AudioRecorderEngine (16 kHz mono PCM)`
  4. `feat(audio): Transcriber interface + StubTranscriber`
  5. `feat(ime): SmartVoiceButton with gesture FSM`
  6. `feat(ime): wire smart button into VoicePanel; request mic permission`
  7. `test(ime): gesture FSM + ring buffer coverage`
  8. `docs: record Plan 005 additions in inventory`

## Steps

### Step 1: Declare `RECORD_AUDIO`

`ime/src/main/AndroidManifest.xml` — add inside `<manifest>` at top
level (not inside `<application>`):

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

Also add these features (informational; not enforced):

```xml
<uses-feature android:name="android.hardware.microphone" android:required="true" />
```

Do NOT add `WAKE_LOCK`, `FOREGROUND_SERVICE`, or `INTERNET` — we don't
need any of them for on-device recording within the IME's own
lifecycle.

**Verify**: `grep RECORD_AUDIO ime/src/main/AndroidManifest.xml` —
one match.

### Step 2: `RingByteBuffer`

Create `audio/RingByteBuffer.kt`:

- Constructor: `RingByteBuffer(capacityBytes: Int)`.
- Methods: `write(bytes: ByteArray, offset: Int, length: Int)`,
  `snapshot(): ByteArray` (copy of everything currently held, oldest
  first), `size(): Int`, `clear()`.
- Thread-safe via `synchronized` on an internal lock (the audio thread
  writes, the stop-handler reads).
- On overflow: overwrite oldest bytes, increment a `dropped` counter
  accessible via `droppedBytes(): Long`.

Also create the test file `audio/RingByteBufferTest.kt`:

- writes less than capacity → snapshot returns exactly what was written
- writes equal to capacity → snapshot returns the full buffer, no drops
- writes 2× capacity → snapshot returns last capacity bytes; `droppedBytes()` returns capacity
- concurrent write/read stress test (short sanity — 100 ms)

**Verify**: `./gradlew --no-daemon :ime:testDebugUnitTest --tests '*RingByteBufferTest'` — all pass.

### Step 3: `AudioRecorderEngine`

Create `audio/AudioRecorderEngine.kt`:

- Injected `RecorderCoroutineDispatcher` (create it as a wrapper around
  `Executors.newSingleThreadExecutor().asCoroutineDispatcher()` — Hilt
  provider if the project uses Hilt).
- `start(): Flow<ShortArray>` — cold flow that
  (a) constructs `AudioRecord` with `SAMPLE_RATE=16000`,
      `channelConfig=CHANNEL_IN_MONO`,
      `audioFormat=ENCODING_PCM_16BIT`,
      `audioSource=VOICE_RECOGNITION`,
      `bufferSizeInBytes = max(AudioRecord.getMinBufferSize(...), 3200)` (200 ms).
  (b) calls `startRecording()`,
  (c) loops on `read()` into a `ShortArray` frame of 1600 shorts (100 ms),
  (d) `emit`s each frame,
  (e) on cancellation, `stop()` + `release()`.
- Errors → the flow throws `AudioRecorderException` with cause; do not
  swallow.
- `checkPermission(): Boolean` static — reads
  `ContextCompat.checkSelfPermission(...) == PERMISSION_GRANTED`.

Also create `audio/AudioSession.kt`:

- Data class holding: `startedAtElapsedMs: Long`, `stoppedAtElapsedMs:
  Long?`, and a `RingByteBuffer` reference.
- `durationMs()`: from timestamps.

**Verify**: `./gradlew --no-daemon :ime:compileDebugKotlin` — success.
No unit test yet (real `AudioRecord` requires a device); an
instrumented test is Plan 010 territory.

### Step 4: `Transcriber` interface + `StubTranscriber`

Create `audio/Transcriber.kt`:

```kotlin
interface Transcriber {
    /** Called on ACTION_DOWN or first tap. */
    fun onSessionStart(session: AudioSession)
    /** Called every ~100 ms with new PCM frames while recording. */
    fun onAudioFrame(session: AudioSession, frame: ShortArray)
    /** Called on stop. Producers commit final text here. */
    suspend fun onSessionStop(session: AudioSession)
    /** Called on gesture cancel. */
    fun onSessionCancel(session: AudioSession)
}
```

Create `audio/StubTranscriber.kt`:

```kotlin
class StubTranscriber : Transcriber {
    override fun onSessionStart(session: AudioSession) {
        Log.d("AudioRecorder", "stub: session start")
    }
    override fun onAudioFrame(session: AudioSession, frame: ShortArray) {}
    override suspend fun onSessionStop(session: AudioSession) {
        val ms = session.durationMs()
        Log.d("AudioRecorder", "stub: would transcribe ${ms} ms of audio")
    }
    override fun onSessionCancel(session: AudioSession) {
        Log.d("AudioRecorder", "stub: session cancel")
    }
}
```

Bind `Transcriber` to `StubTranscriber` in whichever DI setup Dictus
uses. If Hilt: add a `@Module` provider in
`ime/src/main/java/com/gallopkeyboard/ime/di/AudioModule.kt`. If not:
instantiate it in the IME service and pass it down.

### Step 5: `PermissionRequester` (IME-scoped)

Requesting a runtime permission from inside an `InputMethodService` is
awkward — IMEs are not Activities. The reliable approach:

- Launch a translucent, no-history `Activity`
  (`PermissionProxyActivity`) that calls
  `ActivityCompat.requestPermissions(...)` and finishes immediately
  after the user answers, broadcasting the result back to the IME.

Create `panel/PermissionProxyActivity.kt` (register in
`ime/src/main/AndroidManifest.xml` with `theme="@android:style/Theme.Translucent.NoTitleBar"`,
`excludeFromRecents="true"`, `noHistory="true"`).

Create `panel/PermissionRequester.kt`:

- `fun request(context: Context): Deferred<Boolean>` — starts the
  proxy activity, awaits the broadcast, returns granted/denied.
- Timeout after 30 s → returns `false`.

When the smart button is first pressed and the permission is missing,
call `PermissionRequester.request(context)` before any recording. If
denied, show a Toast via `context.showToast(R.string.mic_permission_denied)`
and return the panel to the placeholder state.

**Verify** on device:
- Fresh install (`adb uninstall com.gallopkeyboard.ime` first).
- Open Notes, switch to voice panel, tap the smart button — Android
  shows the mic permission dialog.
- Deny → toast appears, no recording happens.
- Repeat, allow → recording proceeds (Step 6+).

### Step 6: `SmartVoiceButton` — the gesture FSM

Create `panel/SmartVoiceButton.kt` as a Compose composable that
encapsulates the gesture state machine defined in ADR-0003. States:

```
IDLE
  ├─ ACTION_DOWN → RECORDING (start timer)
RECORDING (from press)
  ├─ ACTION_UP  && elapsed < 400 ms → TAP_TOGGLE_ON (recording continues)
  ├─ elapsed >= 400 ms (still down)  → HOLDING
  ├─ ACTION_CANCEL or drag > 48 dp   → CANCELLED
TAP_TOGGLE_ON
  ├─ ACTION_DOWN → STOPPING_TAP (transition to IDLE after stop completes)
HOLDING
  ├─ ACTION_UP  → STOPPING_HOLD
  ├─ ACTION_CANCEL or drag > 48 dp → CANCELLED
CANCELLED / STOPPING_* → IDLE
```

Implementation guidance:

- Use `Modifier.pointerInput(Unit) { awaitPointerEventScope { ... } }`
  — do NOT use `Modifier.clickable` (loses down/up).
- The FSM lives in a `GestureFsm` class (pure Kotlin, no Compose) so
  Step 7 can unit-test it without a UI. The composable feeds it
  events (`Event.Down(atMs, position)`, `Event.Up(atMs, position)`,
  `Event.Move(position)`, `Event.Cancel`, `Event.HoldThresholdElapsed`)
  and reads back state changes as callbacks.
- The composable schedules a 400 ms `LaunchedEffect` on down that
  posts `HoldThresholdElapsed` if still pressed.
- Visual states:
  - IDLE: rounded 64.dp filled button, label "Hold / Tap to speak"
  - RECORDING (hold or tap-toggle-on): color shifts to `error` /
    `recording red` (Dictus theme token, not hardcoded), label
    "Recording…", small animated waveform dot.
- Emit callbacks: `onSessionStart(reason: StartReason)`,
  `onSessionStop(reason: StopReason)`, `onSessionCancel()`. These map
  1:1 to `Transcriber` methods.
- The composable holds a `AudioRecorderEngine` reference (passed in)
  and coroutine-collects the PCM flow on start; writes each frame to
  the session's `RingByteBuffer`; forwards frame to `Transcriber.onAudioFrame`.

Constants file (or top-of-file constants):

```kotlin
private const val HOLD_THRESHOLD_MS = 400L
private const val CANCEL_SLOP_DP = 48
```

**Verify** on device:
- Voice panel shown, tap the button briefly (<400 ms) → button turns
  red and stays red (tap-toggle-on).
- Tap again → button returns to idle; logcat shows
  `AudioRecorder: stub: would transcribe <n> ms of audio` where n
  approximates the elapsed real-world seconds × 1000.
- Press-and-hold ≥ 500 ms then release → same log.
- Press, drag finger off the button by more than 48 dp → button
  returns to idle; logcat shows `stub: session cancel`; NO
  transcription log.
- Press for 3 min, release → transcription log shows ~180000 ms; no
  ANR, no crash.

### Step 7: Unit-test `GestureFsm`

Create `panel/GestureFsmTest.kt`. Cases (one per row):

| Sequence | Expected callback |
|----------|-------------------|
| Down(t=0), Up(t=100)     | onStart(TAP), then next Down triggers onStop(TAP)  |
| Down(t=0), Up(t=100), Down(t=500), Up(t=550) | onStart, onStop(TAP) |
| Down(t=0), HoldElapsed(t=400), Up(t=1000)     | onStart(HOLD), onStop(HOLD) |
| Down(t=0), Cancel(t=50)  | onStart, onCancel (no stop)                        |
| Down(t=0), Move(dist=100dp) | onStart, onCancel                               |
| Down(t=0), Move(dist=40dp), Up(t=100) | onStart, onStop(TAP) (within slop)        |

Use a fake clock (a `TestTimeSource`) so the tests are deterministic.

**Verify**: `./gradlew --no-daemon :ime:testDebugUnitTest --tests '*GestureFsmTest'` — all 6 pass.

### Step 8: Update inventory + plans README

`docs/dictus-inventory.md` — append `## Plan 005 additions` listing:
new files (audio/*, panel/SmartVoiceButton.kt, panel/Permission*),
the manifest permission, and the `Transcriber` interface as the seam
Plan 006 will implement.

`plans/README.md` — Plan 005 status → `DONE`.

## Test plan

Unit: `GestureFsmTest` (6 cases) + `RingByteBufferTest` (~4 cases).

Instrumented / manual on device (S22 or emulator with mic):

- Cold start, first press → permission dialog → grant → proceed to
  record.
- 3-min continuous recording → no auto-stop, no ANR, `stub: would
  transcribe` reports approximately correct duration.
- 5-min continuous recording → ring buffer drop kicks in, warning
  logged, no crash.
- Rotate device mid-recording → do not crash (may reset state; that's
  acceptable for v1).
- Switch panels while recording → recording stops with `onCancel` (the
  panel change is a cancel). If this is annoying UX, log as a Plan 009
  follow-up; do not fix here.
- Denying the permission twice → banner explains how to enable in
  Settings; use `Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)`.

## Done criteria

- [ ] `bash scripts/verify.sh` exits 0.
- [ ] `:ime` unit tests include ≥10 new tests (gesture + ring), all pass.
- [ ] Manifest declares `RECORD_AUDIO` and only that new permission.
- [ ] `Transcriber` interface exists; `StubTranscriber` is the bound
      implementation.
- [ ] Manual 3-minute recording test passes on device.
- [ ] No `WAKE_LOCK`, `FOREGROUND_SERVICE`, or `INTERNET` permission
      added.
- [ ] `docs/dictus-inventory.md` "Plan 005 additions" present.
- [ ] `plans/README.md` row for Plan 005 shows `DONE`.

## STOP conditions

- `AudioRecord.getMinBufferSize` returns `ERROR` on the target device —
  the mic subsystem is not initialized; report the OEM+model.
- The permission proxy activity approach doesn't return control back to
  the IME on your Android version — check whether Android 14+ IME
  permission APIs are available (e.g. `InputMethodService.requestHideSelf`
  + activity result contract) and switch strategies. Update ADR (new
  ADR-0005) before proceeding.
- Recording for 3 min triggers an Android system ANR from the IME —
  the audio work must be off the main thread. Confirm the
  `AudioRecorderEngine` flow is collected on `Dispatchers.Default` or
  `RecorderCoroutineDispatcher`, not the main dispatcher.
- Adding `androidx.compose.foundation.gestures.pointerInput` requires a
  Compose bump — do NOT bump; use `MotionEvent` interop via
  `AndroidView` instead.
- The 5-minute ring buffer causes an out-of-memory on some device —
  reduce the ceiling to 3 min and log the change; do not increase.
- Dictus already has an `AudioRecord`-based recorder (find it via
  `grep AudioRecord`) — reuse it if the interface matches; do not add
  a parallel one. Note the reuse in the inventory.

## Maintenance notes

- The `Transcriber` interface is the ONLY seam between audio and ASR.
  Plans 006 and 007 both implement it (composing implementations —
  Plan 007 wraps Plan 006). Do not add ASR-specific concerns
  (models, language, decoding) to the `AudioRecorderEngine` or
  `SmartVoiceButton`.
- If v1 telemetry ever becomes needed (it shouldn't per HANDOFF), the
  session start/stop timestamps in `AudioSession` are the natural
  hook — but adding telemetry requires an ADR update and a doc note
  in `AGENTS.md` (currently forbids it).
- `HOLD_THRESHOLD_MS` and `CANCEL_SLOP_DP` are the two knobs. If either
  changes, update ADR-0003 *and* `GestureFsmTest` expectations in the
  same PR.
- The permission proxy activity is a common workaround pattern; if
  Google publishes a first-party IME permission API before Plan 010
  hardens the app, switch to it and delete the proxy — track as
  follow-up.
