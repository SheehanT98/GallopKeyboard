# Plan 002: Fork Dictus into this repo (import + rename + attribution)

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 99ca844..HEAD -- LICENSE .gitignore AGENTS.md docs/adr/`
> If those files are absent or empty, Plan 001 has not landed — STOP and
> report. Also run `find . -maxdepth 2 -type d -name "app" -o -name "ime" -o -name "core"` — if any of those directories already exist, Plan 002 has
> already been attempted; read Step 1 STOP guidance below.

## Status

- **Priority**: P1
- **Effort**: L
- **Risk**: MED
- **Depends on**: `plans/001-repo-hygiene-and-agents-md.md`
- **Category**: migration
- **Planned at**: commit `99ca844`, 2026-07-16
- **Issue**: —

## Why this matters

We're forking [Dictus](https://github.com/getdictus/dictus-android) as the
Android IME + Whisper foundation (see `docs/adr/0001-fork-dictus.md`).
Every later plan (voice panel, smart button, hybrid STT, model download,
polish, hardening) modifies or adds *around* Dictus code. Until Dictus is
imported and building under the `com.gallopkeyboard.*` package, no other
plan can start. This is the single biggest one-shot import in the whole
roadmap.

The goal is a **fully building, fully-renamed, MIT-attributed** import
that a fresh `./gradlew assembleDebug` completes on. No new features, no
gesture logic, no voice panel yet — those are Plans 004+.

## Current state

Upstream Dictus (branch `develop`, verified 2026-07-16):

- Kotlin + Jetpack Compose, minSDK 29, Material 3.
- Modules in `settings.gradle.kts`: `:app :ime :core :whisper :asr`.
- Plugins in root `build.gradle.kts` (via `libs.plugins.*` — version
  catalog): Android application/library, Kotlin Android, Kotlin Compose,
  Hilt, KSP, Licensee.
- `third_party/whisper.cpp` is a git submodule
  (`url = https://github.com/ggml-org/whisper.cpp.git`, path
  `third_party/whisper.cpp`).
- `CLAUDE.md` in upstream is written in French; iOS-port context.
- Contains `PRD.md`, `.planning/`, `design/` (Pencil files) — those are
  Dictus-specific and MUST NOT be carried into this repo (see Step 6).
- Default branch: `develop`, not `main`.
- **Not yet observed** in upstream: any sherpa-onnx / Parakeet streaming
  integration. The `asr` module may only wrap `whisper.cpp`. Verify in
  Step 3 and record findings — this affects Plan 006.

Naming decisions from `docs/adr/0004-package-naming-and-application-id.md`:

- Root Gradle project name: `gallopkeyboard-android`
- `applicationId`: `com.gallopkeyboard.ime`
- Kotlin root: `com.gallopkeyboard`
- App label: `GallopKeyboard`

## Commands you will need

| Purpose               | Command                                                                 | Expected on success |
|-----------------------|-------------------------------------------------------------------------|---------------------|
| JDK check             | `java -version 2>&1 \| head -1`                                          | 17 or 21            |
| Android SDK env       | `echo "$ANDROID_HOME"`                                                  | non-empty path      |
| Gradle assemble       | `./gradlew --no-daemon assembleDebug`                                   | `BUILD SUCCESSFUL`  |
| Gradle unit tests     | `./gradlew --no-daemon testAll`                                         | `BUILD SUCCESSFUL`  |
| Package sweep         | `grep -rn "package " --include='*.kt' app ime core asr whisper \| head` | all begin `com.gallopkeyboard.` |
| Old-name sweep        | `grep -rn "dictus" --include='*.kt' --include='*.kts' --include='*.xml' . \| grep -v third_party \| grep -v LICENSE \| grep -v NOTICE \| head` | empty (after Steps 4–5) |

If `ANDROID_HOME` is empty, run:

```
sdkmanager --install "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

If `sdkmanager` is not on PATH, STOP — see STOP conditions.

## Suggested executor toolkit

- If you have access to the `bootstrap` or `deployments-cicd` skills, use
  them only for the CI wiring in Plan 003 — this plan is pure import.
- Read `AGENTS.md` first (created in Plan 001). The "Do NOT" list is
  authoritative — do not add analytics, telemetry, or non-English UI even
  if upstream Dictus has them.

## Scope

**In scope**:
- Everything upstream Dictus ships *except* the exclusion list in Step 6.
- `README.md` (rewritten with build instructions).
- `NOTICE` (new — Dictus attribution).
- Any file under `app/`, `ime/`, `core/`, `asr/`, `whisper/`, `gradle/`,
  `third_party/`, `settings.gradle.kts`, `build.gradle.kts`,
  `gradle.properties`, `gradlew`, `gradlew.bat`, `.gitmodules`.
- `plans/README.md` status row for this plan.

**Out of scope** (do NOT bring across from upstream, even though they are
present):
- `PRD.md` — Dictus product doc, not ours.
- `.planning/` — Dictus's private planning notes.
- `design/` and its `.pen` files — Dictus's iOS-parity mockups, wrong
  language and wrong target UI (we're a keyboard fork, not an iOS port).
- `CLAUDE.md` — we already have our own (Plan 001).
- `CONTRIBUTING.md` — we don't have contributors yet; add later if needed.
- `screenshots/` — not our screenshots.
- Any `.github/` workflows from upstream — Plan 003 writes ours.
- Any `LICENSE` file from upstream — Plan 001 already wrote ours (which
  contains the required Dictus copyright line).

Also out of scope:
- Feature changes. Do not add voice panel, smart button, panel toggle,
  or streaming ASR. Those are Plans 004+. If a feature "just needs one
  line," it doesn't — put it in the correct later plan.
- Removing Dictus features that we plan to reuse (Whisper transcription,
  IME service, keyboard views). If in doubt, keep it.
- Changing minSDK, targetSDK, Kotlin version, or Compose version.

## Git workflow

- Branch: `advisor/002-fork-dictus` (created off the branch that landed
  Plan 001).
- Commit granularity, in order:
  1. `chore: import Dictus source at <upstream sha>` — the raw copy (Step 1–2).
     Include the exact upstream commit SHA in the message body.
  2. `chore: apply .gitignore and drop out-of-scope upstream files` (Step 6).
  3. `refactor: rename Dictus package to com.gallopkeyboard` (Step 4).
  4. `refactor: rename Gradle project + applicationId + IME label` (Step 5).
  5. `docs: add NOTICE with Dictus attribution` (Step 8).
  6. `docs: rewrite README with build + sideload instructions` (Step 9).
  7. `chore: land Dictus submodules and verify build` (Steps 3, 7).
- If any commit fails to build (test with `./gradlew assembleDebug`),
  amend that commit — do not stack broken commits.
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Confirm target directory is clean of Dictus code

```
find . -maxdepth 3 -type d \( -name app -o -name ime -o -name core -o -name asr -o -name whisper -o -name third_party \) -not -path './.git/*' -not -path './.agents/*'
```

Expected: no output. If any of those directories exist, STOP — a
previous fork attempt is in flight. Do not overwrite it.

### Step 2: Import Dictus source (fresh checkout, not a fork remote)

We import as a **snapshot**, not a fork with upstream tracking. Reasoning:
this is a personal project with divergent product goals; keeping git
history clean is worth more than easy upstream merges. Attribution lives
in `LICENSE` and `NOTICE`.

```
DICTUS_SHA=$(git ls-remote https://github.com/getdictus/dictus-android.git develop | awk '{print $1}')
echo "$DICTUS_SHA" > /tmp/dictus.sha   # remember for the commit message
git clone --branch develop --depth 1 https://github.com/getdictus/dictus-android.git /tmp/dictus-src
# Copy everything EXCEPT .git and the exclusion list (real removal happens in Step 6;
# we do a fast prune here so the initial commit is closer to what we want).
cd /tmp/dictus-src && rm -rf .git PRD.md .planning design CLAUDE.md \
  CONTRIBUTING.md screenshots .github && cd -
cp -a /tmp/dictus-src/. /workspace/
```

Note: `cp -a` preserves file modes. If it copies files over your existing
`LICENSE`, `.gitignore`, `AGENTS.md`, `CLAUDE.md`, `README.md`,
`docs/`, or `plans/`, restore them from git:

```
git checkout -- LICENSE .gitignore AGENTS.md CLAUDE.md README.md docs/ plans/
```

**Verify**:
- `test -f settings.gradle.kts && test -d ime && test -d app && test -f gradlew && echo OK` — prints `OK`.
- `head -1 LICENSE` — still `MIT License` from Plan 001, not upstream's.
- `test -f AGENTS.md` — still true.

Commit as `chore: import Dictus source at <sha>` where `<sha>` is the
first 12 chars of `$DICTUS_SHA`.

### Step 3: Initialize submodules and record what's actually there

```
git submodule update --init --recursive
find third_party -maxdepth 2 -type d
```

Record the output in the commit body of the "chore: land Dictus
submodules" commit later. This is your source of truth for what ASR
engines actually ship — do NOT trust the HANDOFF's claim that Parakeet
is already integrated. It probably isn't.

Then investigate:

```
ls asr/src/main/*/com* 2>/dev/null || find asr/src -name "*.kt" | head
grep -rIn -e "sherpa" -e "Parakeet" -e "parakeet" --include='*.kt' --include='*.kts' . | head
grep -rIn "whisper" --include='*.kt' --include='*.kts' . | head
```

Record findings in a new file `docs/dictus-inventory.md` with these
sections (this file *is* in-scope for this plan):

- Upstream commit SHA imported.
- Modules present: `:app :ime :core :whisper :asr` (confirm names in
  `settings.gradle.kts`).
- Whisper integration: which module, which class(es) call whisper.cpp
  JNI, where the model file path is configured.
- Sherpa-ONNX / Parakeet: **present or absent**. If absent, note that
  Plan 006 (streaming pass) needs a sub-plan to add sherpa-onnx first.
- IME entry point: file/class that extends `InputMethodService`, and its
  declaration in `ime/src/main/AndroidManifest.xml`.
- Model download / storage code: which class, which directory.
- Any UI framework other than Compose (e.g. XML `KeyboardView`).

Keep this doc under 200 lines and factual — no opinions, no plans.

### Step 4: Rename Kotlin package root to `com.gallopkeyboard`

Discover the upstream root:

```
grep -h '^package ' $(find app ime core asr whisper -name '*.kt') | sort -u | head
```

You will see something like `package com.dictus.ime`, `package com.dictus.core`,
etc. Let `OLD_ROOT` be the common prefix (probably `com.dictus`). If the
prefix is not consistent, STOP — the ADR-0004 rename plan assumed one root.

Do the rename in three passes (do NOT try to sed everything in one shot):

**Pass A — move directories**:

```
# For each module, move src/main/java/<OLD_ROOT_PATH> to src/main/java/com/gallopkeyboard
for module in app ime core asr whisper; do
  for src in $module/src/main/java $module/src/main/kotlin \
             $module/src/test/java $module/src/test/kotlin \
             $module/src/androidTest/java $module/src/androidTest/kotlin; do
    old="$src/$(echo $OLD_ROOT | tr '.' '/')"
    new="$src/com/gallopkeyboard"
    if [ -d "$old" ]; then
      mkdir -p "$(dirname $new)"
      git mv "$old" "$new"
    fi
  done
done
```

**Pass B — rewrite `package` and `import` statements**:

```
# Only *.kt files, only inside modules we control
find app ime core asr whisper -name '*.kt' -print0 | xargs -0 \
  sed -i "s|$OLD_ROOT|com.gallopkeyboard|g"
```

**Pass C — rewrite Gradle files and manifests**:

```
find . -type f \( -name 'build.gradle.kts' -o -name 'AndroidManifest.xml' \
                  -o -name '*.xml' \) \
       -not -path './third_party/*' -not -path './.git/*' -print0 \
  | xargs -0 sed -i "s|$OLD_ROOT|com.gallopkeyboard|g"
```

**Verify**:
- `grep -rn "^package $OLD_ROOT" --include='*.kt' app ime core asr whisper` — empty.
- `grep -rn "^package com.gallopkeyboard" --include='*.kt' app ime core asr whisper | head` — many lines.
- `./gradlew --no-daemon compileDebugKotlin` — `BUILD SUCCESSFUL`.
  If not, resolve import-name mismatches revealed by the compiler
  output. Do NOT proceed to Step 5 until compilation succeeds.

### Step 5: Rename Gradle project, applicationId, IME label

Edit `settings.gradle.kts`:

- Change `rootProject.name = "dictus-android"` to
  `rootProject.name = "gallopkeyboard-android"`.
- Leave the `include(...)` line untouched.

Edit `app/build.gradle.kts`:

- Locate the `defaultConfig { ... }` block and change `applicationId`
  to `com.gallopkeyboard.ime`.
- Locate `namespace` and change it to `com.gallopkeyboard.app`.
- Also update `versionCode` to `1` and `versionName` to `"0.1.0"` if
  upstream had values — this is our fresh version line.

Edit `ime/build.gradle.kts`:

- Change `namespace` to `com.gallopkeyboard.ime`.

For each of `core/build.gradle.kts`, `asr/build.gradle.kts`,
`whisper/build.gradle.kts`, do the same `namespace` rewrite to the
matching `com.gallopkeyboard.<module>` value.

Rename Android resources:

- In every `res/values*/strings.xml` under `app/` and `ime/`, replace the
  `app_name` and (if present) `ime_label`, `ime_name`,
  `settings_activity_label` values with `GallopKeyboard`. Do NOT translate
  or add other locales — English only per `CONTEXT.md`. If upstream has
  `res/values-fr/` or other-locale strings.xml, **delete those files** in
  the same commit (per ADR-0004 English-only decision). Note in the
  commit message which locales were removed.

Rename the IME service settings (metadata XML):

- Find `ime/src/main/res/xml/method.xml` (or similar Android
  input-method metadata). Update the `android:label`, `android:settingsActivity`, and any subtype `android:label` to `GallopKeyboard` /
  `com.gallopkeyboard.*` as appropriate. Leave subtype `imeSubtypeLocale`
  as `en_US` (or add it if missing) — English only.

**Verify**:
- `grep -n rootProject.name settings.gradle.kts` — prints
  `gallopkeyboard-android`.
- `grep -rn applicationId app/build.gradle.kts` — prints
  `com.gallopkeyboard.ime`.
- `grep -rn "dictus" --include='*.kts' --include='*.xml' . | grep -v third_party | grep -v LICENSE | grep -v NOTICE` — empty (any remaining
  hit means Step 4 or 5 missed a spot; fix and re-run).
- `./gradlew --no-daemon assembleDebug` — `BUILD SUCCESSFUL`.

### Step 6: Delete upstream files that should not be in this fork

Step 2 already pruned most of them, but run this to catch anything that
slipped through:

```
for p in PRD.md .planning design CLAUDE.md.upstream CONTRIBUTING.md screenshots .github/workflows; do
  test -e "$p" && rm -rf "$p" && echo "removed $p"
done
```

Also delete `docs/adr/*` files that came from upstream (Dictus has none
in the observed tree, but check): `ls docs/adr/` should show only
`0001-fork-dictus.md`, `0002-hybrid-stt-pipeline.md`,
`0003-smart-button-gesture-spec.md`, `0004-package-naming-and-application-id.md`.

**Verify**: `ls -A | sort` — no `PRD.md`, `.planning`, `design`,
`CONTRIBUTING.md`, `screenshots`, `CLAUDE.md.upstream`; **and** the files
you preserved in Step 2 (`LICENSE`, `.gitignore`, `AGENTS.md`,
`CLAUDE.md`, `README.md`, `docs/`, `plans/`) all exist.

### Step 7: Re-run the full build and tests

```
./gradlew --no-daemon clean assembleDebug testAll
```

Expected: `BUILD SUCCESSFUL in <time>`. All tests that pass upstream
should still pass after the rename. If a test fails only because it
hardcoded `com.dictus.*`, fix the string — that's a Step 4 miss, not a
scope creep.

If a test relied on network, model files, or an actual audio device,
mark it `@Ignore` with a comment `// TODO(plan-005/plan-006): re-enable`
so it doesn't block CI. Note every such ignore in the commit body.

### Step 8: Add `NOTICE` with upstream attribution

Create `/workspace/NOTICE`:

```
GallopKeyboard
Copyright (c) 2026 GallopKeyboard contributors

This product includes software developed by the Dictus authors:
  Dictus Android — https://github.com/getdictus/dictus-android
  Copyright (c) Dictus contributors, licensed under the MIT License.

This product includes third-party software distributed under separate
licenses. See LICENSE and the files under third_party/ for details.
Notable dependencies:
  - whisper.cpp — MIT License — https://github.com/ggml-org/whisper.cpp
```

If Step 3 discovered other native/binary dependencies (e.g. sherpa-onnx
after Plan 006 lands, or ONNX Runtime), add lines for them **only when
they land**. Do not preemptively list what isn't in the tree yet.

**Verify**: `test -f NOTICE && grep -q "Dictus" NOTICE && grep -q "whisper.cpp" NOTICE && echo OK` — prints `OK`.

### Step 9: Rewrite `README.md` for the fork

Replace the current `README.md` (which points at HANDOFF.md as the "next
step") with a build-focused README. Sections in order:

- H1: `GallopKeyboard`
- One-paragraph description (from `CONTEXT.md`, first paragraph).
- Section: **Build the debug APK**
  - Prereqs: JDK 17+, Android SDK 34, `ANDROID_HOME` set.
  - `git clone --recurse-submodules <this repo URL>`
  - `./gradlew assembleDebug`
  - Output APK path: `app/build/outputs/apk/debug/app-debug.apk`.
- Section: **Install on Galaxy S22**
  - Enable USB debugging, connect device.
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
  - System Settings → General Management → Keyboard → Manage keyboards
    → enable GallopKeyboard → set as default.
  - Grant microphone permission when first tapping the voice panel
    toggle.
- Section: **Repo layout**
  - Bullet list of modules (`app/`, `ime/`, `core/`, `asr/`, `whisper/`,
    `third_party/`, `plans/`, `docs/adr/`) with one line each.
- Section: **Planning system** — one paragraph pointing at
  `plans/README.md` and `AGENTS.md`.
- Section: **License & attribution** — link `LICENSE` and `NOTICE`,
  note MIT + Dictus fork.

Keep it under 150 lines. Do not mention Play Store or iOS.

**Verify**: `grep -q assembleDebug README.md && grep -q "Galaxy S22" README.md && grep -q AGENTS.md README.md && echo OK` — prints `OK`.

### Step 10: Update `AGENTS.md` build commands

Now that the build works, update `AGENTS.md`'s "Build & verify commands"
table by removing the `TODO(plan-003)` marker Plan 001 left there. Fill
in:

- `./gradlew assembleDebug` → produces debug APK
- `./gradlew testAll` → runs all module unit tests
- `./gradlew :ime:lintDebug` → runs Android lint on ime module
- `./gradlew --version` → smoke check for Gradle wrapper

(Plan 003 will add CI-side commands; leave a `TODO(plan-003)` marker
there instead.)

**Verify**: `grep -q "assembleDebug" AGENTS.md && ! grep -q "TODO(plan-003).*Build & verify" AGENTS.md && echo OK` — prints `OK`.

### Step 11: Update `plans/README.md` status row for Plan 002

Change row `002` status from `TODO` to `DONE`.

## Test plan

- No new automated tests written in this plan (this is an import, not a
  feature). What matters is that **upstream Dictus's own tests still
  pass unchanged**. `./gradlew testAll` runs them.
- If Step 3 discovered a Dictus test that references
  `com.dictus.SomeClass` by string (reflection, resource name, etc.),
  fix the string — do not delete the test.
- Manual smoke test (optional but recommended): install the debug APK on
  a Galaxy S22, open Settings → Keyboard, confirm `GallopKeyboard`
  appears as a selectable keyboard. Do **not** actually try to type or
  dictate — Plans 004+ are what make it functional.

## Done criteria

Machine-checkable. ALL must hold:

- [ ] `./gradlew --no-daemon clean assembleDebug testAll` exits 0.
- [ ] `app/build/outputs/apk/debug/app-debug.apk` exists after build.
- [ ] `grep -rn "^package com.dictus" --include='*.kt' app ime core asr whisper` returns empty.
- [ ] `grep -rn "dictus" --include='*.kts' --include='*.xml' . | grep -v third_party | grep -v LICENSE | grep -v NOTICE | grep -v docs/adr/0001` returns empty.
- [ ] `grep applicationId app/build.gradle.kts` shows `com.gallopkeyboard.ime`.
- [ ] `grep rootProject.name settings.gradle.kts` shows `gallopkeyboard-android`.
- [ ] `NOTICE` exists and names Dictus and whisper.cpp.
- [ ] `README.md` includes `assembleDebug` and `Galaxy S22`.
- [ ] `docs/dictus-inventory.md` exists and documents whether Parakeet /
      sherpa-onnx is already integrated.
- [ ] `git status` shows no untracked or unignored build artifacts (`.gradle/`, `build/`, `local.properties` all ignored).
- [ ] `plans/README.md` status row for Plan 002 shows status `DONE`.

## STOP conditions

Stop and report back (do not improvise) if:

- Plan 001's files (`LICENSE`, `AGENTS.md`, `.gitignore`,
  `docs/adr/0003`, `docs/adr/0004`) are missing — Plan 001 hasn't landed.
- `java -version` reports < 17 or `ANDROID_HOME` is unset and
  `sdkmanager` is not available — the environment isn't ready. Do not
  attempt to install Android tooling by hand; report so the operator can
  spin up a properly configured Cursor Cloud Agent environment.
- Upstream Dictus's license file is not MIT when you fetch it — do not
  proceed with the fork.
- Upstream package roots in Step 4 are not all the same prefix (e.g.
  half the modules use `com.dictus.*` and half use
  `com.getdictus.android.*`). The rename strategy assumed one root;
  update ADR-0004 to name every prefix before rewriting.
- `./gradlew assembleDebug` fails after Step 5 and the failure is not
  clearly a missed rename (e.g. NDK toolchain missing, Android SDK
  license not accepted, submodule fetch failure) — this is an env
  problem, not a code problem. Report the exact error.
- Upstream Dictus has added a **non-MIT** dependency that would make our
  fork incompatible with MIT (unlikely, but check
  `licensee` output if Step 7's build runs it). Report the offending
  dependency; do not silently drop it.

## Maintenance notes

- The `chore: import Dictus source at <sha>` commit is the fork
  boundary. When (if) we ever want to pull an upstream Dictus change
  ("cherry-pick"), we diff against that SHA — do not lose it in an
  interactive rebase.
- Because we chose snapshot-not-remote in Step 2, upstream fixes must
  be manually re-applied. This is documented in ADR-0001 as an accepted
  trade-off. If that trade-off becomes painful, add a new ADR before
  adding an `upstream` git remote.
- `docs/dictus-inventory.md` is the reference every later plan reads to
  find Dictus classes. When Plans 004–010 add/rename classes, update
  this inventory in the same commit — do not let it go stale.
- `res/values-<locale>/strings.xml` was deleted for non-English locales.
  If a future plan re-adds a locale, restore from git history
  (`git show <import-sha>:app/src/main/res/values-fr/strings.xml`) and
  update ADR-0004 first.
