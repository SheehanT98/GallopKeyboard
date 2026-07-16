# Plan 008: First-launch model download UX + storage settings

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
> test -f docs/models.md && echo OK
> ```

## Status

- **Priority**: P2
- **Effort**: M
- **Risk**: MED (network + storage + progress UX)
- **Depends on**: `plans/007-whisper-polish-pass.md`
- **Category**: feature, dx
- **Planned at**: commit `99ca844`, 2026-07-16
- **Issue**: —

## Why this matters

After Plan 007, the app requires ~220 MB (Parakeet ~80 + Whisper base
~140) of model files present at
`context.filesDir/models/{parakeet,whisper}/`. Right now those files
must be `adb push`-ed manually — fine for the owner during dev,
unusable for anyone else who ever sideloads. This plan makes first
launch:

1. Show a one-screen setup: "Download voice models (~220 MB)".
2. Download over the network (Wi-Fi warning if metered).
3. Verify SHA-256 per file.
4. Extract into the models directory.
5. Show progress and a resumable error state.

It also adds a Settings screen entry to re-download, delete, or
switch quality tier (`base.en` ↔ `small.en`).

## Current state

After Plan 007:

- Whisper + Parakeet code paths expect files at
  `context.filesDir/models/{parakeet,whisper}/`.
- Missing files → toast, no crash (see Plans 006/007 error paths).
- Dictus has an existing model manager (per
  `docs/dictus-inventory.md`) — reuse if it fits, replace if it
  doesn't. Log the decision.
- No settings screen for GallopKeyboard-specific config exists yet
  (Dictus's may exist but hasn't been re-linked from the new
  panel/gesture flow).

## Commands you will need

| Purpose            | Command                                                                     | Expected |
|--------------------|-----------------------------------------------------------------------------|----------|
| Build              | `./gradlew --no-daemon assembleDebug`                                        | success  |
| Unit tests         | `./gradlew --no-daemon :core:testDebugUnitTest :app:testDebugUnitTest`      | success  |
| Verify             | `bash scripts/verify.sh`                                                     | OK       |
| Simulate slow net  | `adb shell settings put global http_proxy 127.0.0.1:8888` (undo after)      | side-effect only |
| Logcat             | `adb logcat -s ModelDownloader:*`                                            | live |

## Scope

**In scope**:
- `core/src/main/java/com/gallopkeyboard/core/models/ModelSpec.kt` (new)
  — data class: id, url, sha256, sizeBytes, destPath.
- `core/src/main/java/com/gallopkeyboard/core/models/ModelRegistry.kt`
  (new) — static list of specs for Parakeet (encoder/decoder/joiner/tokens)
  and Whisper base.en; plus opt-in `small.en`.
- `core/src/main/java/com/gallopkeyboard/core/models/ModelDownloader.kt`
  (new) — coroutine downloader with progress Flow, resumable
  (Range headers), SHA-256 verification, atomic move via
  `.part` → final rename.
- `core/src/main/java/com/gallopkeyboard/core/models/ModelInstaller.kt`
  (new) — orchestrates downloading a full bundle (Parakeet OR Whisper
  tier) using `ModelDownloader`.
- `app/src/main/java/com/gallopkeyboard/app/onboarding/OnboardingActivity.kt`
  (new) — hosts the first-launch download UX (Compose).
- `app/src/main/java/com/gallopkeyboard/app/settings/ModelsSettingsScreen.kt`
  (new) — Compose settings screen (accessible from the launcher app
  icon).
- `ime/.../asr/StreamingTranscriber.kt` and `PolishingTranscriber.kt`
  — when `AsrModelMissingException` fires, offer a "Set up voice
  models" action in the toast (deep-link Intent to
  `OnboardingActivity`).
- `core/src/test/java/com/gallopkeyboard/core/models/ModelDownloaderTest.kt`
  (new) — using `MockWebServer` (OkHttp).
- `docs/models.md` — append "How model download works" section with
  the fixed URLs and hashes.
- `docs/dictus-inventory.md` — "Plan 008 additions".
- `plans/README.md` status row.

**Out of scope**:
- Delta downloads / partial model updates (v2).
- Peer-to-peer or Bluetooth model transfer.
- On-launcher-app UI beyond the models screen (branding polish is
  Plan 009 / 010).
- Background auto-updates of models — user-initiated only.
- Analytics or download-success reporting — none in v1.
- Deleting models under Android's `Storage Access Framework` picker
  — we own the app's private storage, no SAF needed.

## Git workflow

- Branch: `advisor/008-model-download` off Plan 007.
- Commits:
  1. `feat(core): ModelSpec + ModelRegistry`
  2. `feat(core): ModelDownloader with SHA-256 + resume`
  3. `feat(core): ModelInstaller orchestration`
  4. `feat(app): OnboardingActivity first-launch download UX`
  5. `feat(app): ModelsSettingsScreen (re-download / delete / tier)`
  6. `feat(ime): deep-link toast action to onboarding`
  7. `test(core): ModelDownloader MockWebServer tests`
  8. `docs: Plan 008 additions`

## Steps

### Step 1: `ModelSpec` and `ModelRegistry`

```kotlin
data class ModelSpec(
    val id: String,                // e.g. "parakeet-encoder"
    val url: String,               // https URL to a stable, checksummed file
    val sha256: String,            // hex, 64 chars
    val sizeBytes: Long,           // exact
    val relPath: String,           // relative to filesDir
)
```

`ModelRegistry`:

- `parakeetBundle: List<ModelSpec>` — 4 entries (encoder, decoder,
  joiner, tokens).
- `whisperBase: ModelSpec` — 1 entry.
- `whisperSmall: ModelSpec` — 1 entry.
- URLs: use HuggingFace mirrors that publish stable SHA-256s. Do NOT
  invent hashes — fetch the actual hashes from the mirror at plan-
  execution time and paste them. If a mirror's hash isn't publicly
  documented, compute it yourself with `sha256sum` after downloading
  once and pin.
- Encode versions in the URLs so future model updates are a registry
  change, not a code change.

**Verify**:
- `grep -c ModelSpec core/src/main/java/com/gallopkeyboard/core/models/ModelRegistry.kt` — ≥ 6.
- All SHA-256 strings are exactly 64 hex characters.

### Step 2: `ModelDownloader`

Requirements:

- Uses OkHttp (Dictus likely already has it — check via
  `grep -r okhttp3 --include='*.kts'`). If not, add
  `implementation("com.squareup.okhttp3:okhttp:4.12.0")` to
  `core/build.gradle.kts`.
- Downloads to `<dest>.part`, then renames to `<dest>` on
  successful hash match. Atomic; a crashed download leaves the
  `.part` and can resume.
- Resume: `Range: bytes=<current-size>-` header on retry if
  `<dest>.part` exists.
- Progress: `Flow<DownloadProgress>` with `bytesDone`, `bytesTotal`,
  `speedBytesPerSec`, `state (Running | Verifying | Done | Failed)`.
- On SHA-256 mismatch: delete `.part`, throw `HashMismatchException`.
- Cancellation-cooperative: check `coroutineContext.isActive`
  periodically.
- No wifi-only enforcement here (caller decides) — but expose helper
  `isMeteredNetwork(context): Boolean` in the same module so UX can
  warn.

### Step 3: `ModelInstaller`

Higher-level API:

```kotlin
class ModelInstaller(...) {
    fun install(bundle: List<ModelSpec>): Flow<InstallProgress>
    fun isInstalled(bundle: List<ModelSpec>): Boolean
    fun delete(bundle: List<ModelSpec>)
    fun diskUsageBytes(): Long
}
```

- Runs `ModelDownloader` sequentially over the bundle (not parallel —
  users on hotel wifi will thank you).
- `isInstalled`: all files exist AND SHA-256 matches (avoid the
  "half-corrupt file lingers" bug — do a periodic `verify()` on IME
  startup gated to once/day via a `SharedPreferences` timestamp).
- `delete`: removes the bundle files (not the model directory
  itself).

### Step 4: `OnboardingActivity`

Compose scaffold:

- Detected state on `onResume`: are Parakeet + Whisper base installed?
  If yes, finish immediately and route to Settings or just close.
- Otherwise show:
  - Title: "Set up GallopKeyboard voice"
  - Subtitle: "Downloads ~220 MB of on-device voice models. No data
    leaves your phone."
  - Wi-Fi warning if `isMeteredNetwork` is true: "You're on mobile
    data — this will use ~220 MB."
  - Primary button: "Download". Starts `ModelInstaller.install(parakeet + whisperBase)`;
    Compose collects the progress flow and shows a progress bar with
    per-file label.
  - Secondary button (initially disabled): "Skip for now" — dismisses;
    the IME will show a toast on first use.
  - On error state: show the message + "Retry".
  - On success: "Voice ready. Open your keyboard settings to enable
    GallopKeyboard." + button that launches
    `Settings.ACTION_INPUT_METHOD_SETTINGS`.
- Store a `SharedPreferences` flag `voice.setup.completed=true` on
  success so subsequent app-icon taps go straight to Settings.

`AndroidManifest.xml` (app module): mark `OnboardingActivity` as
`android:launchMode="singleTop"`, and make it the launcher intent
target for the first launch. After setup, the launcher intent points
to `ModelsSettingsScreen` (or you can leave the launcher intent on
onboarding and let its `onResume` route away).

### Step 5: `ModelsSettingsScreen`

Compose screen. Sections:

- **Installed models** — list each ModelSpec with size and status
  (Installed / Missing / Corrupt). Corrupt files get an inline "Fix"
  button that redownloads that spec.
- **Quality tier**: radio buttons "Base (fast, ~140 MB)" vs
  "Small (accurate, ~470 MB)". Switching triggers a download +
  delete of the previous tier.
- **Storage usage** — total from `ModelInstaller.diskUsageBytes()`.
- **Delete all models** — dangerous button, requires confirmation
  dialog. On confirm, deletes and returns to Onboarding.

### Step 6: Toast deep-link from IME

Where Plans 006/007 show "voice models missing" toast, add an intent
launch action if the toast API supports it — otherwise show a
persistent notification-style banner *inside the voice panel* (a
`VoicePanelPromptBanner` composable that says "Set up voice models"
and taps into `OnboardingActivity`).

Do NOT auto-launch onboarding from the IME — IMEs cannot start
activities without visible user interaction on Android 12+.

### Step 7: Unit-test `ModelDownloader` with MockWebServer

Add `testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")`
if not already present.

Test file:
`core/src/test/java/com/gallopkeyboard/core/models/ModelDownloaderTest.kt`.

Cases:

- Happy path — 1 MB file, correct SHA → downloads, renames, verifies.
- SHA mismatch — download completes, hash wrong → `.part` deleted,
  exception thrown.
- Interrupted → resume — cancel at 50%, restart, sees `.part`,
  sends `Range` header, completes.
- 404 → clear error, no `.part` left behind.
- Progress flow emits monotonically increasing `bytesDone` and
  terminal `Done` state on success.
- `isMeteredNetwork` returns without exception (may need a fake
  `ConnectivityManager`; skip if too involved).

### Step 8: Update inventory + plans README + models.md

`docs/models.md` — append the exact URLs and SHA-256 hashes used;
this is the single source of truth for the registry and reviewers.

`docs/dictus-inventory.md` — `## Plan 008 additions`.

`plans/README.md` — Plan 008 status → `DONE`.

## Test plan

Unit: `ModelDownloaderTest` (~6 cases).

Manual on device:

- Fresh install → open the launcher icon → onboarding shown → tap
  Download on Wi-Fi → progress bar advances → "Voice ready" shown.
- Fresh install on mobile data → wifi warning shown → dismissable.
- Kill the app mid-download → reopen → resume works (progress starts
  where it stopped).
- Corrupt a model file (`adb shell dd if=/dev/urandom of=... bs=1 count=100 seek=200`)
  → `ModelsSettingsScreen` shows Corrupt → Fix redownloads.
- Delete all models → IME toast/banner appears on first tap →
  tapping banner opens onboarding.

## Done criteria

- [ ] `bash scripts/verify.sh` exits 0.
- [ ] `ModelDownloaderTest` (6 cases) passes.
- [ ] Fresh install → onboarding flow completes on device.
- [ ] IME banner deep-links to onboarding when models are missing.
- [ ] Settings screen lists all specs with statuses.
- [ ] Resume-download manual test passes.
- [ ] `docs/models.md` contains real SHA-256 values (64 hex chars each).
- [ ] `docs/dictus-inventory.md` "Plan 008 additions" present.
- [ ] `plans/README.md` row for Plan 008 shows `DONE`.

## STOP conditions

- The chosen model mirror (HuggingFace or otherwise) returns HTTP 302
  chains that OkHttp doesn't follow with `Range` — configure OkHttp
  `followRedirects(true)` and re-test. If the redirect target
  changes on every request, pin to the resolved URL.
- SHA-256 values aren't publicly documented for the desired model
  release — download once, compute, pin in the registry with a
  comment linking to the release page and date. Do NOT ship without
  hashes.
- Total bundle exceeds 500 MB after including all four Parakeet
  files + Whisper base — the "~220 MB" copy in onboarding lies.
  Update the copy to the true number; do NOT silently ship a bigger
  download than the UX promised.
- Downloader hangs on Android's background execution limits — the
  activity must remain visible during download (do not background-
  download in v1). If a foreground service becomes necessary later,
  it's a new ADR.
- Dictus already ships a model downloader that fits the shape we
  need — extend it via composition, don't duplicate. Note the reuse
  in inventory.

## Maintenance notes

- Every model URL bump is a `ModelRegistry.kt` edit + a `docs/models.md`
  edit in the same commit. Do not decouple them.
- `SharedPreferences` for `voice.setup.completed` is a UX shortcut,
  not a source of truth. `ModelInstaller.isInstalled()` is the truth
  — the flag only controls where the launcher opens.
- If we ever add support for user-provided model files (advanced
  users), do it as a `docs/BYOM.md` + a new registry loader
  (`FileSpecLoader`); do not remove the built-in registry.
- The `.part` file naming is important: any file scanner that walks
  `filesDir` must ignore `*.part`. If a future plan adds one, add
  the exclusion up front.
