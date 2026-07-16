# Devteam

Job-based pipeline for GallopKeyboard: no active-job cap; jobs queue only for dependency or planned-file conflicts. Human verbs: **approve**, **revise**, **cancel**.

## Dashboard

**Active agent jobs:** 1
**Queued:** 0
**Awaiting your review:** 0
| Job | Status | Feature | Branch | PR | Review |
|-----|--------|---------|--------|-----|--------|
| job-010 | conflict_hold | Execute plan 010: Hardening battery crashes release | `cursor/devteam-job-010-execute-plan-010-hardening-battery-crashes-relea-c1fc` | — | — |
| job-009 | conflict_hold | Execute plan 009: Keyboard polish clipboard + emoji | `cursor/devteam-job-009-execute-plan-009-keyboard-polish-clipboard-emoji-c1fc` | — | — |
| job-008 | conflict_hold | Execute plan 008: Model download UX | `cursor/devteam-job-008-execute-plan-008-model-download-ux-c1fc` | — | — |
| job-007 | conflict_hold | Execute plan 007: Whisper polish pass | `cursor/devteam-job-007-execute-plan-007-whisper-polish-pass-c1fc` | — | — |
| job-006 | conflict_hold | Execute plan 006: Parakeet streaming pass | `cursor/devteam-job-006-execute-plan-006-parakeet-streaming-pass-c1fc` | — | — |
| job-005 | coding | Execute plan 005: Smart button + AudioRecorder | `cursor/devteam-job-005-execute-plan-005-smart-button-audiorecorder-c1fc` | — | — |

## Ready for review

_No jobs awaiting review._

## Commands

| Command | Action |
|---------|--------|
| `/devteam <task>` | Submit full job (plan → code → test → review → double_check) |
| `/devteamquick <plan path>` | Submit quick job (skip plan) |
| `/devteam status` | Show dashboard |
| `/devteam show <job-id>` | Walkthrough artifacts |
| `/devteam approve <job-id>` | Merge when CI green |
| `/devteam revise <job-id> <notes>` | Send back for revision |
| `/devteam cancel <job-id>` | Abandon job |
| `npm run devteam:sync -- <job-id>` | Merge `main` into an open PR branch (fixes README conflicts) |

## Stacked PRs

When plan B depends on plan A:

1. Submit B with `--depends-on job-XXX` so B stays on `conflict_hold` until A merges.
2. Branch each job from `origin/main` at code time — never stack feature branches against `main`.
3. After approving A, run `npm run devteam:sync -- <job-id-B>` (or `--all-open`) before merging B.

`devteam/README.md` is auto-generated on every stage — expect trivial conflicts if branches are stacked without sync.

_Auto-updated by devteam scripts._
