# On-device ASR model layout

GallopKeyboard keeps speech models **out of the APK**. They are downloaded (Plan 008) or sideloaded for development.

## Streaming Parakeet (Plan 006)

Directory on device (app-private storage):

```
context.getFilesDir()/models/parakeet/
  encoder.onnx
  decoder.onnx
  joiner.onnx
  tokens.txt
```

For the IME process (`com.gallopkeyboard.ime`), the absolute path is typically:

```
/data/data/com.gallopkeyboard.ime/files/models/parakeet/
```

On a user-visible path via `adb`:

```
/sdcard/Android/data/com.gallopkeyboard.ime/files/models/parakeet/
```

### Sizes

English streaming Parakeet transducer (int8) is roughly **~80 MB** total across the four files.

### Obtaining models

Download a **streaming** English transducer release from the sherpa-onnx pretrained models index:

- https://k2-fsa.github.io/sherpa/onnx/pretrained_models/index.html

Look for streaming zipformer / transducer English models with separate `encoder.onnx`, `decoder.onnx`, and `joiner.onnx`. Rename or place files to match the layout above. Do **not** commit `.onnx` binaries to git.

### Sideload for local dev (before Plan 008)

```bash
adb shell mkdir -p /sdcard/Android/data/com.gallopkeyboard.ime/files/models/parakeet/
adb push encoder.onnx /sdcard/Android/data/com.gallopkeyboard.ime/files/models/parakeet/
adb push decoder.onnx /sdcard/Android/data/com.gallopkeyboard.ime/files/models/parakeet/
adb push joiner.onnx  /sdcard/Android/data/com.gallopkeyboard.ime/files/models/parakeet/
adb push tokens.txt   /sdcard/Android/data/com.gallopkeyboard.ime/files/models/parakeet/
```

### Optional instrumented smoke test

```bash
RUN_ASR_SMOKE=1 ./gradlew :asr:connectedDebugAndroidTest
```

Requires models on the device/emulator and a reference WAV under `asr/src/androidTest/assets/`.

## Whisper polish (Plan 007)

Whisper GGML models live as flat files under `context.filesDir/models/` (see upstream `ModelManager`). Plan 007 wires the polish pass; this document only lists Parakeet streaming paths for Plan 006.
