# Dictus import inventory

Factual reference for Plans 004+ — updated when Kotlin modules change.

## Upstream commit

- **SHA:** `4f5d24821d0772be16f482455c00da77d9a5f594`
- **Branch:** `develop`
- **Source:** https://github.com/getdictus/dictus-android
- **Import date:** 2026-07-16

## Modules

Confirmed in `settings.gradle.kts`:

| Gradle module | Path | Role |
|---------------|------|------|
| `:app` | `app/` | Launcher app, onboarding, settings, `DictationService` |
| `:ime` | `ime/` | `InputMethodService`, Compose keyboard UI |
| `:core` | `core/` | Shared theme, preferences, `DictationController` interface |
| `:whisper` | `whisper/` | whisper.cpp JNI bridge (`WhisperLib`, `WhisperContext`) |
| `:asr` | `asr/` | sherpa-onnx JNI + `ParakeetProvider` |

## Whisper integration

| Item | Detail |
|------|--------|
| Module | `:whisper` |
| JNI entry | `whisper/src/main/jni/whisper/jni.c` → loads `libwhisper.so` (CMake via `third_party/whisper.cpp`) |
| Kotlin API | `WhisperLib` (JNI declarations), `WhisperContext` (lifecycle wrapper) |
| App wrapper | `app/.../service/WhisperProvider.kt` implements `SttProvider` |
| Model path | `ModelManager` resolves `context.filesDir/models/{fileName}` for GGML files |
| Submodule | `third_party/whisper.cpp` (ggml-org/whisper.cpp) |

## Sherpa-ONNX / Parakeet

**Present.** Upstream ships offline Parakeet via sherpa-onnx (not streaming in current code).

| Item | Detail |
|------|--------|
| Module | `:asr` |
| JNI libs | `asr/src/main/jniLibs/*/libonnxruntime.so`, `libsherpa-onnx-jni.so` |
| Kotlin bindings | `com.k2fsa.sherpa.onnx.*` (vendored in `asr/src/main/java/com/k2fsa/sherpa/onnx/`) |
| Provider | `asr/.../ParakeetProvider.kt` implements `SttProvider` via `OfflineRecognizer` |
| Models | Downloaded to `filesDir/models/{key}/` (ONNX + `tokens.txt`) per `ModelCatalog` |

Plan 006 (streaming pass) still needs live partial-transcript wiring; the engine dependency is already in-tree.

## IME entry point

| Item | Detail |
|------|--------|
| Class | `com.gallopkeyboard.ime.DictusImeService` |
| File | `ime/src/main/java/com/gallopkeyboard/ime/DictusImeService.kt` |
| Base class | `LifecycleInputMethodService` (Compose lifecycle for IME) |
| Manifest | `ime/src/main/AndroidManifest.xml` — `android:name="com.gallopkeyboard.ime.DictusImeService"`, `BIND_INPUT_METHOD` |
| IME metadata | `ime/src/main/res/xml/method.xml` |

## Model download / storage

| Item | Detail |
|------|--------|
| Downloader | `app/.../service/ModelDownloader.kt` (HuggingFace + sherpa-onnx release URLs) |
| Catalog | `app/.../model/ModelCatalog.kt`, `ModelManager.kt` |
| On-device dir | `context.filesDir/models/` (Whisper flat files; Parakeet per-key subdirs) |

## UI framework

- **Keyboard / IME screens:** Jetpack Compose (`ime/ui/*`, `DictusImeService` hosts Compose via `setContent`)
- **Launcher / settings:** Jetpack Compose (`app/` screens)
- **No XML `KeyboardView`** — layout data is Kotlin (`KeyboardLayouts.kt`, `KeyDefinition.kt`)
