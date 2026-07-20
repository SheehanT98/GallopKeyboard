# Job 020 — Review

| Field | Value |
|-------|-------|
| **Job** | job-020 |
| **Branch** | `cursor/devteam-job-020-execute-plan-030-wire-suggestion-bar-english-def-c1fc` |
| **PR** | [#49](https://github.com/SheehanT98/GallopKeyboard/pull/49) |
| **Plan** | `plans/030-wire-suggestion-bar-english-defaults.md` |
| **Reviewer** | devteam-reviewer (cursor-grok-4.5-high) |
| **Reviewed at** | 2026-07-20T22:10:00Z |
| **SHA reviewed** | `079aab2` |
| **Base** | `origin/main...HEAD` |
| **Verdict** | **APPROVE** |

## Summary

Plan 030 finishes incomplete Plan 019: SuggestionBar is wired into `KeyboardScreen`, IME/Settings suggestion defaults agree (`true`), and dictionary asset selection defaults to English for missing/`auto`/unknown. Product diff is scoped; tester `verify.sh` PASS. **APPROVE**.

## Scope compliance

| In / out of scope | Result |
|-------------------|--------|
| `DictionaryEngine` — `dictionaryAssetForLanguage`; English for null/`auto` | **Met** |
| `DictusImeService` — `SUGGESTIONS_ENABLED ?: true`; pass flows; commit handlers | **Met** |
| `KeyboardScreen` — render `SuggestionBar` when enabled | **Met** |
| Optional `recordWordTyped` on space / suggestion commit | **Met** (included) |
| Unit tests for language→asset mapping | **Met** |
| `docs/dictus-inventory.md` Plan 030 | **Met** |
| `plans/README.md` — 030 DONE; 019 SUPERSEDED → 030 | **Met** |
| SwipeWordResolver (032) / autocorrect default-ON / SuggestionBar redesign | **Untouched** (correct) |

Product files vs `origin/main` (excl. job artifacts): `DictusImeService.kt`, `KeyboardScreen.kt`, `DictionaryEngine.kt`, `DictionaryEngineTest.kt`, `docs/dictus-inventory.md`, `plans/README.md`.

## Done criteria

| Criterion | Result |
|-----------|--------|
| SuggestionBar visible when pref on; hidden when off | **Pass** — gated by `suggestionsEnabled` from DataStore flow |
| IME and Settings default agree (`true`) | **Pass** — both `?: true`; IME `MutableStateFlow` init `true` |
| Missing/`auto` language loads `dict_en.txt` | **Pass** — helper + table tests (null/auto/en/unknown → en; fr → fr) |
| Suggestion tap replaces current word + space | **Pass** — `deleteSurroundingText` then `commitText("$word ")`; clears `lastAutoCorrect` |
| `verify.sh` OK; scope respected | **Pass** — tester evidence; no out-of-scope churn |

## Implementation notes

- Layout order: `MicButtonRow` → `SuggestionBar` (when on) → `ClipboardStrip` → `KeyboardView`, matching Gboard-style bar-above-keys.
- `commitCurrentWord` commits a trailing space on the already-typed fragment (left slot) — correct for echo-slot UX.
- `KeyboardScreen` param default `suggestionsEnabled = false` is preview-safe; production path always passes the collected IME value (default true).

## Verification evidence

| Check | Source | Result |
|-------|--------|--------|
| Focused Dictionary/Suggestion tests | `03-test-report.md` @ `6f34958` | BUILD SUCCESSFUL |
| `:ime:testDebugUnitTest` | same | BUILD SUCCESSFUL |
| `bash scripts/verify.sh` | same | exit 0, `OK` |
| PR CI | GitHub Actions on #49 | **IN_PROGRESS** at review time (not red) |

Drift anchor `32b0d20` absent from history (noted by coder/tester); Phase 9 surfaces present on branch — not a product defect.

## Risks for the human reviewer

1. **S22 height budget** — `SuggestionBar` (36.dp) sits inside the fixed `310.dp` column with optional `ClipboardStrip` (32.dp), so keys compress rather than growing IME host height. Plan maintenance note: confirm usable hit targets with suggestions ON + clipboard strip populated.
2. **No Compose UI test** for bar show/hide — wiring covered by code review + unit tests; manual check (Settings → type `hel` → tap center) still worthwhile on device.
3. **CI not green yet** at review time — approve on local verify evidence; human merge should wait for CI green.

## AGENTS.md

No cloud STT/telemetry; English-only defaults reinforced; package/`applicationId` unchanged; swipe typing untouched.
