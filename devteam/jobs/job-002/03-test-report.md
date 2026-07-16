# Job 002 — Test report

| Field | Value |
|-------|-------|
| **Job** | job-002 |
| **Branch** | `cursor/devteam-job-002-execute-plan-002-fork-dictus-into-repo-c1fc` |
| **Tester** | devteam-tester (composer-2.5) |
| **Tested at** | 2026-07-16T17:29:13Z |
| **Base SHA (pre-report)** | `3b378ab66a93b9d009b2a139255014a0fad14b5c` |
| **Verdict** | **PASS** |

## Environment

| Check | Command | Result |
|-------|---------|--------|
| Branch | `git branch --show-current` | `cursor/devteam-job-002-execute-plan-002-fork-dictus-into-repo-c1fc` |
| JDK | `java -version 2>&1 \| head -1` | `openjdk version "17.0.19"` |
| Android SDK | `source scripts/android-env.sh && echo "$ANDROID_HOME"` | `/opt/android-sdk` |
| Submodules | `git submodule update --init --recursive` | OK (whisper.cpp present) |

## Gradle verification

| Command | Result |
|---------|--------|
| `./gradlew --no-daemon clean assembleDebug testAll` | **BUILD SUCCESSFUL** in 1m 29s (227 tasks; 207 executed) |
| `./gradlew --no-daemon compileDebugKotlin` | **BUILD SUCCESSFUL** in 8s |
| APK output | `app/build/outputs/apk/debug/app-debug.apk` — 156 MB |

## Done-criteria checks (Plan 002)

| Criterion | Command / check | Result |
|-----------|-----------------|--------|
| Full build + tests | `./gradlew --no-daemon clean assembleDebug testAll` | PASS (exit 0) |
| Debug APK exists | `test -f app/build/outputs/apk/debug/app-debug.apk` | PASS |
| No `com.dictus` packages | `grep -rn "^package com.dictus" --include='*.kt' app ime core asr whisper` | PASS (0 hits) |
| No `dictus` in kts/xml | `grep -rn "dictus" --include='*.kts' --include='*.xml' . \| grep -v third_party \| grep -v LICENSE \| grep -v NOTICE \| grep -v docs/adr/0001 \| grep -v /build/` | PASS (0 hits) |
| `applicationId` | `grep applicationId app/build.gradle.kts` | `com.gallopkeyboard.ime` |
| `rootProject.name` | `grep rootProject.name settings.gradle.kts` | `gallopkeyboard-android` |
| `NOTICE` | Dictus + whisper.cpp attribution | PASS |
| `README.md` | `assembleDebug`, `Galaxy S22`, `AGENTS.md` | PASS |
| `docs/dictus-inventory.md` | Sherpa-ONNX / Parakeet documented as **present** | PASS |
| `plans/README.md` Plan 002 | Status `DONE` | PASS |
| `AGENTS.md` build table | `assembleDebug` present; no `TODO(plan-003)` on Build & verify | PASS |
| Package sweep | All `package` lines under modules use `com.gallopkeyboard.*` | PASS |
| Clean git tree | `git status --porcelain` | Only `?? package-lock.json` (not a Gradle artifact; ignored for verdict) |

## Observations (non-blocking)

- **UI copy** in `app/src/main/res/values/strings.xml` still displays the word **Dictus** in several onboarding/settings strings (capital D). Plan done-criteria only require lowercase `dictus` absent from `*.kts` / `*.xml`; rebranding user-visible copy may be a follow-up outside Plan 002 scope.
- **Kotlin identifiers** retain upstream names where intentional (`dictus_prefs` DataStore, `dictus_recording` channel, `dictus.log` export paths, GitHub URLs in licences). These are not covered by the kts/xml sweep and do not affect build or package rename.
- **Robolectric** uses `application=com.gallopkeyboard.DictusApplication` in test resources (documented in code summary).

## Blockers

None.

## Advance

Tests passed → `npm run devteam:advance -- job-002 --to reviewing`
