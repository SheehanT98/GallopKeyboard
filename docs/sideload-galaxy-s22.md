# Sideload GallopKeyboard on Galaxy S22

GallopKeyboard ships as a debug APK installed via `adb` — there is no Play Store build in v1.

## Prereqs on your Mac/Linux

- `adb` on `$PATH`. On macOS: `brew install --cask android-platform-tools`.
- A USB cable that supports data transfer (not charge-only).

## Prereqs on the S22 (one-time)

1. **Settings → About phone → Software information** — tap **Build number** seven times to unlock Developer options.
2. **Settings → Developer options** — enable **USB debugging**.
3. Plug in the phone. When prompted, tap **Allow** on the USB debugging authorization dialog.

## Install the debug APK

From the repo root (with JDK 17 and Android SDK configured — see [`android-toolchain.md`](android-toolchain.md)):

```bash
source scripts/android-env.sh
./gradlew assembleDebug
adb devices          # should list your S22
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Expect `Success` from `adb`.

## Enable GallopKeyboard as system keyboard

1. **Settings → General management → Keyboard list and default → Manage keyboards** — toggle **GallopKeyboard** on and confirm the warning.
2. **Settings → General management → Keyboard list and default → Default keyboard** — select **GallopKeyboard**.

## Grant microphone permission

Open any text field (Notes, Messages, etc.). Switch to the voice panel and use
the mic button. Android will prompt for **RECORD_AUDIO** — allow it.

## Download voice models (required for dictation)

Voice models are **not** inside the APK (~220 MB total).

1. Open the **GallopKeyboard** app from the launcher.
2. Tap **Download** (use Wi‑Fi if you can).
3. Wait until it says voice is ready.

If you open the voice panel before downloading, you’ll see a **Voice models
needed** card with a **Download voice models** button that opens the same flow.

See [`models.md`](models.md) for details and optional adb sideload.

## Reinstall for a new build

`adb install -r app/build/outputs/apk/debug/app-debug.apk` re-installs while preserving keyboard selection in most cases.

If Android refuses a downgrade:

```bash
adb uninstall com.gallopkeyboard.ime
adb install app/build/outputs/apk/debug/app-debug.apk
```

You will need to re-enable GallopKeyboard as the default keyboard after an uninstall.

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| APK installs but keyboard doesn't appear in the list | Reboot the device — the IME cache is aggressive. |
| `adb devices` shows `unauthorized` | Unplug, replug, and accept the USB debugging prompt on the phone. |
| Keyboard selection resets after install | Normal after an `applicationId` change (won't recur unless ADR-0004 is updated). |

## What NOT to do

- Do **not** enable "Install unknown apps" for a browser and download APKs from the web — always install via `adb` from your own build.
- Do **not** sign the debug APK with a release key. Release signing is handled in Plan 010.
