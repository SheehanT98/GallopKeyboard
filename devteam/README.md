# Devteam

Job-based pipeline for GallopKeyboard: up to **3** active agent jobs, rest queued. Human verbs: **approve**, **revise**, **cancel**.

Execution targets live in [`plans/`](../plans/README.md) (Plans 001–010). Phase bundling and dependencies are in [`PHASE-ORCHESTRATION.md`](./PHASE-ORCHESTRATION.md).

## Dashboard

**Active agent jobs:** 0/3  
**Queued:** 0  
**Awaiting your review:** 0

| Job | Status | Feature | Branch | PR | Review |
|-----|--------|---------|--------|-----|--------|
| — | — | — | — | — | — |

## Ready for review

_None._

## Commands

| Command | Action |
|---------|--------|
| `/devteam <task>` | Submit full job (plan → code → test → review → double_checking) |
| `/devteamquick <plan path>` | Submit quick job (skip plan — use for `plans/NNN-*.md`) |
| `/devteam status` | Show dashboard |
| `/devteam show <job-id>` | Walkthrough artifacts |
| `/devteam approve <job-id>` | Merge when CI green |
| `/devteam revise <job-id> <notes>` | Send back for revision |
| `/devteam cancel <job-id>` | Abandon job (artifacts move to [`archive/`](./archive/README.md)) |
| `npm run devteam:sync -- <job-id>` | Merge `main` into an open PR branch (fixes README conflicts) |

> **Note:** `npm run devteam:*` scripts require a root `package.json` with devteam script entries (see gallopCRM for reference implementations). Until those land, run stages manually using the orchestrator prompt in [`PHASE-ORCHESTRATION.md`](./PHASE-ORCHESTRATION.md).

## Stacked PRs

When plan B depends on plan A:

1. Submit B with `--depends-on job-XXX` so B stays on `conflict_hold` until A merges.
2. Branch each job from `origin/main` at code time — never stack feature branches against `main`.
3. After approving A, run `npm run devteam:sync -- <job-id-B>` (or `--all-open`) before merging B.

`devteam/README.md` is auto-generated on every stage — expect trivial conflicts if branches are stacked without sync.

## Archive

Completed, approved, and cancelled jobs are moved to [`archive/jobs/`](./archive/README.md). The archive folder holds workflow history only — do not copy job artifacts from other projects.

_Auto-updated by devteam scripts._
