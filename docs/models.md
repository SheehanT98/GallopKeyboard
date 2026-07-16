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

## How model download works (Plan 008)

GallopKeyboard downloads voice models on first launch via `OnboardingActivity`. Files are fetched over HTTPS, verified with SHA-256, and written atomically (`*.part` → rename). Downloads resume with HTTP `Range` when interrupted.

Registry source of truth: `core/.../models/ModelRegistry.kt` (kept in sync with this section).

### Parakeet streaming (zipformer int8, 2023-06-26)

Base URL: `https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26/resolve/main`

| Device path | Source file | Size (bytes) | SHA-256 |
|-------------|-------------|--------------|---------|
| `models/parakeet/encoder.onnx` | `encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx` | 70,108,816 | `5022b2eca5b19d1bc104fcf33e26bc32604b7df553cd2e1f62e31dc7b05e9c87` |
| `models/parakeet/decoder.onnx` | `decoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx` | 540,688 | `780c63ee94c7cfa314211172e5d09b406c0da2beab5c40ea2f54cc95670b76a5` |
| `models/parakeet/joiner.onnx` | `joiner-epoch-99-avg-1-chunk-16-left-128.int8.onnx` | 259,416 | `abd5e30f3f16fc510605c6029dba33f10e4386bd75c5bdc30cf94076864db10d` |
| `models/parakeet/tokens.txt` | `tokens.txt` | 5,048 | `49e3c2646595fd907228b3c6787069658f67b17377c60aeb8619c4551b2316fb` |

Parakeet bundle total: **~71 MB**.

### Whisper polish

Base URL: `https://huggingface.co/ggerganov/whisper.cpp/resolve/main`

| Device path | Source file | Size (bytes) | SHA-256 |
|-------------|-------------|--------------|---------|
| `models/whisper/base.en.gguf` | `ggml-base.en.bin` | 147,964,211 | `a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002` |
| `models/whisper/small.en.gguf` | `ggml-small.en.bin` | 487,614,201 | `c6138d6d58ecc8322097e0f987c32f1be8bb0a18532a3f88f734d1bbf9c41e5d` |

Default first-launch bundle (Parakeet + Whisper base): **~219 MB**.

Settings: `ModelsSettingsActivity` lists each spec, supports re-download / delete, and Whisper tier switch (`base.en` ↔ `small.en`).

