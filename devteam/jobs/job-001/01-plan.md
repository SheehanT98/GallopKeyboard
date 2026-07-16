# Plan 001: Repo hygiene, AGENTS.md, and supporting ADRs

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 99ca844..HEAD -- README.md HANDOFF.md CONTEXT.md BOOTSTRAP.md docs/ .gitignore LICENSE AGENTS.md`
> If any of those files changed since this plan was written, re-read them
> before proceeding. If new source code has landed under `app/`, `ime/`,
> `core/`, `asr/`, or `whisper/`, this plan is stale — Plan 002 may have
> already run — STOP and report.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: dx, docs
- **Planned at**: commit `99ca844`, 2026-07-16
- **Issue**: —

## Why this matters

Right now the repo is only bootstrap docs (`README.md`, `HANDOFF.md`,
`CONTEXT.md`, `BOOTSTRAP.md`, two ADRs). Before we import Dictus in Plan 002
we need three things in place so cheap executor agents can work reliably:

1. **AGENTS.md** — a rules file at the repo root telling agents the build
   commands, coding conventions, and hard "do not do" list. Without this,
   subsequent plans have to re-derive context every time.
2. **Two more ADRs**, one for the smart voice button gesture spec and one
   for the target package name / applicationId. Both are referenced by
   later plans and need to be settled before Dictus is imported (renaming
   is much cheaper before the fork than after).
3. **Baseline `LICENSE` and `.gitignore`** so we don't accidentally commit
   `.gradle/`, `local.properties`, or Android build output the moment Plan
   002 lands. `LICENSE` also has to be MIT to be compatible with Dictus
   (required by Plan 002).

None of this touches code — it's cheap, low-risk, and unblocks everything else.

## Current state

Files that exist today (verify with `ls -1`):

- `README.md` — one-screen project blurb + "next step" pointing at `/improve`
- `HANDOFF.md` — the full grilling spec (do not edit; it's the input to
  planning)
- `CONTEXT.md` — glossary + acceptance criteria (do not edit)
- `BOOTSTRAP.md` — one-time doc explaining how these files got here; can be
  deleted once the fork is in
- `docs/adr/0001-fork-dictus.md`
- `docs/adr/0002-hybrid-stt-pipeline.md`
- `skills-lock.json` — leave alone (managed by the improve skill)
- `.agents/` — leave alone (skill install)
- `plans/` — plan files (this file plus siblings)

There is **no** `LICENSE`, `.gitignore`, `AGENTS.md`, or `CLAUDE.md` yet.
Confirm this with:

```
test ! -f LICENSE && test ! -f .gitignore && test ! -f AGENTS.md && test ! -f CLAUDE.md && echo OK
```

Expected output: `OK`.

The existing two ADRs are short and follow the standard "Status / Context /
Decision / Consequences" shape — model new ADRs on their structure. See
`docs/adr/0001-fork-dictus.md` lines 1–20 for the exact form.

Repo conventions to honor:

- Markdown filenames are kebab-case (`0001-fork-dictus.md`, not
  `0001_Fork_Dictus.md`).
- ADR headings use `## Status` / `## Context` / `## Decision` /
  `## Consequences` — match that order and wording.
- Line width is loose; do not hard-wrap. HANDOFF.md is the reference.

## Commands you will need

| Purpose        | Command                                              | Expected on success            |
|----------------|------------------------------------------------------|--------------------------------|
| List repo      | `ls -1 /workspace`                                   | Only files listed above        |
| Confirm no code| `find . -name "*.kt" -not -path "./.agents/*" | head` | Empty output                   |
| MIT template   | `curl -sSL https://raw.githubusercontent.com/getdictus/dictus-android/develop/LICENSE` | Prints MIT LICENSE text |
| Commit         | `git add -A && git commit -m "..."`                  | exit 0                         |

There are no tests, no lint, and no typecheck yet — those come in Plan 003.

## Scope

**In scope** (the only files you should create or modify):
- `LICENSE` (create)
- `.gitignore` (create)
- `AGENTS.md` (create)
- `CLAUDE.md` (create — one-line stub that points at `AGENTS.md`)
- `docs/adr/0003-smart-button-gesture-spec.md` (create)
- `docs/adr/0004-package-naming-and-application-id.md` (create)
- `plans/README.md` status row for this plan (update)

**Out of scope** (do NOT touch, even though they look related):
- `HANDOFF.md`, `CONTEXT.md`, `BOOTSTRAP.md` — historical inputs; leave as-is.
  If you think they're wrong, note it in the ADR under "Consequences",
  don't edit them.
- `README.md` — will be rewritten in Plan 003 once we have build commands.
  Do not touch it now.
- Anything under `.agents/` or `skills-lock.json`.
- No Android code, no Gradle files, no `settings.gradle.kts` — Plan 002
  imports all of that in one operation.

## Git workflow

- Branch: `advisor/001-repo-hygiene` (created off the current branch).
- One commit per created file is fine, or one commit per logical group
  (`docs: add LICENSE and .gitignore`, `docs: add AGENTS.md`,
  `docs: add ADR-0003 and ADR-0004`).
- Commit messages: imperative mood ("Add MIT LICENSE", not "Added MIT
  LICENSE"). No emoji.
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Add `LICENSE` (MIT, copied from Dictus)

Dictus is MIT and Plan 002 will import its code, so this repo must also be
MIT. Copy the exact text of Dictus's `LICENSE` file:

```
curl -sSL https://raw.githubusercontent.com/getdictus/dictus-android/develop/LICENSE > LICENSE
```

Then **edit only the copyright line** to be:

```
Copyright (c) 2026 GallopKeyboard contributors
Copyright (c) 2026 Dictus contributors (original Dictus Android code)
```

Do not alter the MIT permission/limitation text.

**Verify**:
- `head -3 LICENSE` — first line is `MIT License`, second is blank, third
  starts with `Copyright (c) 2026 GallopKeyboard`.
- `wc -l LICENSE` — approximately 21 lines (a standard MIT LICENSE).

### Step 2: Add `.gitignore`

Create `/workspace/.gitignore` with the standard Android/Gradle ignore set.
Content to write verbatim:

```
# Gradle
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar
!**/src/**/build/

# Android Studio / IntelliJ
.idea/
*.iml
local.properties
captures/
.externalNativeBuild/
.cxx/

# Android build outputs
*.apk
*.aab
*.ap_
*.dex
*.class

# NDK
obj/

# Keystores (never commit)
*.jks
*.keystore

# Kotlin
.kotlin/

# Logs / OS
*.log
.DS_Store
Thumbs.db

# Model files (managed by first-launch downloader, see plans/008)
/models/
*.gguf
*.onnx
*.bin
!third_party/**/*.bin

# Env / secrets
.env
.env.local

# Test / coverage
coverage/
```

**Verify**:
- `grep -c '^' .gitignore` — around 40 lines.
- `git check-ignore build/foo` — prints `build/foo` (confirms `build/` is
  ignored).

### Step 3: Add `AGENTS.md`

This is the single most important file for the plans that follow —
cheap executors will read it first. Create `/workspace/AGENTS.md` with
these sections (write them out; do not link off to other files for
essentials):

- **What this repo is** — one paragraph from `README.md` + `CONTEXT.md`
  (fork of Dictus, English-only Android IME with DeepSeek-style voice
  panel, target Galaxy S22, sideload v1).
- **Build & verify commands** — a table with placeholders for now
  (`./gradlew assembleDebug`, `./gradlew testAll`, `./gradlew :ime:lint`);
  add a note "These commands will not work until Plan 002 has landed the
  Dictus import. Until then, only the docs/ADR checks apply."
- **Coding conventions** — Kotlin + Jetpack Compose, Material 3, minSDK 29,
  Hilt for DI, `camelCase` for members, `PascalCase` for classes and
  composables. Comments in English. UI strings in English (see ADR-0004
  for how this differs from upstream Dictus).
- **Do NOT** list, verbatim:
  - Do not add cloud STT, telemetry, analytics, or crash reporters — v1
    is 100% offline (see `CONTEXT.md` "Out of scope").
  - Do not add swipe/gesture typing.
  - Do not add non-English keyboard layouts or locales in v1.
  - Do not commit `.env`, keystores, `local.properties`, or model
    binaries.
  - Do not edit `HANDOFF.md`, `CONTEXT.md`, or `BOOTSTRAP.md` — those
    are historical inputs.
  - Do not change the package name or `applicationId` without an ADR
    update — see `docs/adr/0004`.
- **Planning system** — one paragraph: "Work is organized as sequential
  plans under `plans/`. Read `plans/README.md` for ordering and status.
  Follow the plan file you were dispatched with; do not read siblings
  unless the plan says to. Update your row in `plans/README.md` when done."
- **Where to find things** — bullet list: HANDOFF.md, CONTEXT.md, docs/adr/,
  plans/, upstream Dictus URL, whisper.cpp submodule note.

**Verify**:
- `test -f AGENTS.md && wc -l AGENTS.md` — file exists, at least 60 lines.
- `grep -q "Do NOT" AGENTS.md && grep -q "assembleDebug" AGENTS.md && echo OK`
  — prints `OK`.

### Step 4: Add `CLAUDE.md` stub

One-line pointer so tools that only read `CLAUDE.md` still find rules:

```markdown
# CLAUDE.md

This project's agent rules live in [`AGENTS.md`](./AGENTS.md). Read that first.
```

**Verify**: `cat CLAUDE.md | wc -l` — 3 lines (heading, blank, sentence).

### Step 5: Add ADR-0003 — smart button gesture spec

Create `/workspace/docs/adr/0003-smart-button-gesture-spec.md` using the
same 4-heading shape as ADR-0001. Content requirements:

- **Status**: Accepted
- **Context**: 2–4 sentences. Owner wants one button that serves both quick
  bursts and long dictation, without a mode switch. HANDOFF.md fixes the
  gesture: tap-tap = long, hold ≥ threshold + release = short.
- **Decision**: A numbered list of the exact behaviors, mirroring
  `HANDOFF.md` "Smart button logic":
  1. On `ACTION_DOWN`: start hold timer; begin recording; start streaming pass.
  2. If `ACTION_UP` before **400 ms**: enter tap-toggle mode — first tap
     started recording, second tap stops recording and triggers polish.
  3. If pointer is still down at **400 ms**: enter hold mode — show
     "recording" visual state; `ACTION_UP` stops recording and triggers
     polish.
  4. While recording (either mode): partial transcript from Parakeet is
     committed as composing text (see ADR-0002).
  5. On stop: Whisper polish replaces the composing text with the final
     transcript, or falls back to the last streaming partial if polish
     exceeds a **2000 ms** timeout (see ADR-0002 acceptance criterion).
  6. Cancel gestures (`ACTION_CANCEL`, pointer leaves button bounds by
     more than **48 dp** slop): discard the recording session — no commit,
     no polish. This prevents accidental long dictations when the user
     drags off the button.
- **Consequences**: threshold values are named constants so Plan 005 can
  reference them; the streaming/polish contract is fixed by ADR-0002; any
  future change to gesture behavior updates this ADR *and* Plan 005's tests.

**Verify**: `test -f docs/adr/0003-smart-button-gesture-spec.md &&
grep -q "400 ms" docs/adr/0003-smart-button-gesture-spec.md &&
grep -q "48 dp" docs/adr/0003-smart-button-gesture-spec.md && echo OK`
— prints `OK`.

### Step 6: Add ADR-0004 — package naming and applicationId

Create `/workspace/docs/adr/0004-package-naming-and-application-id.md`.
Content:

- **Status**: Accepted
- **Context**: The fork must be installable alongside upstream Dictus on
  the same device (developer wants to compare). Both apps also declare an
  Android `InputMethodService`, so both must have distinct
  `applicationId`s and distinct IME service component names. The rename
  needs to be settled *before* Plan 002 imports Dictus source so we do it
  once, not twice.
- **Decision**:
  - Android `applicationId`: **`com.gallopkeyboard.ime`**
  - Kotlin/Java package root: **`com.gallopkeyboard`** (submodules use
    child packages: `com.gallopkeyboard.ime`, `com.gallopkeyboard.asr`,
    `com.gallopkeyboard.core`, `com.gallopkeyboard.whisper`,
    `com.gallopkeyboard.app`).
  - Gradle root project name: **`gallopkeyboard-android`**.
  - App display name (`app_name`): **`GallopKeyboard`**.
  - IME label: **`GallopKeyboard`**.
  - MIT attribution to upstream Dictus is preserved (see `LICENSE` +
    Plan 002 Step 8).
- **Consequences**: Plan 002 renames every Dictus package reference from
  the upstream root (whatever it is — Plan 002 discovers it) to
  `com.gallopkeyboard.*` in one pass. Any future rename repeats that
  operation and updates this ADR. Installing alongside Dictus is a
  supported dev workflow.

**Verify**: `test -f docs/adr/0004-package-naming-and-application-id.md &&
grep -q "com.gallopkeyboard.ime" docs/adr/0004-package-naming-and-application-id.md && echo OK`
— prints `OK`.

### Step 7: Update `plans/README.md` status row

Change the Status column for row `001` from `TODO` to `DONE`. Do not
touch other rows.

**Verify**: `grep -E "^\| 001 " plans/README.md` — the row now ends with
`| DONE |`.

## Test plan

There are no automated tests at this stage. Verification is by inspection
using the commands in each step. As a final sweep:

```
# 1. All expected files exist
for f in LICENSE .gitignore AGENTS.md CLAUDE.md \
         docs/adr/0003-smart-button-gesture-spec.md \
         docs/adr/0004-package-naming-and-application-id.md; do
  test -f "$f" || { echo "MISSING: $f"; exit 1; }
done && echo "all present"

# 2. No unexpected source code appeared
find . -type f \( -name "*.kt" -o -name "*.java" -o -name "*.gradle*" \) \
  -not -path "./.agents/*" -not -path "./.git/*" | wc -l
```

Expected: `all present` from the loop; `0` from the find (no Android
source yet).

## Done criteria

Machine-checkable. ALL must hold:

- [ ] `LICENSE`, `.gitignore`, `AGENTS.md`, `CLAUDE.md` exist at repo root.
- [ ] `docs/adr/0003-smart-button-gesture-spec.md` exists and contains
      `400 ms` and `48 dp`.
- [ ] `docs/adr/0004-package-naming-and-application-id.md` exists and
      contains `com.gallopkeyboard.ime`.
- [ ] `head -1 LICENSE` prints `MIT License`.
- [ ] `git status` shows only the in-scope files as changes.
- [ ] `plans/README.md` row for Plan 001 shows status `DONE`.

## STOP conditions

Stop and report back (do not improvise) if:

- Any of `README.md`, `HANDOFF.md`, `CONTEXT.md`, `BOOTSTRAP.md`,
  `docs/adr/0001-fork-dictus.md`, or `docs/adr/0002-hybrid-stt-pipeline.md`
  have been modified since this plan's Planned-at SHA (`99ca844`) — the
  planning inputs may have moved.
- Source code files (`.kt`, `.java`, `.gradle*`, `AndroidManifest.xml`)
  already exist anywhere in the repo — Plan 002 has already run and the
  ADR-0004 package decision may conflict with what's there. Report the
  existing `applicationId` and package root before proceeding.
- Dictus's upstream `LICENSE` is no longer MIT when you fetch it in Step
  1. Do not invent a license — stop and report.

## Maintenance notes

- ADR-0003's `400 ms` and `2000 ms` values are the *reference* thresholds;
  Plan 005 (gesture) and Plan 007 (polish) both encode them as named
  constants. If either constant moves, update the ADR first and then the
  code — never the other way around.
- ADR-0004's `applicationId` is baked into the IME component name Android
  writes to system settings. Changing it after users have installed the
  APK means users lose their keyboard selection until they re-enable it.
  Only rename via a new ADR.
- `AGENTS.md` will be re-scoped in Plan 003 once real build commands
  work — leave a `TODO(plan-003)` marker in the "Build & verify" section
  so it's obvious what to update.
