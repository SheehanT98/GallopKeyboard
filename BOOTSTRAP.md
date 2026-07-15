# Bootstrap note

These files were published from the GallopCRM grilling session (2026-07-15).

## If you are viewing this on the `gallopCRM` repo branch

This branch (`gallopkeyboard`) is a **standalone bootstrap** — only GallopKeyboard docs, no CRM code.

### Move to `gallopkeyboard` repo (one step after repo exists)

1. On GitHub (phone or desktop): create an **empty** repo named `gallopkeyboard` under `SheehanT98` (no README, no .gitignore).
2. Ask a Cursor agent (or run on any machine with `gh auth login`):

```bash
git clone --branch gallopkeyboard --single-branch https://github.com/SheehanT98/gallopCRM.git gallopkeyboard
cd gallopkeyboard
git remote set-url origin https://github.com/SheehanT98/gallopkeyboard.git
git push -u origin HEAD:main
```

Or from gallopCRM checkout: `./scripts/publish-gallopkeyboard.sh`

### Then

Open the new `gallopkeyboard` repo in Cursor and run `/improve deep` using `HANDOFF.md`.
