# Plan 018: Reconcile AGENTS.md and agent docs with shipped Plans 011–013

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**:
> ```
> git diff --stat 3571aab..HEAD -- \
>   AGENTS.md README.md plans/README.md docs/dictus-inventory.md
> ```
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P2
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: docs
- **Planned at**: commit `3571aab`, 2026-07-17
- **Issue**: —

## Why this matters

`AGENTS.md` still says **“Do not add swipe/gesture typing”** while Plan
013 has already shipped swipe typing on the LETTERS layer. Agents that
obey `AGENTS.md` will refuse or revert legitimate swipe work and will
mis-prioritize UX. The planning index still describes the repo as
“planning-only / Plans 001–010” in places, which confuses `/devteam`
dispatch for post-v1 work.

After this plan: agent rules match the product; plans index accurately
lists 001–018; historical `CONTEXT.md` out-of-scope swipe line is
explicitly superseded without rewriting that historical file (AGENTS
forbids editing `CONTEXT.md`).

## Current state

`AGENTS.md` Do NOT section:

```
- Do not add swipe/gesture typing.
```

`AGENTS.md` also says:

```
- Do not edit `HANDOFF.md`, `CONTEXT.md`, or `BOOTSTRAP.md` — those are historical inputs.
```

`CONTEXT.md` Out of scope still lists:

```
- Swipe/gesture typing
```

`plans/README.md` (as of plan-writing) still opens with greenfield /
Phases 0–5 language centered on Plans 001–010 even though 011–013 are
`DONE` and 014–018 are being added by this improve pass.

Code evidence swipe exists:

- `ime/.../ui/SwipeTypingController.kt`
- `ime/.../ui/KeyboardView.kt` (`swipeEnabled`)
- `plans/013-swipe-typing-across-letters.md` status DONE

## Commands you will need

| Purpose | Command | Expected |
|---------|---------|----------|
| Confirm swipe code present | `test -f ime/src/main/java/com/gallopkeyboard/ime/ui/SwipeTypingController.kt && echo OK` | `OK` |
| Confirm AGENTS no longer bans swipe | `rg -n "swipe/gesture" AGENTS.md` | no “Do not add” ban (see steps) |
| No Kotlin changes | `git diff --name-only` | docs/plans only |

## Scope

**In scope**:

- `AGENTS.md`
- `plans/README.md` (index prose + tables for 014–018 already added by
  the advisor — keep consistent; you only adjust docs wording / status
  if needed when finishing **this** plan)
- `README.md` — only if it claims swipe is out of scope or omits voice
  toolbar/clipboard pins incorrectly (read first; skip if already fine)
- `docs/dictus-inventory.md` — short “Plan 018 additions” note
- This plan’s status row

**Out of scope**:

- Editing `CONTEXT.md`, `HANDOFF.md`, or `BOOTSTRAP.md` (AGENTS forbids
  it). Instead, document supersession **inside `AGENTS.md`**.
- Any Kotlin / Gradle / resource changes
- Implementing Plans 014–017

## Git workflow

- Branch: `cursor/docs-agents-swipe-reconcile-1534`
- Commit style: `docs: reconcile AGENTS.md with shipped swipe typing`
- Do NOT push/PR unless instructed

## Steps

### Step 1: Update `AGENTS.md` Do NOT + product summary

1. **Remove** the line `Do not add swipe/gesture typing.`
2. Add a positive constraint, for example:
   - Swipe typing on the LETTERS layer is **in scope** (Plan 013). Do not
     remove it without an ADR.
   - Do not add cloud-backed swipe decoders / network lexicon services
     (still 100% on-device).
3. In “What this repo is” / “Where to find things”, mention Plans
   011–013 UX (toolbar Voice, clipboard pins, swipe) briefly.
4. Add an explicit supersession note near the CONTEXT pointer:

```markdown
Note: `CONTEXT.md` “Out of scope → Swipe/gesture typing” is historical
(pre–Plan 013). Swipe typing shipped in Plan 013; treat `AGENTS.md` +
`plans/013-*.md` as authoritative over that CONTEXT bullet.
```

5. Update the plans range mention from “001–010” to “001+ / see
   `plans/README.md`”.

**Verify**:
```
rg -n "Do not add swipe" AGENTS.md; test $? -ne 0 && echo BAN_GONE
rg -n "Plan 013|Swipe typing" AGENTS.md
```
→ `BAN_GONE` and at least one positive swipe reference.

### Step 2: Refresh `plans/README.md` intro prose

Ensure the index introduction no longer claims the repo is
planning-only without Kotlin. Describe:

- Plans 001–010: original v1 delivery (DONE)
- Plans 011–013: post-v1 UX (DONE)
- Plans 014–018: improve-skill hardening/docs (TODO)

Keep the status table accurate.

**Verify**: `rg -n "planning-only|no Kotlin" plans/README.md` → no stale
false claims (or only inside a clearly dated historical note).

### Step 3: README.md spot-check

Read root `README.md`. If it contradicts swipe / voice toolbar, fix the
one or two sentences. If fine, do not churn it.

**Verify**: judgment via `rg -ni "swipe|gesture|out of scope" README.md`.

### Step 4: Inventory note + finish

Append Plan 018 additions (docs-only). Mark this plan DONE in the index.

**Verify**:
```
git diff --name-only
```
→ only markdown files under the in-scope list.

## Test plan

- No code tests. Verification is grep-based as above.
- Optional: `bash scripts/verify.sh` should still pass untouched (run if
  easy; not required to prove docs).

## Done criteria

- [ ] `AGENTS.md` no longer bans swipe/gesture typing
- [ ] `AGENTS.md` documents CONTEXT supersession for that bullet
- [ ] `plans/README.md` intro matches shipped reality + 014–018 rows
- [ ] No Kotlin/Gradle files changed
- [ ] This plan’s status → `DONE`

## STOP conditions

- Operator insists on editing `CONTEXT.md` despite AGENTS — stop and
  ask; do not silently violate AGENTS
- Swipe code was removed from `main` since `3571aab` — stop; docs plan
  would be wrong

## Maintenance notes

- Future feature bans belong in `AGENTS.md` Do NOT and should be removed
  when a plan ships the feature
- Prefer ADRs for reversals of shipped UX
