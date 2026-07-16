# Devteam archive

This folder holds **finished or abandoned jobs** moved out of active `devteam/jobs/`. The archive workflow is part of devteam; this repo starts with an **empty** archive (no gallopCRM job history).

## When jobs land here

| Trigger | Action |
|---------|--------|
| `/devteam approve <job-id>` | After merge, move `jobs/<job-id>/` → `archive/jobs/<job-id>/` |
| `/devteam cancel <job-id>` | Move immediately to `archive/jobs/<job-id>/` with `status: cancelled` in `meta.json` |
| Superseded phase job | Queue script cancels and archives (e.g. `--cancel-job-018` in phase orchestration) |

## Expected layout per archived job

```
archive/jobs/job-NNN/
  meta.json           # final status, models used, branch, PR URL
  01-plan.md          # (if full job)
  02-code.diff        # or summary
  03-test.log
  04-review.md
  05-double-check.md
```

Not every stage file is required for `/devteamquick` jobs (plan skipped).

## What to commit

- **Do** commit archive README and empty `archive/jobs/.gitkeep` when bootstrapping.
- **Optional:** commit archived jobs from *this* repo if you want history in git (large logs usually stay local).
- **Do not** import archive contents from gallopCRM or other projects.

## Restoring / auditing

```bash
/devteam show job-NNN    # works if job is in jobs/ or archive/jobs/
ls devteam/archive/jobs/
```

Orchestrators read `devteam/registry.json` for queue state; archived jobs are removed from the active queue but remain discoverable under `archive/jobs/<job-id>/`.
