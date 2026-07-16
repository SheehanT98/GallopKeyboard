---
name: devteam
description: GallopKeyboard job-based agent pipeline — submit, orchestrate, and review devteam jobs. Use for /devteam, /devteamquick, /devteam status, approve, revise, cancel. Reads devteam/MODEL-POLICY.md and model-presets.json; launches stage subagents with explicit model slugs.
license: MIT
metadata:
  author: GallopKeyboard
  version: "1.0.0"
---

# Devteam

Orchestrate multi-stage agent jobs for GallopKeyboard. Up to **3** active jobs; human verbs: **approve**, **revise**, **cancel**.

## Hard rules

1. **Read models before every stage** — `npm run devteam:models -- --job <job-id>` or `devteam/jobs/<job-id>/meta.json`. Pass the exact `model` slug on every Task/subagent launch. See `devteam/MODEL-POLICY.md`.
2. **Never do stage work on the orchestrator model** — launch `devteam-planner`, `devteam-coder`, `devteam-tester`, `devteam-reviewer`, or `devteam-double-checker` (see `.cursor/agents/`).
3. **Never skip advance gates** — after each stage artifact exists, run the matching `npm run devteam:advance` command.
4. **Base branch is `main`** — PRs and sync target `origin/main`.

## Invocation

### `/devteam <task description>`

Full pipeline (includes planning stage):

```bash
npm run devteam:submit -- "<task description>"
```

Then run stages in order until `awaiting_review`:

```
planning → coding → testing → reviewing → double_checking → awaiting_review
```

Launch subagents per stage; after each, `npm run devteam:advance -- <job-id> --to <next-stage>`.

### `/devteamquick <plan path>`

Skip planning — use an existing plan under `plans/`:

```bash
npm run devteam:submit -- "Execute plan 001" --quick --plan plans/001-repo-hygiene-and-agents-md.md
```

Then: **coding → testing → reviewing → double_checking → awaiting_review**.

### `/devteam status`

```bash
npm run devteam:status
npm run devteam:status -- --fetch   # auto-archive merged PRs
```

### `/devteam show <job-id>`

```bash
npm run devteam:show -- <job-id>
```

### `/devteam approve <job-id>`

Merge when CI green (waits for checks):

```bash
npm run devteam:approve -- <job-id>
```

### `/devteam revise <job-id> <notes>`

```bash
npm run devteam:revise -- <job-id> "Fix X and re-run tests"
```

### `/devteam cancel <job-id>`

```bash
npm run devteam:cancel -- <job-id>
```

## Phased queue (all plans)

```bash
npm run devteam:generate-phase-plans
npm run devteam:queue-phases
npm run devteam:status -- --fetch
```

See `devteam/PHASE-ORCHESTRATION.md`.

## Stage → subagent → advance

| Stage | Subagent type | Agent file | Advance when done |
|-------|---------------|------------|-------------------|
| plan | `devteam-planner` | `.cursor/agents/planner.md` | `--to coding` |
| code | `devteam-coder` | `.cursor/agents/coder.md` | `--to testing` |
| test | `devteam-tester` | `.cursor/agents/tester.md` | `--to reviewing` |
| review | `devteam-reviewer` | `.cursor/agents/reviewer.md` | `--to double_checking` |
| double-check | `devteam-double-checker` | `.cursor/agents/double-checker.md` | `--to awaiting_review` |

Artifacts live in `devteam/jobs/<job-id>/` (`01-plan.md` … `05-double-check.md`).

On approve/cancel, jobs move to `devteam/archive/` (see `devteam/archive/README.md`).

## Orchestrator loop (copy to worker chat)

> Read `devteam/phase-queue.json` and job `meta.json`.
> For each job in `coding`/`planning`/etc.: run the full pipeline to `awaiting_review`.
> Max 3 active jobs — poll `npm run devteam:status -- --fetch` when capped.
> Use model slugs from `meta.models` for every stage Task launch.

## Utilities

| Command | Purpose |
|---------|---------|
| `npm run devteam:conflicts` | Planned-file overlap between jobs |
| `npm run devteam:sync -- <job-id>` | Merge `main` into PR branch |
| `npm run devteam:sync -- --all-open` | Sync all open job branches |
| `npm run devteam:validate` | Validate jobs + agent model sync |
| `npm run devteam:models -- --validate-agents` | CI gate for model drift |
| `npm run android:setup` | Install JDK 17 + Android SDK (Plans 002+) |
| `source scripts/android-env.sh` | Set `ANDROID_HOME` / `JAVA_HOME` before `./gradlew` |

See `docs/android-toolchain.md` for cloud agent and local setup.
