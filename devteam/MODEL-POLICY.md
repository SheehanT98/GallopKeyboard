# Devteam model policy

**Single source of truth:** `devteam/model-presets.json`

When `/devteam` or `/devteamquick` runs — in **Cloud Agents**, **local Cursor chat**, or any other orchestrator — every devteam stage **must** launch on the model slug recorded for that stage. There are no exceptions and no silent fallbacks to the orchestrator/parent model.

## Stage → model mapping

| Stage | Subagent | Preset key | Current slug |
|-------|----------|------------|--------------|
| Plan | `devteam-planner` | `plan` | from `model-presets.json` |
| Code | `devteam-coder` | `code` | from `model-presets.json` |
| Test | `devteam-tester` | `test` | from `model-presets.json` |
| Review | `devteam-reviewer` | `review` | from `model-presets.json` |
| Double-check | `devteam-double-checker` | `doubleCheck` | from `model-presets.json` |

On submit, these slugs are copied into `devteam/jobs/<job-id>/meta.json` under `models`. The orchestrator must use **those exact slugs** when launching each stage.

## Orchestrator rules (hard requirements)

1. **Read models before every stage.** Run `npm run devteam:models -- --job <job-id>` or read `meta.json`.
2. **Always pass `model` on Task/subagent launch.** Never omit it for devteam stages.
3. **Never use `model: inherit` or the parent chat model** as a substitute for a stage model.
4. **Never perform stage work yourself** on the orchestrator model when a devteam subagent should run (plan/code/test/review/double-check). If a subagent fails to write files, re-launch the **same stage on the same preset model** with write access — do not substitute a different model or do the stage yourself.
5. **Never ask the human to pick models.** Change `devteam/model-presets.json` instead.

## Agent definition sync

`.cursor/agents/{planner,coder,tester,reviewer,double-checker}.md` frontmatter must match `model-presets.json`. Run:

```bash
npm run devteam:models -- --validate-agents
```

This must exit 0 before opening a PR for devteam pipeline changes.

## Changing models

1. Edit `devteam/model-presets.json` only.
2. Run `npm run devteam:models -- --validate-agents` and fix any agent frontmatter drift.
3. New jobs pick up the change on submit; in-flight jobs keep the slugs stored in their `meta.json`.

## Forbidden

- Per-job / per-chat model prompts
- Orchestrator completing planner/reviewer/double-checker artifacts without launching the preset model
- Launching `devteam-coder` without `model: "<presets.code>"` (same for every stage)
