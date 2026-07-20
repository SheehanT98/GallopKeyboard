# Devteam

Job-based pipeline for GallopKeyboard: no active-job cap; jobs queue only for dependency or planned-file conflicts. Human verbs: **approve**, **revise**, **cancel**.

## Dashboard

**Active agent jobs:** 0
**Queued:** 0
**Awaiting your review:** 8
| Job | Status | Feature | Branch | PR | Review |
|-----|--------|---------|--------|-----|--------|
| job-020 | awaiting_review | Execute plan 029 finish privacy backup and log scrub | `cursor/devteam-job-020-execute-plan-029-finish-privacy-backup-and-log-s-c1fc` | #48 | [04-review.md](jobs/job-020/04-review.md) |
| job-019 | awaiting_review | Execute plan 017 fix accent popup commit and geometry | `cursor/devteam-job-019-execute-plan-017-fix-accent-popup-commit-and-geo-c1fc` | #42 | [04-review.md](jobs/job-019/04-review.md) |
| job-018 | awaiting_review | Execute plan 016 voice dispose cancel and unify mic | `cursor/devteam-job-018-execute-plan-016-voice-dispose-cancel-and-unify--c1fc` | #41 | [04-review.md](jobs/job-018/04-review.md) |
| job-017 | awaiting_review | Execute plan 014 offload streaming ASR from IME main thread | `cursor/devteam-job-017-execute-plan-014-offload-streaming-asr-from-ime--c1fc` | #40 | [04-review.md](jobs/job-017/04-review.md) |
| job-016 | awaiting_review | Execute plan 015 defer IME model SHA verify | `cursor/devteam-job-016-execute-plan-015-defer-ime-model-sha-verify-c1fc` | #39 | [04-review.md](jobs/job-016/04-review.md) |
| job-014 | awaiting_review | Execute plan 018 reconcile AGENTS docs | `cursor/devteam-job-014-execute-plan-018-reconcile-agents-docs-c1fc` | #38 | [04-review.md](jobs/job-014/04-review.md) |
| job-013 | awaiting_review | Execute plan 013 swipe typing | `cursor/devteam-job-013-execute-plan-013-swipe-typing-c1fc` | #34 | [04-review.md](jobs/job-013/04-review.md) |
| job-012 | awaiting_review | Execute plan 012 clipboard pins | `cursor/devteam-job-012-execute-plan-012-clipboard-pins-c1fc` | #33 | [04-review.md](jobs/job-012/04-review.md) |

## Ready for review

- **job-012** — Execute plan 012 clipboard pins · PR 33
- **job-013** — Execute plan 013 swipe typing · PR 34
- **job-014** — Execute plan 018 reconcile AGENTS docs · PR 38
- **job-016** — Execute plan 015 defer IME model SHA verify · PR 39
- **job-017** — Execute plan 014 offload streaming ASR from IME main thread · PR 40
- **job-018** — Execute plan 016 voice dispose cancel and unify mic · PR 41
- **job-019** — Execute plan 017 fix accent popup commit and geometry · PR 42
- **job-020** — Execute plan 029 finish privacy backup and log scrub · PR 48

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
