# Plan 010: Hardening — battery, crashes, ANR guards, release APK

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving
> to the next step. If anything in the "STOP conditions" section
> occurs, stop and report — do not improvise. When done, update the
> status row for this plan in `plans/README.md` — unless a reviewer
> dispatched you and told you they maintain the index.
>
> **Drift check (run first)**:
> ```
> bash scripts/verify.sh
> grep -q "Plan 009 additions" docs/dictus-inventory.md && echo OK
> ```

## Status

- **Priority**: P1 (blocks personal daily-use adoption)
- **Effort**: L
- **Risk**: MED
- **Depends on**: `plans/009-keyboard-polish-clipboard-emoji.md`
- **Category**: perf, tests, bug, dx
- **Planned at**: commit `99ca844`, 2026-07-16
- **Issue**: —

## Why this matters

`HANDOFF.md` deal-breakers, ranked in owner's own words: "battery,
accuracy, crashes > latency." Plans 004–009 built features; this plan
proves the app is worth using every day. It's the last plan in the
sequence and covers four hardening tracks:

1. **Battery**: profile a 30-min mixed session, remove wake sources
   we don't need, ensure ASR runs only during active recording.
2. **Crashes / ANRs**: install a lightweight in-process crash handler
   that writes to `filesDir/crashes/`, add StrictMode in debug,
   run through the top 5 apps under a manual crash matrix.
3. **Release build**: signing config template, R8/ProGuard rules that
   don't break sherpa-onnx or whisper.cpp JNI, size baseline.
4. **Model lifecycle**: unload models when the voice panel is
   inactive for > 60 s (settings-toggleable).

Nothing here adds new features. Everything makes v1 sideloadable to a
person who expects it to work.

## Current state

After Plans 001–009 all `DONE`:

- Full pipeline works end-to-end on S22.
- Debug APK signs with the default debug key.
- No release signing, no ProGuard, no R8 rules committed.
- No structured crash logging.
- Models stay loaded in memory indefinitely.
- StrictMode not enabled.

## Commands you will need

| Purpose            | Command                                                                | Expected |
|--------------------|------------------------------------------------------------------------|----------|
| Debug build        | `./gradlew --no-daemon assembleDebug`                                  | success  |
| Release build      | `./gradlew --no-daemon assembleRelease` (after Step 5)                 | success  |
| APK size baseline  | `du -h app/build/outputs/apk/{debug,release}/*.apk`                    | records  |
| Battery snapshot   | `adb shell dumpsys batterystats --reset`, then use, then `--charged`   | usage    |
| Wake locks         | `adb shell dumpsys power | grep -i "wake\|lock"`                       | none from us |
| Verify             | `bash scripts/verify.sh`                                               | OK       |

## Scope

**In scope**:
- `core/src/main/java/com/gallopkeyboard/core/log/CrashHandler.kt` (new).
- IME service — install `CrashHandler` in `onCreate`.
- IME service — install StrictMode in debug builds only.
- `ime/src/main/java/com/gallopkeyboard/ime/asr/ModelLifecycleManager.kt` (new)
  — unload `ParakeetEngine` + `PolishEngine` after 60 s idle in voice
  panel; reload on next session start.
- `ime/src/main/res/xml/settings_prefs.xml` — new setting
  `models_keep_loaded` (default false).
- `app/src/main/java/com/gallopkeyboard/app/settings/CrashLogsScreen.kt` (new)
  — reads `filesDir/crashes/` and lets user copy/share text.
- `app/build.gradle.kts` — add `buildTypes { release { minifyEnabled true; shrinkResources true; ... } }` and a **placeholder** `signingConfig` that reads from `~/.gallopkeyboard/keystore.properties`
  (owner sets it up locally; never commit real keys).
- `app/proguard-rules.pro` — new; keep rules for sherpa-onnx and
  whisper.cpp JNI entry points.
- `.gitignore` — ensure keystore paths + `crashes/` are ignored (may
  already be from Plan 001).
- `docs/release-signing.md` (new).
- `docs/manual-test-matrix.md` (new).
- `docs/dictus-inventory.md` — "Plan 010 additions".
- `scripts/verify.sh` — add a "no Log.d in release paths without a
  guard" grep, and a "no `System.out.println` anywhere" grep.
- `plans/README.md` status row.

**Out of scope**:
- Play Store distribution — v1 is sideload only per `CONTEXT.md`.
- Automated device tests in CI (would need Firebase Test Lab or a
  self-hosted runner; personal project).
- Third-party crash reporting (Sentry, Crashlytics) — CONTEXT.md
  forbids telemetry.
- Any new feature (voice, keyboard, models).
- Locale / accessibility audit — a follow-up plan if the owner asks.

## Git workflow

- Branch: `advisor/010-hardening` off Plan 009.
- Commits:
  1. `feat(core): CrashHandler writes to filesDir/crashes`
  2. `feat(ime): install crash handler + StrictMode (debug)`
  3. `feat(ime): ModelLifecycleManager unloads idle engines`
  4. `feat(app): CrashLogsScreen in Settings`
  5. `build(app): release signing config + R8/ProGuard rules`
  6. `docs: release signing + manual test matrix`
  7. `chore: verify.sh guards for logging discipline`

## Steps

### Step 1: `CrashHandler`

- Wraps `Thread.setDefaultUncaughtExceptionHandler`, chaining to any
  existing handler.
- On uncaught: write `filesDir/crashes/<isoTs>-<thread>.txt` with
  timestamp, thread name, `Throwable.stackTraceToString()`, and the
  last 200 lines of `Log.getRadioLog()` if available (skip if not
  reachable). No PII beyond what a Kotlin stack trace already leaks.
- Keep only the last 20 crash files; delete oldest on write.
- Registered in `GallopKeyboardImeService.onCreate` and in
  `GallopKeyboardApplication.onCreate` if an Application class exists;
  if not, add a minimal one.

### Step 2: StrictMode in debug

Add to `GallopKeyboardApplication.onCreate`:

```kotlin
if (BuildConfig.DEBUG) {
    StrictMode.setThreadPolicy(
        StrictMode.ThreadPolicy.Builder()
            .detectDiskReads()
            .detectDiskWrites()
            .detectNetwork()
            .penaltyLog()
            .build())
    StrictMode.setVmPolicy(
        StrictMode.VmPolicy.Builder()
            .detectLeakedClosableObjects()
            .detectActivityLeaks()
            .penaltyLog()
            .build())
}
```

Fix any StrictMode violations that pre-existing code (or Plans 004–
009) introduced. If a fix requires a bigger refactor, note it in a
follow-up plan doc, don't paper over with `.permitAll()`.

### Step 3: `ModelLifecycleManager`

- Registered in DI, singleton, holds soft references to
  `StreamingAsrEngine` and `AsrPolishEngine`.
- On every `Transcriber.onSessionStop` (or explicit `voicePanelHidden()`
  event from `PanelController`), start a 60 s timer.
- On timer expiry, if no new session has started AND
  `models_keep_loaded` setting is false, call `engine.close()` on
  both engines and drop the references. Next session will re-init.
- Setting: `models_keep_loaded` (BooleanPreference, default `false`).
  When true, skip the unload timer entirely — for users with 12+ GB
  RAM who prefer speed.
- Log every load/unload cycle in debug builds.

### Step 4: `CrashLogsScreen`

- Adds a "Crash logs" entry in the launcher app's settings.
- Reads `filesDir/crashes/*.txt`, lists them by timestamp.
- Detail: shows text, "Copy to clipboard", "Share" (via `ACTION_SEND`
  text intent), and "Delete".
- Empty state: "No crashes recorded. That's the goal."

### Step 5: Release build + signing + R8/ProGuard

`app/build.gradle.kts` additions (structure; adjust to Dictus's
existing DSL):

```kotlin
val keystorePropsFile = File(System.getProperty("user.home"),
    ".gallopkeyboard/keystore.properties")
val keystoreProps = if (keystorePropsFile.exists()) {
    Properties().apply { load(FileInputStream(keystorePropsFile)) }
} else null

android {
    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = File(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")   // fallback so CI still builds
        }
    }
}
```

`app/proguard-rules.pro` (starter):

```
# Keep JNI entry points
-keepclasseswithmembernames class * {
    native <methods>;
}

# sherpa-onnx (adjust namespace after Plan 006 confirms actual bindings)
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keep class com.gallopkeyboard.asr.sherpa.** { *; }

# whisper.cpp binding (Dictus namespace after rename)
-keep class com.gallopkeyboard.whisper.** { *; }

# Compose runtime already handled by AGP default rules
```

`docs/release-signing.md` explains:

- Generate a keystore locally
  (`keytool -genkey -v -keystore ~/.gallopkeyboard/keystore.jks
   -keyalg RSA -keysize 4096 -validity 10000 -alias gallop-release`).
- Populate `~/.gallopkeyboard/keystore.properties`:

```
storeFile=/Users/<you>/.gallopkeyboard/keystore.jks
storePassword=***
keyAlias=gallop-release
keyPassword=***
```

- `./gradlew assembleRelease` produces
  `app/build/outputs/apk/release/app-release.apk`.
- Sideload the same way as debug (`adb install -r`).
- Back up the keystore; losing it means new users have to uninstall+
  reinstall on next release.

### Step 6: Manual test matrix

Create `docs/manual-test-matrix.md`. Table with rows per app and
columns for behaviors:

| App | Type text | Voice panel opens | Records 10s | Polish returns | Cancel gesture | Emoji insert | Clipboard chip |
|-----|-----------|-------------------|-------------|----------------|----------------|--------------|----------------|
| Samsung Notes | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| WhatsApp | ☐ | ... |
| Gmail | ☐ | ... |
| Chrome (google.com search box) | ☐ | ... |
| Google Keep | ☐ | ... |

Owner ticks boxes; a failure per app is a follow-up bug plan.

### Step 7: Battery profiling procedure (documented, not automated)

Add a section to `docs/manual-test-matrix.md`:

- `adb shell dumpsys batterystats --reset`
- Unplug device (do not test with USB power).
- 30-minute mixed session: 5 min typing, 10 min dictating short
  bursts, 5 min continuous dictation, 5 min idle keyboard shown,
  5 min mixed.
- `adb shell dumpsys batterystats --charged com.gallopkeyboard.ime > /tmp/battery.txt`
- Record: total CPU foreground time, mAh consumed, wake locks held.
- Target (from `CONTEXT.md` acceptance #1): "no noticeable battery
  drain during normal use." Numeric threshold: <150 mAh for the
  30-min session under mixed load. Adjust after first measurement if
  we've been too aspirational.

### Step 8: `scripts/verify.sh` logging guards

Extend `scripts/verify.sh` with:

```
echo "==> no System.out.println in production sources"
if grep -rn "System\.out\.println" --include='*.kt' app ime core asr whisper; then
  echo "FAIL"; exit 1
fi

echo "==> no Log.d without BuildConfig.DEBUG guard in release-only paths"
# advisory only — logs at INFO+ are fine; noisy DEBUG in tight loops isn't
if grep -rn "Log\.d(" --include='*.kt' ime/src/main | grep -v "BuildConfig.DEBUG" | grep -E "for|while|onAudioFrame|onPartial" ; then
  echo "WARN: Log.d in hot path without guard"
fi
```

### Step 9: Update inventory + plans README

`docs/dictus-inventory.md` — `## Plan 010 additions`.
`plans/README.md` — Plan 010 status → `DONE`.

## Test plan

Unit: none new (this plan is hardening, not features).

Manual on device:

- Fresh install → 30-min battery run per Step 7 procedure. Record
  results.
- Crash test: force-kill during recording; reopen — no partial
  transcript corruption; crash log written (if the kill produced one).
- Idle test: open voice panel, do nothing for 90 s → logcat shows
  `ModelLifecycleManager` unload. Next tap: reload succeeds, latency
  < 1 s.
- Toggle `models_keep_loaded` on → repeat idle test → NO unload.
- Manual matrix (Step 6) across the top 5 apps.
- Release APK: `assembleRelease` succeeds, install, confirm the app
  behaves identically to debug (no ProGuard regression).

## Done criteria

- [ ] `bash scripts/verify.sh` exits 0 with the new guards.
- [ ] `./gradlew assembleRelease` succeeds (with signing config
      falling back to debug key if `keystore.properties` absent).
- [ ] `CrashHandler` writes to `filesDir/crashes/` on a forced
      exception (test with a hidden dev button or via a
      `throw RuntimeException` in a debug-only path).
- [ ] `ModelLifecycleManager` unloads engines after 60 s idle
      (verified via logcat).
- [ ] `models_keep_loaded` setting toggles the unload behavior.
- [ ] `CrashLogsScreen` lists and shares any crash file.
- [ ] `docs/release-signing.md`, `docs/manual-test-matrix.md` exist.
- [ ] Battery baseline measured and recorded in
      `docs/manual-test-matrix.md`.
- [ ] `docs/dictus-inventory.md` "Plan 010 additions" present.
- [ ] `plans/README.md` row for Plan 010 shows `DONE`.

## STOP conditions

- R8 minification breaks JNI resolution — the keep rules in Step 5
  are a starting point; if release APK crashes on native init, add
  package-specific `-keep` rules and iterate. Do not disable R8 to
  paper over.
- Release APK exceeds 30 MB (bare, without models) — R8/shrinkResources
  aren't doing their job or the sherpa-onnx `.so` shipped debug
  symbols. `strip --strip-unneeded` the JNI libs in build, and re-
  verify.
- StrictMode floods logcat with disk-read warnings from Dictus code
  we didn't write — these are pre-existing tech debt; either fix
  them in this plan (small scope) or add a `.permitDiskReads()` with
  a `TODO(strictmode)` comment per site and file a follow-up plan.
- Model-unload timing (60 s) causes visible delays when users
  frequently toggle voice — tune down to 120 s or make it a settings
  slider; do NOT remove the unload entirely (battery target depends
  on it).
- Battery test shows > 300 mAh for 30 min — the app has a leak. Do
  NOT ship v1 without investigating; report the top consumers from
  `dumpsys batterystats`.
- Owner does not yet have a release keystore — the fallback to the
  debug signing config keeps CI green; a real keystore is a
  one-time manual setup described in `docs/release-signing.md`.

## Maintenance notes

- The debug keystore is fine for personal sideload forever. Only if
  we ever distribute to more devices (or hit Play Store) does the
  release keystore become important. When that day comes, back it
  up to *at least* two locations.
- ProGuard rules are additive; every future plan that adds a native
  library or reflection must extend `proguard-rules.pro`. Add a
  `# added by plan-NNN` comment above each block.
- `ModelLifecycleManager` unload timing is a UX/battery trade knob.
  Record any change to the default in `docs/dictus-inventory.md` so
  future battery measurements have context.
- Crash logs are local-only (no upload) per `CONTEXT.md`. If the
  owner wants a "send crash to me" workflow, use `ACTION_SEND` from
  the CrashLogsScreen — do not introduce a network uploader without
  a new ADR.
- StrictMode is debug-only. Do not enable in release, ever — it
  measurably slows down real usage.
