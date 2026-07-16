# Plan 003: CI baseline + Galaxy S22 sideload workflow

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to
> the next step. If anything in the "STOP conditions" section occurs,
> stop and report — do not improvise. When done, update the status row
> for this plan in `plans/README.md` — unless a reviewer dispatched you
> and told you they maintain the index.
>
> **Drift check (run first)**: `./gradlew --no-daemon assembleDebug`
> If this fails, Plan 002 has not landed cleanly — STOP.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: `plans/002-fork-dictus-into-repo.md`
- **Category**: dx, tests
- **Planned at**: commit `99ca844`, 2026-07-16
- **Issue**: —

## Why this matters

Every plan after this one modifies real Android code. Without a
"green build" signal on every push, later cheap executors will merge
plans that broke the build and downstream plans will start from a bad
baseline. This plan wires:

1. A single GitHub Actions workflow that runs `./gradlew assembleDebug`
   + `./gradlew testAll` + `./gradlew lint` on every push and PR.
2. A local `scripts/verify.sh` that runs the same commands, so agents
   can gate themselves before pushing.
3. A short `docs/sideload-galaxy-s22.md` that codifies how the *owner*
   installs a build on their device. This is our only distribution
   channel for v1 (per `CONTEXT.md`, no Play Store).

This is the last DX plan; Plans 004+ start touching Kotlin.

## Current state

After Plan 002:

- `./gradlew assembleDebug` and `./gradlew testAll` both succeed locally.
- No `.github/workflows/` — Plan 002 explicitly excluded upstream's.
- No `scripts/` directory.
- `docs/adr/`, `docs/dictus-inventory.md` exist.
- `README.md` has a "Build the debug APK" section from Plan 002 Step 9 —
  we will link `docs/sideload-galaxy-s22.md` from it (see Step 4).

## Commands you will need

| Purpose            | Command                              | Expected on success |
|--------------------|--------------------------------------|---------------------|
| Local build        | `./gradlew --no-daemon assembleDebug`| BUILD SUCCESSFUL    |
| Local tests        | `./gradlew --no-daemon testAll`      | BUILD SUCCESSFUL    |
| Local lint         | `./gradlew --no-daemon lint`         | BUILD SUCCESSFUL    |
| Verify script      | `bash scripts/verify.sh`             | exit 0              |
| Workflow lint      | `python3 -c 'import yaml; yaml.safe_load(open(".github/workflows/ci.yml"))'` | no error |

## Scope

**In scope**:
- `.github/workflows/ci.yml` (create)
- `scripts/verify.sh` (create, `chmod +x`)
- `docs/sideload-galaxy-s22.md` (create)
- `README.md` — add one line linking sideload doc (surgical edit)
- `AGENTS.md` — replace `TODO(plan-003)` marker with CI-side commands
  (surgical edit)
- `plans/README.md` status row

**Out of scope**:
- Any Kotlin, Java, or resource changes.
- Signing config / release APK — Plan 010 does that.
- CI matrix builds (multiple JDKs, multiple Android SDKs). One row is
  enough; personal project.
- Actions for release publishing, changelog, or Play Store — v1 is
  sideload only.
- Auto-formatter (ktlint, spotless) — we won't run one until a plan
  asks for it explicitly; adding a formatter now would rewrite the
  entire imported Dictus tree.

## Git workflow

- Branch: `advisor/003-ci-baseline` off Plan 002.
- Commit granularity:
  1. `ci: add GitHub Actions workflow for build + test + lint`
  2. `chore: add scripts/verify.sh`
  3. `docs: sideload guide for Galaxy S22`
  4. `docs: link sideload guide from README; update AGENTS.md CI section`

## Steps

### Step 1: Create `.github/workflows/ci.yml`

Write this file exactly (adjust JDK/Android SDK if `docs/dictus-inventory.md`
Step 3 output showed different values, but do **not** downgrade
minSDK/targetSDK):

```yaml
name: CI

on:
  push:
    branches: [ main ]
  pull_request:

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4
        with:
          submodules: recursive

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      - name: Cache Gradle
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: gradle-${{ runner.os }}-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties', 'gradle/libs.versions.toml') }}
          restore-keys: |
            gradle-${{ runner.os }}-

      - name: Assemble debug
        run: ./gradlew --no-daemon assembleDebug

      - name: Unit tests
        run: ./gradlew --no-daemon testAll

      - name: Lint
        run: ./gradlew --no-daemon lint

      - name: Upload debug APK
        if: success()
        uses: actions/upload-artifact@v4
        with:
          name: app-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
          if-no-files-found: warn
          retention-days: 14
```

**Verify**:
- `python3 -c 'import yaml; yaml.safe_load(open(".github/workflows/ci.yml"))'` — no error.
- `grep -c 'gradlew' .github/workflows/ci.yml` — at least 3.

### Step 2: Create `scripts/verify.sh`

The same commands as CI, plus a simple grep guard for common regressions.

```bash
#!/usr/bin/env bash
# Local verification. Mirrors .github/workflows/ci.yml so agents can
# self-check before pushing. Fail fast; verbose on failure.
set -euo pipefail

echo "==> assembleDebug"
./gradlew --no-daemon assembleDebug

echo "==> testAll"
./gradlew --no-daemon testAll

echo "==> lint"
./gradlew --no-daemon lint

echo "==> no leftover Dictus package references outside third_party/"
if grep -rn "com\.dictus" --include='*.kt' --include='*.kts' --include='*.xml' \
     app ime core asr whisper 2>/dev/null | grep -v third_party; then
  echo "FAIL: found com.dictus references outside third_party/"; exit 1
fi

echo "==> no committed model binaries"
if git ls-files | grep -E '\.(gguf|onnx|bin)$' | grep -v third_party; then
  echo "FAIL: model binary committed"; exit 1
fi

echo "OK"
```

Then `chmod +x scripts/verify.sh`.

**Verify**:
- `test -x scripts/verify.sh` — true.
- `bash scripts/verify.sh` — prints `OK`.

### Step 3: Create `docs/sideload-galaxy-s22.md`

Under 100 lines. Sections in order:

- **Prereqs on your Mac/Linux**
  - `adb` on `$PATH` (`brew install --cask android-platform-tools` on macOS).
  - USB cable that supports data (not charge-only).
- **Prereqs on the S22 (one-time)**
  - Settings → About phone → Software information → tap "Build number" 7×
    to unlock Developer options.
  - Settings → Developer options → enable "USB debugging".
  - When plugged in, tap "Allow" on the USB debugging prompt.
- **Install the debug APK**
  - `./gradlew assembleDebug`
  - `adb devices` — should list your S22.
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk`
  - Expect `Success` from adb.
- **Enable GallopKeyboard as system keyboard**
  - Settings → General management → Keyboard list and default →
    Manage keyboards → toggle GallopKeyboard on → confirm the warning.
  - Settings → General management → Keyboard list and default → Default
    keyboard → GallopKeyboard.
- **Grant microphone permission**
  - Open any text input (Notes / Messages), long-press the space bar
    or the mic toggle (depending on which panel is showing) — Android
    will prompt for `RECORD_AUDIO`.
- **Reinstall for a new build**
  - `adb install -r ...` re-installs preserving keyboard selection.
  - If Android refuses a downgrade, `adb uninstall com.gallopkeyboard.ime`
    first (you will need to re-enable the keyboard afterwards).
- **Troubleshooting**
  - APK installs but keyboard doesn't appear: reboot the device — the
    IME cache is aggressive.
  - `adb devices` shows `unauthorized`: replug and accept the prompt.
  - Keyboard selection resets after install: normal after
    `applicationId` change (won't happen again unless ADR-0004 is
    updated).
- **What NOT to do**
  - Do not enable "Install unknown apps" for a browser and download
    APKs from the web — always install via `adb`.
  - Do not sign the debug APK with a release key. Release signing is
    Plan 010.

**Verify**: `test -f docs/sideload-galaxy-s22.md && wc -l docs/sideload-galaxy-s22.md` — 40+ lines.

### Step 4: Link from `README.md` and update `AGENTS.md`

`README.md`: in the "Install on Galaxy S22" section (added by Plan 002),
add a first line: `See [docs/sideload-galaxy-s22.md](docs/sideload-galaxy-s22.md) for the full walkthrough.` Keep the short version in the README for
scannability.

`AGENTS.md`: find the "Build & verify commands" section. If Plan 002
already replaced the `TODO(plan-003)` marker there, verify the table lists
`bash scripts/verify.sh` as the one-command "before you push" check. If
not, add it. Also add a short "CI" subsection: "CI (GitHub Actions,
`.github/workflows/ci.yml`) runs the same three Gradle tasks on push
and PR. A red CI run blocks merging."

**Verify**:
- `grep -q "sideload-galaxy-s22.md" README.md` — true.
- `grep -q "scripts/verify.sh" AGENTS.md` — true.
- `grep -q "GitHub Actions" AGENTS.md` — true.

### Step 5: Update `plans/README.md` status row for Plan 003

Change row `003` status to `DONE`.

## Test plan

- No unit tests to add.
- Manual: push to a throwaway branch, confirm the CI workflow runs and
  passes. If you can't push (no operator instruction to), run
  `act -j build` (nektos/act) locally to dry-run the workflow. If `act`
  isn't available, `bash scripts/verify.sh` is the acceptable
  substitute.
- Confirm the artifact upload step of CI actually produces
  `app-debug-apk` in the run's artifacts tab.

## Done criteria

- [ ] `.github/workflows/ci.yml` exists and is valid YAML.
- [ ] `scripts/verify.sh` exists, is executable, and exits 0.
- [ ] `docs/sideload-galaxy-s22.md` exists.
- [ ] `README.md` links to `docs/sideload-galaxy-s22.md`.
- [ ] `AGENTS.md` documents `bash scripts/verify.sh` and CI.
- [ ] `plans/README.md` row for Plan 003 shows `DONE`.

## STOP conditions

- Plan 002's build is not green (`./gradlew assembleDebug` fails
  locally) — fix Plan 002 first.
- `android-actions/setup-android@v3` is unavailable/deprecated in the
  GitHub Actions marketplace at execution time. Use the closest
  official replacement, note the substitution in the commit message,
  and re-verify with `bash scripts/verify.sh`.
- CI succeeds locally (via `act`) but fails on GitHub with a
  license-acceptance error — the SDK Manager needs an
  `sdkmanager --licenses` yes-piped step; add it and re-push. Do not
  hardcode license hashes.
- `gradle/libs.versions.toml` uses a Kotlin/AGP combo that requires
  JDK 21 — bump the workflow's `java-version` to `21` and update
  `AGENTS.md` to match. Do not downgrade Kotlin/AGP.

## Maintenance notes

- The workflow uses `hashFiles('**/gradle-wrapper.properties', ...)` as
  cache key — bumping Gradle wrapper is safe and re-warms cache.
- If a future plan adds instrumented tests (device tests), keep them
  out of `ci.yml` (they need a hardware or emulator runner). Add a
  `device-tests.yml` at that time.
- `scripts/verify.sh`'s grep guards are cheap — add one per class of
  regression future plans want to prevent (e.g. Plan 010 may add a
  "no `Log.d` in release code" guard).
