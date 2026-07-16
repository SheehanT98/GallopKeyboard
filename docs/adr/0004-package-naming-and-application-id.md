# ADR-0004: Package naming and applicationId

## Status

Accepted

## Context

The fork must be installable alongside upstream Dictus on the same device so the owner can compare behavior during development. Both apps declare an Android `InputMethodService`, so both must have distinct `applicationId`s and distinct IME service component names. The rename needs to be settled before Plan 002 imports Dictus source so the operation happens once, not twice. Upstream Dictus uses its own package root; GallopKeyboard must not collide with it.

## Decision

- Android `applicationId`: **`com.gallopkeyboard.ime`**
- Kotlin/Java package root: **`com.gallopkeyboard`** (submodules use child packages: `com.gallopkeyboard.ime`, `com.gallopkeyboard.asr`, `com.gallopkeyboard.core`, `com.gallopkeyboard.whisper`, `com.gallopkeyboard.app`).
- Gradle root project name: **`gallopkeyboard-android`**.
- App display name (`app_name`): **`GallopKeyboard`**.
- IME label: **`GallopKeyboard`**.
- MIT attribution to upstream Dictus is preserved (see `LICENSE` + Plan 002 Step 8).

## Consequences

- Plan 002 renames every Dictus package reference from the upstream root (whatever it is — Plan 002 discovers it) to `com.gallopkeyboard.*` in one pass.
- Any future rename repeats that operation and updates this ADR.
- Installing alongside Dictus is a supported dev workflow.
- Changing `applicationId` after users have installed the APK means users lose their keyboard selection until they re-enable it — only rename via a new ADR.
