# Code summary — job-016 (Plan 015)

## Summary

Moved daily model SHA-256 verify off the IME `onCreate` critical path. The cheap
`areFilesPresent` check remains synchronous for immediate banner display; full
integrity verify runs on `bindingScope` + `Dispatchers.IO` and posts corrupt
banner updates back to Main.

## Files changed

| Path | Change |
|------|--------|
| `ime/src/main/java/com/gallopkeyboard/ime/DictusImeService.kt` | Defer `verifyInstalledIfDue()` to `bindingScope.launch(Dispatchers.IO)`; `withContext(Main)` for corrupt banner |
| `core/src/main/java/com/gallopkeyboard/core/models/ModelInstaller.kt` | KDoc: daily verify is background-scheduled from IME startup |
| `core/src/test/java/com/gallopkeyboard/core/models/ModelInstallerTest.kt` | **New** — skips re-verify when recently stamped |
| `docs/dictus-inventory.md` | Plan 015 additions section |
| `plans/README.md` | Plan 015 status → DONE |

## Verification

| Command | Result |
|---------|--------|
| `git diff --stat 3571aab..HEAD` (drift check) | No in-scope drift |
| `rg verifyInstalledIfDue ime/src/main` | Only inside `bindingScope.launch` |
| `./gradlew :ime:testDebugUnitTest :core:testDebugUnitTest` | BUILD SUCCESSFUL |
| `bash scripts/verify.sh` | OK |

## STOP conditions

None encountered. `bindingScope` lives for service lifetime (`onDestroy` cancels).
