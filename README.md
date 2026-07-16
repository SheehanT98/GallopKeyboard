# GallopKeyboard

Personal Android keyboard IME — a fork of [Dictus](https://github.com/getdictus/dictus-android) extended with a DeepSeek-style voice panel toggle, hybrid offline speech-to-text (streaming Parakeet + Whisper polish on stop), and Gboard-like typing basics. English only, 100% on-device STT, no cloud services.

## Build the debug APK

**Prerequisites:** JDK 17+, Android SDK 34, `ANDROID_HOME` set (see [`docs/android-toolchain.md`](docs/android-toolchain.md)).

```bash
git clone --recurse-submodules https://github.com/sheehant98/gallopkeyboard.git
cd gallopkeyboard
source scripts/android-env.sh   # or: npm run android:setup
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Install on Galaxy S22

See [docs/sideload-galaxy-s22.md](docs/sideload-galaxy-s22.md) for the full walkthrough.

1. Enable USB debugging and connect the device.
2. `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. **Settings → General Management → Keyboard → Manage keyboards** → enable **GallopKeyboard** → set as default.
4. Open the **GallopKeyboard** app and **download voice models** (~220 MB, Wi‑Fi recommended) before using dictation.
5. Grant microphone permission when prompted on first voice use.

## Repo layout

| Path | Purpose |
|------|---------|
| `app/` | Launcher app, onboarding, settings, `DictationService` |
| `ime/` | `InputMethodService` and Compose keyboard UI |
| `core/` | Shared theme, preferences, dictation contracts |
| `asr/` | sherpa-onnx / Parakeet offline ASR |
| `whisper/` | whisper.cpp JNI bridge |
| `third_party/` | Git submodules (whisper.cpp) |
| `plans/` | Sequential executor plans (001–010) |
| `docs/adr/` | Architecture decision records |

## Planning system

Work is organized as numbered plans under [`plans/README.md`](plans/README.md). Read [`AGENTS.md`](AGENTS.md) before executing any plan — it holds build commands, coding conventions, and hard constraints.

## License & attribution

MIT License — see [`LICENSE`](LICENSE). Upstream Dictus attribution and third-party notices are in [`NOTICE`](NOTICE).
