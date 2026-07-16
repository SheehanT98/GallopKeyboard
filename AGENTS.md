# AGENTS.md — GallopKeyboard agent rules

Read this file first when working in this repository. It is the single source of truth for build commands, coding conventions, and hard constraints that every executor plan references.

## What this repo is

GallopKeyboard is a personal Android keyboard IME — a fork of [Dictus](https://github.com/getdictus/dictus-android) extended with a DeepSeek-style voice panel toggle, hybrid offline speech-to-text (streaming Parakeet + Whisper polish on stop), and Gboard-like typing basics. English only, 100% on-device STT, no cloud services. Target device is the owner's Samsung Galaxy S22 (8 GB RAM). v1 ships as a sideload APK; Play Store release is deferred. See `CONTEXT.md` for glossary and acceptance criteria.

## Build & verify commands

<!-- TODO(plan-003): replace placeholders with verified commands once Gradle import lands -->

| Purpose | Command | Notes |
|---------|---------|-------|
| Debug APK | `./gradlew assembleDebug` | Produces installable debug APK |
| All unit tests | `./gradlew testAll` | Runs module unit tests |
| IME lint | `./gradlew :ime:lint` | Android lint for the IME module |

These commands will not work until Plan 002 has landed the Dictus import. Until then, only the docs/ADR checks apply.

## Coding conventions

- **Language**: Kotlin with Jetpack Compose for new UI; existing Dictus views may remain XML until migrated incrementally.
- **UI toolkit**: Material 3 (`MaterialTheme`, `Material3` components).
- **Min SDK**: 29 (Android 10).
- **Dependency injection**: Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@Inject`).
- **Naming**: `camelCase` for properties, functions, and local variables; `PascalCase` for classes, composables, and enums.
- **Comments**: English only.
- **UI strings**: English only in v1 — upstream Dictus may ship other locales; GallopKeyboard does not (see ADR-0004).
- **Threading**: STT inference and audio I/O off the IME main thread (see ADR-0002).
- **Package root**: `com.gallopkeyboard` per ADR-0004 — do not rename without updating that ADR.

## Do NOT

- Do not add cloud STT, telemetry, analytics, or crash reporters — v1 is 100% offline (see `CONTEXT.md` "Out of scope").
- Do not add swipe/gesture typing.
- Do not add non-English keyboard layouts or locales in v1.
- Do not commit `.env`, keystores, `local.properties`, or model binaries.
- Do not edit `HANDOFF.md`, `CONTEXT.md`, or `BOOTSTRAP.md` — those are historical inputs.
- Do not change the package name or `applicationId` without an ADR update — see `docs/adr/0004-package-naming-and-application-id.md`.

## Planning system

Work is organized as sequential plans under `plans/`. Read `plans/README.md` for ordering and status. Follow the plan file you were dispatched with; do not read sibling plans unless the plan says to. Update your row in `plans/README.md` when done.

## Where to find things

- [`HANDOFF.md`](./HANDOFF.md) — full grilling spec: UX, smart button logic, phased delivery, model defaults.
- [`CONTEXT.md`](./CONTEXT.md) — glossary, acceptance criteria, out-of-scope list, target device.
- [`docs/adr/`](./docs/adr/) — architecture decision records (fork rationale, hybrid STT, gesture spec, package naming).
- [`plans/`](./plans/README.md) — executor plans 001–010 with dependencies and status.
- [`docs/android-toolchain.md`](./docs/android-toolchain.md) — JDK 17 and Android SDK setup (relevant from Plan 002 onward).
- **Upstream Dictus**: https://github.com/getdictus/dictus-android (MIT, `develop` branch).
- **whisper.cpp**: imported as a Git submodule by Plan 002 for the Whisper polish pass (see ADR-0002).

## ADR quick reference

| ADR | Topic |
|-----|-------|
| 0001 | Fork Dictus as Android foundation |
| 0002 | Hybrid STT pipeline (streaming + polish) |
| 0003 | Smart voice button gesture spec (`400 ms` hold threshold, `48 dp` cancel slop) |
| 0004 | Package naming (`com.gallopkeyboard.ime`, `gallopkeyboard-android`) |

## Devteam workflow

Agent jobs are tracked under `devteam/jobs/`. Use `/devteamquick plans/NNN-*.md` to dispatch a single plan, or see `devteam/PHASE-ORCHESTRATION.md` to queue phases. Model policy lives in `devteam/MODEL-POLICY.md`.
