# Manual test matrix (Plan 010)

Owner ticks boxes after testing on the Samsung Galaxy S22 (or target device).
A failure per app should become a follow-up bug plan.

## Cross-app smoke matrix

| App | Type text | Voice panel opens | Records 10s | Polish returns | Cancel gesture | Emoji insert | Clipboard chip |
|-----|-----------|-------------------|-------------|----------------|----------------|--------------|----------------|
| Samsung Notes | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| WhatsApp | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| Gmail | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| Chrome (google.com search box) | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| Google Keep | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |

## Battery profiling procedure

1. `adb shell dumpsys batterystats --reset`
2. Unplug the device (do not test on USB power).
3. Run a **30-minute mixed session**:
   - 5 min typing
   - 10 min dictating short bursts
   - 5 min continuous dictation
   - 5 min idle keyboard shown
   - 5 min mixed
4. `adb shell dumpsys batterystats --charged com.gallopkeyboard.ime > /tmp/battery.txt`
5. Record: total CPU foreground time, mAh consumed, wake locks held.

### Target

From `CONTEXT.md` acceptance #1: no noticeable battery drain during normal use.

Numeric threshold: **< 150 mAh** for the 30-min mixed session. Adjust after the
first real measurement if the target was too aspirational.

### Baseline (agent / CI environment)

| Metric | Value | Notes |
|--------|-------|-------|
| mAh (30 min) | _pending device run_ | Requires physical S22, unplugged |
| CPU fg time | _pending_ | From `batterystats` |
| Wake locks | _pending_ | `adb shell dumpsys power \| grep -i wake` |
| Debug APK size | 157M | `app-debug.apk` (includes debug symbols) |
| Release APK size | 88M | `app-release.apk` after R8 (native `.so` dominate; models not bundled) |

## Model lifecycle idle test

1. Open voice panel, do **not** record for 90 s.
2. Logcat should show `ModelLifecycleManager: unloaded streaming + polish engines`.
3. Tap record again — reload succeeds, latency < 1 s.
4. Enable **Keep models loaded** in Settings → repeat — **no** unload log.

## Crash handler test

1. Debug build: trigger an uncaught exception (or use a dev-only crash path).
2. Confirm `filesDir/crashes/*.txt` is written.
3. Settings → Crash logs — list, copy, share, delete work.

## Autocorrect on space (Plan 026)

Default **OFF**. Enable in Settings → Autocorrect before testing.

| Check | Pass |
|-------|------|
| Pref default OFF — typing `teh ` does **not** replace | ☐ |
| Pref ON — `teh ` → `the ` | ☐ |
| Immediate backspace after correction restores `teh` | ☐ |
| Pref ON — known word `the ` stays `the ` | ☐ |
| Space cursor drag does **not** autocorrect / insert spaces | ☐ |
| Double-tap space still yields `. ` after a correction | ☐ |
| Leave pref OFF for daily use until owner promotes | ☐ |

## Release APK parity

1. `./gradlew assembleRelease`
2. Install release APK; repeat smoke matrix spot-check.
3. Confirm JNI / ASR init succeeds (no ProGuard regression).
