# Code summary — job-008 (Plan 008: Model download UX)

## What landed

First-launch voice model download and storage settings for GallopKeyboard hybrid STT (Parakeet streaming + Whisper polish).

### Core (`:core`)

- **`ModelSpec` / `ModelRegistry`** — Pinned HuggingFace URLs + SHA-256 for streaming zipformer int8 (2023-06-26) and Whisper `base.en` / `small.en` (~219 MB default bundle).
- **`ModelDownloader`** — OkHttp, `.part` files, HTTP Range resume, SHA-256 verify, `isMeteredNetwork()`.
- **`ModelInstaller`** — Sequential bundle install, corrupt/missing detection, daily verify gate, disk usage.
- **`VoiceSetupPrefs` / `VoiceSetupIntents`** — Launcher routing flag, Whisper tier pref, IME deep-link component names.
- **`ModelDownloaderTest`** — 6 MockWebServer cases.

### App (`:app`)

- **`OnboardingActivity`** — Launcher; download UX with metered warning, progress, retry, skip; routes to settings when installed.
- **`ModelsSettingsActivity` + `ModelsSettingsScreen`** — Per-spec status, Fix, tier switch, storage usage, delete-all.
- **`AndroidManifest`** — Launcher on onboarding; `ACCESS_NETWORK_STATE` for metered detection.

### IME (`:ime`)

- **`VoicePanelPromptBanner`** — “Set up voice models” banner in voice panel.
- **`VoiceModelPromptState`** — Shared banner visibility.
- **`StreamingTranscriber`** — Shows banner on `AsrModelMissingException`.
- **`DictusImeService`** — Daily model verify + banner when default bundle missing.

### Docs

- **`docs/models.md`** — “How model download works” with URLs and hashes.
- **`docs/dictus-inventory.md`** — Plan 008 additions (new stack; Dictus `ModelDownloader` not reused).
- **`plans/README.md`** — Plan 008 → DONE.

## Dictus reuse decision

Upstream `app/.../ModelDownloader.kt` targets different paths (`ModelCatalog` tar.bz2 archives, flat Whisper bins) without SHA pinning or Range resume. Built new `:core` stack per plan; legacy Dictus downloader left for dictation flows.

## STOP conditions

None hit. Default bundle ~219 MB (within ~220 MB UX copy). HuggingFace mirrors used with computed SHA-256 pins.

## Verification

```bash
source scripts/android-env.sh
bash scripts/verify.sh   # OK
```
