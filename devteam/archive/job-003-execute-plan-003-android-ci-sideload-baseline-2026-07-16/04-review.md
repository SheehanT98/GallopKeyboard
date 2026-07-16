# Job 003 — Review

| Field | Value |
|-------|-------|
| **Job** | job-003 |
| **Branch** | `cursor/devteam-job-003-execute-plan-003-android-ci-sideload-baseline-c1fc` |
| **PR** | https://github.com/SheehanT98/GallopKeyboard/pull/12 (OPEN) |
| **Base** | `origin/main` |
| **Reviewed at tip** | `149015d` |
| **Verdict** | **approve** |

## Summary

Plan 003 done criteria are met: CI workflow, `scripts/verify.sh`, sideload doc, README/AGENTS links, and plans index `DONE`. Tester report is **PASS** (`bash scripts/verify.sh` → OK). Lint baselines were refreshed so `./gradlew lint` is green — justified for CI, no Kotlin/feature work. **Approve** for double-check / human merge.

## Scope compliance

| Area | Status |
|------|--------|
| `.github/workflows/ci.yml` — assembleDebug + testAll + lint + APK artifact | Matches plan template (JDK 17, setup-android@v3) |
| `scripts/verify.sh` executable + Dictus/model grep guards | Present; mirrors CI |
| `docs/sideload-galaxy-s22.md` (prereqs → install → enable → mic → reinstall → troubleshoot → NOT) | 62 lines; required sections present |
| `README.md` link to sideload doc | Present |
| `AGENTS.md` — `bash scripts/verify.sh` + CI subsection; `TODO(plan-003)` removed | Present |
| `plans/README.md` Plan 003 → DONE | Present |
| Kotlin / Java / resources / signing / formatter / Play Store | Not touched (out of scope) |
| `ime/lint-baseline.xml`, `app/lint-baseline.xml` | Extra vs plan file list — required so CI `lint` passes (stale paths after package rename); no source changes |

## Verification evidence

Spot-checked on review branch; full Gradle re-run deferred to tester artifact:

| Criterion | Result |
|-----------|--------|
| Tester: `bash scripts/verify.sh` | PASS (`03-test-report.md`) — assembleDebug + testAll + lint + guards |
| CI YAML parse | Valid (`yaml.safe_load`) |
| `grep -c gradlew` in `ci.yml` | 3 |
| `test -x scripts/verify.sh` | true |
| Sideload doc | Exists, 62 lines |
| README / AGENTS / plans index | Done-criteria greps true; Plan 003 = DONE |
| Debug APK path (from tester) | Present after verify (~156 MB) |
| PR #12 | OPEN (do not recreate) |

## Risks for the human reviewer

1. **GitHub Actions first green run**: At review time PR #12 CI `build` was still **QUEUED**. Local `verify.sh` passed; confirm the workflow completes and uploads `app-debug-apk` before merge.
2. **Lint baseline refresh**: Baselines updated (path dots→slashes + regenerated entries). Acceptable for green lint; future real lint fixes should shrink baselines rather than grow them.
3. **`act` not run**: Plan allows `verify.sh` as substitute — fine for this environment.
4. **Untracked `package-lock.json`**: Local npm artifact only; not part of this job's deliverables.

## Blockers

None.

## Advance

`npm run devteam:advance -- job-003 --to double_checking`
