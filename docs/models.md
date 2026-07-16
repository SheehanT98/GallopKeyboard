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

Directory on device (app-private storage):

```
context.getFilesDir()/models/whisper/
  base.en.gguf          # default (~140 MB)
  small.en.gguf         # optional higher accuracy (~470 MB)
```

Legacy Dictus flat filenames (`ggml-base.bin`, etc.) under the same `whisper/` subdirectory are also accepted if sideloaded for development.

For the IME process (`com.gallopkeyboard.ime`):

```
/data/data/com.gallopkeyboard.ime/files/models/whisper/
```

On a user-visible path via `adb`:

```
/sdcard/Android/data/com.gallopkeyboard.ime/files/models/whisper/
```

### Sizes

| File | Approx. size |
|------|----------------|
| `base.en.gguf` | ~140 MB |
| `small.en.gguf` | ~470 MB |

### Obtaining models

Download GGML/GGUF English models from the HuggingFace [ggerganov/whisper.cpp](https://huggingface.co/ggerganov/whisper.cpp) release files. Filename convention: `<name>.gguf` (whisper.cpp ggml format). Do **not** commit model binaries to git.

### Sideload for local dev (before Plan 008)

```bash
adb shell mkdir -p /sdcard/Android/data/com.gallopkeyboard.ime/files/models/whisper/
adb push base.en.gguf /sdcard/Android/data/com.gallopkeyboard.ime/files/models/whisper/
```

Polish runs as a single non-streaming pass over the full session buffer on stop, with a 2 s timeout (see ADR-0003). If polish times out, the streaming partial from Plan 006 remains committed.

