# Job 021 — Review

| Field | Value |
|-------|-------|
| **Job** | job-021 |
| **Branch** | `cursor/devteam-job-021-execute-plan-031-stop-swipe-recomposition-jank-c1fc` |
| **PR** | [#50](https://github.com/SheehanT98/GallopKeyboard/pull/50) |
| **Plan** | `plans/031-stop-swipe-recomposition-jank.md` |
| **Reviewer** | devteam-reviewer (cursor-grok-4.5-high) |
| **Reviewed at** | 2026-07-20T22:19:01Z |
| **SHA reviewed** | `a230654` |
| **Base** | `origin/main...HEAD` |
| **Verdict** | **APPROVE** |

## Summary

Plan 031 removes `gestureTick` MOVE-rate full-keyboard invalidation and drives
swipe/accent UI from discrete `GestureUiState` that writes Compose state only
when membership or accent selection changes. Product scope is tight; tester
`verify.sh` PASS. **APPROVE**.

## Scope compliance

| In / out of scope | Result |
|-------------------|--------|
| `KeyboardView` — delete `gestureTick` / `recompositionTrigger`; sync discrete UI | **Met** |
| `KeyRow` / `KeyButton` — per-key highlight/press/accent props | **Met** — `KeyRow` takes `GestureUiState`; `KeyButton` already had per-key flags (no edit needed) |
| `SwipeTypingController` — snapshot for Compose | **Met** — `snapshotGestureUi()` |
| `GestureUiState` + equality skip helper + unit tests | **Met** |
| `docs/dictus-inventory.md` Plan 031 | **Met** |
| `plans/README.md` — 031 DONE; 021 SUPERSEDED → 031 | **Met** |
| Bounds rebuild not on every MOVE | **Met** — `refreshKeyBounds()` from `onCharacterBoundsChanged` only |
| SwipeWordResolver / dwell (032), spatial index rewrite, accent redesign | **Untouched** (correct) |

Product files vs `origin/main` (excl. job artifacts): `GestureUiState.kt`,
`KeyboardView.kt`, `KeyRow.kt`, `SwipeTypingController.kt`,
`GestureUiStateTest.kt`, `docs/dictus-inventory.md`, `plans/README.md`.

## Done criteria

| Criterion | Result |
|-----------|--------|
| Zero `gestureTick` in `ime/src/main` | **Pass** — `rg` clean |
| Highlights update only on membership change | **Pass** — `gestureUiStateIfChanged` + `GestureUiStateTest` equality skip; MOVE calls `syncGestureUi()` but skips assign when equal |
| Accent popup still works | **Pass** — `accentPopupKey` / `highlightedAccentIndex` in snapshot; index passed only to popup key; controller accent tests green per tester |
| Swipe tests + `verify.sh` OK | **Pass** — `03-test-report.md` |
| Scope respected | **Pass** |

## Implementation notes

- Pointer path still snapshots every MOVE (cheap alloc + `==`); Compose invalidation
  only when the data class differs — correct fix for the Plan 021 failure mode.
- Accent drag invalidates via `highlightedAccentIndex` changes, not a tick side
  channel; non-popup keys get `highlightedAccentIndexOverride = null` so they
  can skip when only the accent cell highlight moves.
- Drift anchor `32b0d20` absent from history (coder/tester); not a product defect.

## Verification evidence

| Check | Source | Result |
|-------|--------|--------|
| No `gestureTick` | reviewer `rg` + `03-test-report.md` | No matches |
| `*Swipe*` / `*GestureUi*` / full `:ime:testDebugUnitTest` | `03-test-report.md` @ `57084f4` | BUILD SUCCESSFUL |
| `bash scripts/verify.sh` | same | exit 0, `OK` |
| PR CI | GitHub Actions on #50 | **IN_PROGRESS** at review time (not red) |

## Risks for the human reviewer

1. **S22 feel** — Plan maintenance asks for FrameTimeline / long-swipe feel on
   Galaxy S22. Unit tests prove equality-skip; device smoothness is still a
   manual check.
2. **Layout-time bounds churn** — `refreshKeyBounds()` runs per key
   `onCharacterBoundsChanged` (N list rebuilds at layout). Better than
   MOVE-rate composition, but worth a glance if hit-test feels stale mid-swipe.
3. **CI not green yet** at review time — approve on local verify evidence; human
   merge should wait for CI green.

## AGENTS.md

No cloud STT/telemetry; English-only; package/`applicationId` unchanged; swipe
typing retained and only its Compose invalidation path changed.
