# Job 002 — Review

| Field | Value |
|-------|-------|
| **Job** | job-002 |
| **Branch** | `cursor/devteam-job-002-execute-plan-002-fork-dictus-into-repo-c1fc` |
| **PR** | https://github.com/SheehanT98/GallopKeyboard/pull/10 (OPEN) |
| **Base** | `origin/main` @ `05ee6f5` |
| **Reviewed at tip** | `6d28aa7` |
| **Verdict** | **approve** |

## Summary

Plan 002 done criteria are met: Dictus is imported at `4f5d24821d07`, packages renamed to `com.gallopkeyboard.*`, Gradle/`applicationId`/IME label rebranded, attribution and docs in place, and `./gradlew clean assembleDebug testAll` passed (tester report + APK present). No scope creep into Plans 004+. **Approve** for double-check / human merge.

## Scope compliance

| Area | Status |
|------|--------|
| Import modules `:app :ime :core :whisper :asr` + whisper.cpp submodule | In scope — present |
| Package rename → `com.gallopkeyboard` (upstream root was `dev.pivisolutions.dictus`) | Done; no `com.dictus` packages left |
| `applicationId` / `rootProject.name` / `GallopKeyboard` labels | Matches ADR-0004 |
| Exclude PRD, `.planning`, `design`, upstream `.github`, etc. | Absent from tree |
| English-only: no source `values-fr/` | Only `values/` under app/ime/core |
| `NOTICE`, README build/sideload, `docs/dictus-inventory.md`, Plan 002 → DONE | Present |
| Feature work (voice panel, smart button, streaming) | Not added |

Out of scope leftovers (identifiers / UI copy) are noted under Risks — they do not fail machine-checkable done criteria.

## Verification evidence

Re-checked on review branch (JDK 17, `ANDROID_HOME=/opt/android-sdk`):

| Criterion | Result |
|-----------|--------|
| Tester: `./gradlew --no-daemon clean assembleDebug testAll` | PASS (`03-test-report.md`) |
| `app/build/outputs/apk/debug/app-debug.apk` | Present (~156 MB) |
| `^package com.dictus` in `*.kt` modules | 0 hits |
| lowercase `dictus` in `*.kts` / `*.xml` (excl. third_party, LICENSE, NOTICE, adr/0001, build) | 0 hits |
| `applicationId` = `com.gallopkeyboard.ime` | OK |
| `rootProject.name` = `gallopkeyboard-android` | OK |
| `NOTICE` names Dictus + whisper.cpp | OK |
| README: `assembleDebug`, Galaxy S22 | OK |
| `docs/dictus-inventory.md` documents sherpa-onnx/Parakeet as **present** | OK |
| `plans/README.md` Plan 002 = `DONE` | OK |
| Upstream exclusions (PRD, design, …) | Absent |
| PR #10 | OPEN |

## Risks for the human reviewer

1. **User-visible “Dictus” copy** (non-blocking): several onboarding/settings strings in `app/src/main/res/values/strings.xml` still say “Dictus” (capital D). Done-criteria grep is case-sensitive lowercase on kts/xml; system `app_name` / `ime_name` / `method.xml` label are already `GallopKeyboard`. Follow-up polish, not a Plan 002 blocker.
2. **Kotlin / asset names**: `DictusImeService`, `DictusApplication`, `DictusTheme`, `ic_dictus_logo.png`, prefs keys like `dictus_prefs` — intentional upstream keep; rename later if desired.
3. **Large binary payload**: sherpa-onnx JNI `.so` + debug APK ~156 MB — expected for this import; watch clone/CI size.
4. **Inventory vs original plan guess**: Parakeet/sherpa-onnx **is** in-tree (Plan 006 still owns streaming wiring). Inventory is correct; do not re-add the engine in 006.
5. **Untracked `package-lock.json`**: local npm artifact only; not a Gradle build leak (`.gradle/` / `build/` / `local.properties` ignored).

## Blockers

None.

## Advance

`npm run devteam:advance -- job-002 --to double_checking`
