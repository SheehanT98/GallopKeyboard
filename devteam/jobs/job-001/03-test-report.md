# Test report — job-001

**Job:** Plan 001 — repo hygiene, AGENTS.md, and supporting ADRs  
**Branch:** `cursor/devteam-job-001-execute-plan-001-repo-hygiene-agents-md-adrs-c1fc`  
**Tester:** devteam-tester  
**Date:** 2026-07-16  
**Verdict:** **PASS**

## Summary

Re-ran all step-level and final-sweep verification commands from `devteam/jobs/job-001/01-plan.md` on the job branch. Every machine-checkable done criterion passed. No Android source files present. Plan 001 scope artifacts are committed on the branch.

## Commands and results

### Step 1 — LICENSE

| Command | Expected | Result |
|---------|----------|--------|
| `head -3 LICENSE` | `MIT License`, blank line, GallopKeyboard copyright | **PASS** |
| `wc -l LICENSE` | ~21 lines | **PASS** (22 lines) |
| `head -1 LICENSE` | `MIT License` | **PASS** |

### Step 2 — `.gitignore`

| Command | Expected | Result |
|---------|----------|--------|
| `grep -c '^' .gitignore` | ~40 lines | **PASS** (49 lines) |
| `git check-ignore build/foo` | prints `build/foo` | **PASS** |

### Step 3 — `AGENTS.md`

| Command | Expected | Result |
|---------|----------|--------|
| `test -f AGENTS.md && wc -l AGENTS.md` | exists, ≥60 lines | **PASS** (67 lines) |
| `grep -q "Do NOT" AGENTS.md && grep -q "assembleDebug" AGENTS.md && echo OK` | `OK` | **PASS** |

### Step 4 — `CLAUDE.md`

| Command | Expected | Result |
|---------|----------|--------|
| `cat CLAUDE.md \| wc -l` | 3 lines | **PASS** |

### Step 5 — ADR-0003

| Command | Expected | Result |
|---------|----------|--------|
| `test -f docs/adr/0003-smart-button-gesture-spec.md && grep -q "400 ms" ... && grep -q "48 dp" ... && echo OK` | `OK` | **PASS** |

### Step 6 — ADR-0004

| Command | Expected | Result |
|---------|----------|--------|
| `test -f docs/adr/0004-package-naming-and-application-id.md && grep -q "com.gallopkeyboard.ime" ... && echo OK` | `OK` | **PASS** |

### Step 7 — `plans/README.md`

| Command | Expected | Result |
|---------|----------|--------|
| `grep -E "^\| 001 " plans/README.md` | row ends with `\| DONE \|` | **PASS** |

### Final sweep (test plan)

| Command | Expected | Result |
|---------|----------|--------|
| File-existence loop for all six artifacts | `all present` | **PASS** |
| `find . -type f \( -name "*.kt" -o -name "*.java" -o -name "*.gradle*" \) -not -path "./.agents/*" -not -path "./.git/*" \| wc -l` | `0` | **PASS** |

## Done criteria checklist

- [x] `LICENSE`, `.gitignore`, `AGENTS.md`, `CLAUDE.md` exist at repo root
- [x] ADR-0003 exists and contains `400 ms` and `48 dp`
- [x] ADR-0004 exists and contains `com.gallopkeyboard.ime`
- [x] `head -1 LICENSE` prints `MIT License`
- [x] Branch diff vs `main` contains only in-scope files (plus devteam job artifacts)
- [x] `plans/README.md` row for Plan 001 shows status `DONE`

## Observations (non-blocking)

- **Drift since plan SHA `99ca844`:** `README.md` and `docs/android-toolchain.md` also changed on this branch. Documented in `02-code-summary.md`; no STOP conditions triggered (HANDOFF/CONTEXT/existing ADRs unchanged; no Android source).
- **Working tree:** untracked `package-lock.json` present locally; not part of Plan 001 scope and not committed on the job branch.

## Blockers

None.
