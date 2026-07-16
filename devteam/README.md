# Devteam

Job-based pipeline for GallopKeyboard: up to **3** active agent jobs, rest queued. Human verbs: **approve**, **revise**, **cancel**.

Plans: [`plans/`](../plans/README.md). Orchestration: [`.agents/skills/devteam/SKILL.md`](../.agents/skills/devteam/SKILL.md).

## Dashboard

**Active agent jobs:** 0/3
**Queued:** 0
**Awaiting your review:** 0
| Job | Status | Feature | Branch | PR | Review |
|-----|--------|---------|--------|-----|--------|
| — | *No active jobs* | — | — | — | — |

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
