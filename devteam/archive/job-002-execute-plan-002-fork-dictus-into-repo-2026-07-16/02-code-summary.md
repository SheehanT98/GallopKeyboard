# Job 002 — Code summary

## What landed

Forked upstream Dictus Android (`develop` @ `4f5d24821d0772be16f482455c00da77d9a5f594`) into GallopKeyboard with full package rename, Gradle rebranding, MIT attribution, and a verified debug build.

## Modules imported

| Module | Package root | Purpose |
|--------|--------------|---------|
| `:app` | `com.gallopkeyboard` | Launcher, onboarding, settings, `DictationService` |
| `:ime` | `com.gallopkeyboard.ime` | `DictusImeService`, Compose keyboard |
| `:core` | `com.gallopkeyboard.core` | Theme, preferences, dictation contracts |
| `:asr` | `com.gallopkeyboard.asr` | sherpa-onnx / `ParakeetProvider` |
| `:whisper` | `com.gallopkeyboard.whisper` | whisper.cpp JNI |

## Key renames (ADR-0004)

- Upstream package `dev.pivisolutions.dictus` → `com.gallopkeyboard`
- `applicationId`: `com.gallopkeyboard.ime`
- `rootProject.name`: `gallopkeyboard-android`
- App/IME label: `GallopKeyboard`
- Removed `values-fr/` locale dirs (English-only)

## Submodule

- `third_party/whisper.cpp` (ggml-org/whisper.cpp) — git submodule at import commit

## New / updated docs

| File | Why |
|------|-----|
| `docs/dictus-inventory.md` | Factual map of IME entry, STT engines, model paths (sherpa-onnx **present**) |
| `NOTICE` | Dictus + whisper.cpp attribution |
| `README.md` | Build + Galaxy S22 sideload instructions |
| `AGENTS.md` | Verified `./gradlew` commands |
| `plans/README.md` | Plan 002 → DONE |

## Build verification

```bash
source scripts/android-env.sh
./gradlew --no-daemon clean assembleDebug testAll
# BUILD SUCCESSFUL
# app/build/outputs/apk/debug/app-debug.apk (~156 MB)
```

## Notable findings

- Upstream uses `dev.pivisolutions.dictus`, not `com.dictus` — rename applied to that single consistent root; `com.k2fsa.sherpa.onnx` left untouched.
- Sherpa-ONNX / Parakeet offline ASR is already integrated (contrary to plan pre-check); streaming wiring remains Plan 006.
- Robolectric needed `application=com.gallopkeyboard.DictusApplication` in `app/src/test/resources/robolectric.properties` because Gradle `namespace` is `com.gallopkeyboard.app` while Kotlin package stays `com.gallopkeyboard`.

## STOP conditions

None hit.
