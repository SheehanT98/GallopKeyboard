# Devteam archive

Finished or cancelled jobs are moved here from `devteam/jobs/` by the scripts (`approve`, `cancel`, `devteam-archive`, or `status --fetch` after merge).

## Layout

```
devteam/archive/
  job-001-repo-hygiene-2026-07-16/
    meta.json
    01-plan.md
    02-code-summary.md
    ...
```

Folder name: `{job-id}-{slug}-{YYYY-MM-DD}`.

## Triggers

| Event | Script |
|-------|--------|
| `/devteam approve` | `devteam-approve.cjs` → archive after merge |
| `/devteam cancel` | `devteam-cancel.cjs` → archive immediately |
| PR merged on GitHub | `devteam-status --fetch` → auto-archive |
| Manual | `npm run devteam:archive -- <job-id>` |

## What to commit

- **Do not** import archive folders from gallopCRM or other projects.
- Optional: commit archives from this repo for history (usually keep local only).

## Audit

```bash
npm run devteam:show -- job-001   # works while job is active
ls devteam/archive/
```
