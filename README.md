# GallopKeyboard

Personal Android keyboard with offline voice dictation. Fork of [Dictus](https://github.com/getdictus/dictus-android) + DeepSeek-style voice panel toggle.

**Status:** Planning / bootstrap — no app code yet. Phased plans in [`plans/`](./plans/README.md); agent execution via [`devteam/`](./devteam/README.md).

## Docs

| File | Purpose |
|------|---------|
| [`CONTEXT.md`](./CONTEXT.md) | Glossary, acceptance criteria, device targets |
| [`HANDOFF.md`](./HANDOFF.md) | Full grilling spec — feed to `/improve` |
| [`plans/`](./plans/README.md) | Executor plans 001–010 (from `/improve deep`) |
| [`devteam/`](./devteam/README.md) | Job queue, model policy, phased orchestration |
| [`docs/adr/`](./docs/adr/) | Architecture decisions |

## Next step

Execute Plan 001, then queue the rest:

```text
/devteamquick plans/001-repo-hygiene-and-agents-md.md
```

Or queue all phases: see [`devteam/PHASE-ORCHESTRATION.md`](./devteam/PHASE-ORCHESTRATION.md).

## Target

- Android only (v1), Galaxy S22
- English only, 100% on-device STT
- Sideload APK; Play Store later
