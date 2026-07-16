# Phased devteam orchestration — GallopKeyboard

One **phase** = one **devteam job** = one **PR**. Sub-plans inside a phase run **in order on a single branch**.

Plans live in [`plans/`](../plans/README.md) (001–010). This file maps them to devteam phases for queued execution.

## Quick start

```bash
# After plans/ exist on your branch:
# node scripts/generate-phase-plans.cjs    # optional: refresh phase-*.md from manifest
npm run devteam:queue-phases
npm run devteam:status -- --fetch
```

Job → phase mapping is written to `devteam/phase-queue.json` after queueing.

## Phase map (9 PRs for Plans 001–010)

| Phase | Plans bundled | Waits for | HANDOFF phase |
|-------|---------------|-----------|---------------|
| 01 | 001 | — | 0 — bootstrap |
| 02 | 002 | phase-01 | 0 — bootstrap |
| 03 | 003 | phase-02 | 0 — bootstrap |
| 04 | 004 | phase-03 | 1 — panel toggle |
| 05 | 005 | phase-04 | 2 — smart button + recording |
| 06 | 006, 007 | phase-05 | 3 — hybrid STT |
| 07 | 008 | phase-06 | 3 — hybrid STT (models) |
| 08 | 009 | phase-07 | 4 — keyboard polish |
| 09 | 010 | phase-08 | 5 — hardening |

### Quick jobs (skip plan stage)

For executor-ready plans under `plans/`, prefer:

```text
/devteamquick plans/001-repo-hygiene-and-agents-md.md
/devteamquick plans/002-fork-dictus-into-repo.md
…
```

Each plan file is self-contained (verification commands, scope, STOP conditions).

## Multitask model (3 agent slots)

Devteam supports:

1. **Submit all phases** — `devteam:queue-phases` registers every job with `--depends-on` edges.
2. **Auto-wait** — jobs sit in `conflict_hold` until dependency PRs **merge** (not just approve).
3. **Auto-promote** — when you `/devteam approve job-XXX`, dependents release and enter `coding` if a slot is free.
4. **Parallel when safe** — up to **3** jobs in `coding`/`testing`/etc. at once when:
   - dependencies are satisfied, and
   - `plannedFiles` do not overlap (`npm run devteam:conflicts`).

For GallopKeyboard v1, phases are **mostly sequential** (each phase depends on the previous). Parallelism opens up only if you split optional work (e.g. docs-only jobs) with non-overlapping `plannedFiles`.

## Per-job pipeline (each Cloud Agent / chat)

For each job in `coding` status:

```
code (devteam-coder) → open-pr → test → review → double-check → awaiting_review
```

**Human batch approve**: when several jobs hit `awaiting_review`, run:

```
/devteam approve job-001
/devteam approve job-002
…
```

Then `npm run devteam:sync -- --all-open` before merging the next wave if README conflicts appear.

On **approve** or **cancel**, job artifacts move to [`archive/jobs/`](./archive/README.md) per the archive workflow.

## Orchestrator prompt (copy to worker agents)

> You are the devteam orchestrator for **GallopKeyboard**.
> Read `devteam/phase-queue.json` and `devteam/PHASE-ORCHESTRATION.md`.
> Read `HANDOFF.md`, `CONTEXT.md`, and `AGENTS.md` before coding.
> For every job in `coding` status: run the full quick pipeline to `awaiting_review`.
> Do not wait for human approve between jobs unless the job is not yet promoted.
> Max 3 active jobs globally — if capped, poll `npm run devteam:status -- --fetch` until a slot opens.
> Use model slugs from each job's `meta.json` for every stage Task launch (see `devteam/MODEL-POLICY.md`).
> Execute plans with `bash scripts/verify.sh` as the gate before opening PRs (once Plan 003 lands).

## Files that often conflict

See `devteam/hot-files.json` — especially `plans/README.md`, `devteam/README.md`, `docs/dictus-inventory.md`. Use `devteam:sync` after each merge.

## Cancelling superseded work

If a phase is re-queued with a broader plan bundle, cancel the old job:

```bash
npm run devteam:queue-phases -- --cancel-job-NNN
```

Cancelled jobs are archived per [`archive/README.md`](./archive/README.md).

## Script wiring (TODO)

gallopCRM ships `scripts/` + `package.json` entries for `devteam:*` commands. This repo is Gradle-first until Plan 002 lands. Copy or port those scripts when adding root `package.json`, or run stages manually via the orchestrator prompt above.
