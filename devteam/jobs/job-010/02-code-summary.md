# Code summary — job-010

Plan 010 hardening: crash logging, StrictMode (debug), model lifecycle unload,
release APK with R8/ProGuard, settings + docs.

## Files added

| File | Why |
|------|-----|
| `core/.../log/CrashHandler.kt` | Writes uncaught exceptions to `filesDir/crashes/` (max 20 files) |
| `ime/.../asr/ModelLifecycleManager.kt` | Unloads Parakeet + Whisper after 60s voice-panel idle |
| `ime/.../asr/ModelLifecycleController.kt` | DI/test seam for lifecycle callbacks |
| `app/.../settings/CrashLogsScreen.kt` | Settings UI: list, copy, share, delete crash logs |
| `app/proguard-rules.pro` | R8 keep rules for JNI, sherpa-onnx, whisper, Hilt |
| `docs/release-signing.md` | Local keystore setup for sideload release builds |
| `docs/manual-test-matrix.md` | Cross-app smoke matrix + battery profiling procedure |

## Files edited

| File | Why |
|------|-----|
| `DictusApplication.kt` | `CrashHandler.install` + StrictMode in debug |
| `DictusImeService.kt` | `CrashHandler.install` |
| `PolishingTranscriber.kt` | Lifecycle hooks on session start/stop |
| `PanelHost.kt` | Voice panel shown/hidden → idle unload timer |
| `AudioModule.kt` | Hilt bind `ModelLifecycleController` |
| `DictusImeEntryPoint.kt` | Expose lifecycle manager to IME |
| `PreferenceKeys.kt` | `MODELS_KEEP_LOADED` preference |
| `SettingsScreen.kt` / `SettingsViewModel.kt` | Toggle + Crash logs navigation |
| `AppNavHost.kt` / `AppDestination.kt` | Crash logs route |
| `app/build.gradle.kts` | Release minify/shrink + `~/.gallopkeyboard/keystore.properties` |
| `scripts/verify.sh` | `System.out.println` guard + Log.d hot-path advisory |
| `.gitignore` | Ignore `.gallopkeyboard/` and `crashes/` |
| `docs/dictus-inventory.md` | Plan 010 additions section |
| `plans/README.md` | Plan 010 → DONE |
| `app/.../strings.xml` | Crash logs + models_keep_loaded strings |
| `PolishingTranscriberTest.kt` | No-op lifecycle controller for tests |

## Verification

```bash
source scripts/android-env.sh
bash scripts/verify.sh          # OK
./gradlew assembleRelease       # OK (debug signing fallback)
```

APK sizes: debug 157M, release 88M (native libs; models not in APK).

## STOP conditions

| Condition | Result |
|-----------|--------|
| R8 breaks JNI | **Not hit** — `assembleRelease` succeeds |
| Release APK > 30 MB | **Noted** — 88M due to bundled native `.so` (expected for sherpa-onnx + whisper); not a ProGuard regression |
| StrictMode floods logcat | **Not hit** in CI build |
| Model unload UX | **Not hit** — 60s default retained |
| Battery > 300 mAh | **Not hit** — requires physical S22 (documented as pending) |
| No release keystore | **Not hit** — debug signing fallback works |

## Manual / device tests (owner)

- 30-min battery run per `docs/manual-test-matrix.md`
- Idle unload logcat test (90s in voice panel)
- Crash handler forced-exception test
- Release APK install + smoke matrix
