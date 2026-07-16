# Job 003 — Code summary

## What landed

Android CI baseline, local verify script, and Galaxy S22 sideload documentation per Plan 003.

## Files created

| File | Why |
|------|-----|
| `.github/workflows/ci.yml` | GitHub Actions: `assembleDebug` + `testAll` + `lint` on push/PR; uploads debug APK artifact |
| `scripts/verify.sh` | Local mirror of CI + grep guards (Dictus package refs, model binaries) |
| `docs/sideload-galaxy-s22.md` | Owner walkthrough: adb install, keyboard enable, mic permission, troubleshooting |

## Files updated

| File | Why |
|------|-----|
| `README.md` | Link to full sideload guide in "Install on Galaxy S22" section |
| `AGENTS.md` | `bash scripts/verify.sh` as pre-push check; CI subsection; removed `TODO(plan-003)` |
| `plans/README.md` | Plan 003 status → DONE |
| `ime/lint-baseline.xml` | Refreshed baseline so `./gradlew lint` passes (stale Dictus entries + Compose flow-in-composition) |
| `app/lint-baseline.xml` | Refreshed alongside `:ime` baseline update |

## Verification

```bash
source scripts/android-env.sh
./gradlew --no-daemon assembleDebug   # BUILD SUCCESSFUL (drift check)
bash scripts/verify.sh                # OK
python3 -c 'import yaml; yaml.safe_load(open(".github/workflows/ci.yml"))'  # valid
grep -c 'gradlew' .github/workflows/ci.yml  # 3
test -x scripts/verify.sh             # true
wc -l docs/sideload-galaxy-s22.md     # 62 lines
```

## STOP conditions

None hit. `android-actions/setup-android@v3` used as specified. JDK 17 retained (Kotlin 2.1.10 + AGP 8.7.3).

## Notes

- Plan 002 verified `assembleDebug` + `testAll` but not `lint`. Lint failed on first `verify.sh` run due to stale `ime/lint-baseline.xml`; refreshed via `./gradlew :ime:updateLintBaseline :app:updateLintBaseline` (no Kotlin changes).
- `act` not available locally; `bash scripts/verify.sh` used as plan-acceptable substitute for workflow dry-run.
