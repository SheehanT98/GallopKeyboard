# Phase 08: Keyboard polish (clipboard + emoji + DeepSeek look)

> Bundled phase plan (phase-08). Execute sub-plans **in order** on one branch.

---

<!-- from plans/009-keyboard-polish-clipboard-emoji.md -->

# Plan 009: Keyboard polish — short clipboard + basic emoji + DeepSeek look

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving
> to the next step. If anything in the "STOP conditions" section
> occurs, stop and report — do not improvise. When done, update the
> status row for this plan in `plans/README.md` — unless a reviewer
> dispatched you and told you they maintain the index.
>
> **Drift check (run first)**:
> ```
> bash scripts/verify.sh
> grep -q "Plan 008 additions" docs/dictus-inventory.md && echo OK
> ```

## Status

- **Priority**: P2
- **Effort**: M
- **Risk**: LOW (all UI, no ASR touch)
- **Depends on**: `plans/008-model-download-ux.md`
- **Category**: feature, dx
- **Planned at**: commit `99ca844`, 2026-07-16
- **Issue**: —

## Why this matters

`HANDOFF.md` Phase 4 asks for three polish items to reach "replace
Gboard for daily use":

1. **Short clipboard** (2–3 items).
2. **Basic emoji picker**.
3. **Visual polish** toward DeepSeek's voice UI (large rounded voice
   button, clean toolbar).

None of these are required for the STT pipeline to work, but without
them the owner (per `CONTEXT.md` "Destination v1") won't actually
adopt the keyboard.

Scope discipline: this is *basic* clipboard and *basic* emoji, not
full-featured. Anything beyond the acceptance list should become a
follow-up plan, not scope creep here.

## Current state

After Plan 008:

- Voice pipeline works end-to-end with sideloaded or downloaded models.
- Typing panel is Dictus's original.
- No clipboard, no emoji picker in the fork's own code (Dictus may or
  may not ship them — check inventory).
- Voice panel visual is the plain placeholder-derived layout from
  Plan 004 + smart button from Plan 005.

## Commands you will need

| Purpose  | Command                                       | Expected |
|----------|-----------------------------------------------|----------|
| Build    | `./gradlew --no-daemon assembleDebug`         | success  |
| Tests    | `./gradlew --no-daemon testAll`               | success  |
| Verify   | `bash scripts/verify.sh`                      | OK       |
| Logcat   | `adb logcat -s Clipboard:* -s VoicePanel:*`   | live |

## Scope

**In scope**:
- `ime/src/main/java/com/gallopkeyboard/ime/clipboard/ClipboardStore.kt`
  (new) — in-memory ring of the last 3 copy events.
- `ime/src/main/java/com/gallopkeyboard/ime/clipboard/ClipboardWatcher.kt`
  (new) — a `PrimaryClipChangedListener` bound to the IME lifecycle.
- `ime/src/main/java/com/gallopkeyboard/ime/panel/ClipboardStrip.kt`
  (new — Compose) — a thin row above the typing panel showing 0–3
  chips of recent clipboard text; tap inserts.
- `ime/src/main/java/com/gallopkeyboard/ime/panel/EmojiPanel.kt`
  (new) — a scrollable grid of emoji, opened via a new key/button on
  the typing panel.
- `ime/src/main/res/values/arrays.xml` — categorized emoji lists (7
  standard categories: Smileys, People, Animals, Food, Activities,
  Objects, Symbols; ~40 per category is enough for v1).
- `ime/src/main/java/com/gallopkeyboard/ime/panel/VoicePanel.kt`
  (edit) — apply DeepSeek visual pass: larger rounded button, clean
  neutral background, more breathing room, animated waveform ring
  while recording.
- `ime/src/main/java/com/gallopkeyboard/ime/theme/GallopTheme.kt`
  (new or edit) — token dark palette that echoes DeepSeek (deep
  neutral gray/near-black surface, single accent color for the
  active voice button; do not copy DeepSeek's exact brand hex).
- `ime/src/test/java/com/gallopkeyboard/ime/clipboard/ClipboardStoreTest.kt` (new).
- `docs/dictus-inventory.md` — "Plan 009 additions".
- `plans/README.md` status row.

**Out of scope**:
- Long-term clipboard history, cloud clipboard sync, or pinning
  (out of scope in `CONTEXT.md`).
- Emoji search, skin-tone modifier picker, recent emojis (v2).
- Swipe/gesture typing (`CONTEXT.md` "Out of scope").
- Sticker or GIF pickers.
- Per-app themes.
- Landscape layouts.
- Custom fonts — use system defaults.
- Animated haptics — Plan 010 audits UX polish across the app.

## Git workflow

- Branch: `advisor/009-keyboard-polish` off Plan 008.
- Commits:
  1. `feat(ime): ClipboardStore + ClipboardWatcher (3-item ring)`
  2. `feat(ime): ClipboardStrip above typing panel`
  3. `feat(ime): EmojiPanel with 7 categories`
  4. `feat(ime): DeepSeek-style visual pass on VoicePanel`
  5. `test(ime): ClipboardStore tests`
  6. `docs: Plan 009 additions`

## Steps

### Step 1: `ClipboardStore`

```kotlin
class ClipboardStore(private val capacity: Int = 3) {
    fun add(text: String) { /* dedup + trim */ }
    fun items(): List<String>
    fun clear()
}
```

- Deduplicate: if `text` equals the most recent item, no-op.
- Trim: skip strings > 500 chars (avoid holding huge pastes in memory;
  they'd be re-inserted from the user's real clipboard anyway if they
  paste them).
- Skip blanks and single whitespace.
- No persistence — this is *short* clipboard, in-memory only. If the
  IME process dies, history is gone. That's the intended scope.

### Step 2: `ClipboardWatcher`

- Register `ClipboardManager.OnPrimaryClipChangedListener` on IME
  `onCreate`, unregister on `onDestroy`.
- On event, read primary clip; only handle
  `ClipDescription.MIMETYPE_TEXT_PLAIN`.
- Push to `ClipboardStore`.
- Do NOT read the clipboard proactively (Android 13+ shows a system
  toast when apps read clipboard; we only touch it in response to
  events).

### Step 3: `ClipboardStrip`

- A thin `Row(Modifier.fillMaxWidth().height(32.dp))` above the
  typing panel keys (only shown when the store has ≥ 1 item).
- Each chip: `Card` with the text truncated to `overflow = TextOverflow.Ellipsis, maxLines = 1`, single line, ~120 dp max width.
- Tap chip → `InputConnection.commitText(text, 1)`.
- Long-press chip → confirmation → `store.clear()` (dev-friendly
  wipe; keeps the API surface minimal).
- Do not show system clipboard content beyond what our own watcher
  captured. That means: fresh install has an empty strip until the
  user copies something.

### Step 4: `EmojiPanel`

- Add a new key or button on the typing panel that opens the emoji
  view (Dictus likely has an existing empty slot; if not, put it
  adjacent to the space bar). Icon: `Icons.Filled.EmojiEmotions`.
- `EmojiPanel`: 8-column `LazyVerticalGrid`, `contentPadding = 8.dp`,
  each cell 40.dp × 40.dp. Tapping a cell commits that emoji.
- Category tabs at the top (`TabRow`): Smileys / People / Animals /
  Food / Activities / Objects / Symbols. Simple emoji lists in
  `arrays.xml`, no custom rendering.
- A "back to typing" icon at the bottom-right (mirrors voice panel's
  ⌨️ icon).
- Do NOT persist a "recents" section in v1.

### Step 5: DeepSeek visual pass on `VoicePanel`

Reference the ASCII layout in `HANDOFF.md`. Apply:

- Panel background: single flat dark surface color from `GallopTheme`
  (roughly `#1A1A1D` or Dictus's dark surface if it looks similar —
  do not clash).
- Voice button: 72.dp height, `RoundedCornerShape(36.dp)`, full width
  minus 32.dp margins. Text weight `Medium`. Icon leading (`Icons.Filled.Mic`).
- Recording state animation: `Modifier.drawBehind { drawCircle(color =
  accent, radius = animatedRadius) }` around the button center,
  pulsing at 1 Hz. Keep animation cheap (`animateFloatAsState`, no
  `Canvas` loops).
- Toolbar row: text "Think?" and "Search?" grayed out and non-clickable
  (still placeholders per HANDOFF); ⌨️ back to typing on the right.
- Typography: use `MaterialTheme.typography.titleMedium` for the
  button label, `bodySmall` for placeholder labels.
- No shadows, no gradients — matches DeepSeek's clean flat aesthetic.

Do NOT copy DeepSeek's actual brand colors or logo — this is
inspired, not derivative.

### Step 6: `ClipboardStore` unit tests

`ClipboardStoreTest.kt` cases:

- Empty store returns empty items.
- Add 3 → items() returns 3 in insertion order (most recent first).
- Add 4th → oldest evicted.
- Duplicate consecutive add → no-op.
- Add blank / whitespace → no-op.
- Add > 500 chars → no-op.
- Clear → empty.

### Step 7: Update inventory + plans README

`docs/dictus-inventory.md` — `## Plan 009 additions`.
`plans/README.md` — Plan 009 status → `DONE`.

## Test plan

Unit: `ClipboardStoreTest` (7 cases).

Manual on device (Galaxy S22):

- Type in Notes; copy 3 different sentences from a webpage; open
  keyboard → strip shows 3 chips → tap oldest → committed.
- Copy a 4th sentence → oldest chip is evicted.
- Long-press a chip → confirm clear → strip disappears.
- Open emoji panel → scroll categories → tap a smiley → committed.
- Open voice panel → visual matches DeepSeek reference (rounded
  button, animated recording ring). Compare against a DeepSeek
  screenshot side-by-side; own-eyes acceptance.
- Rotation still doesn't crash (may look bad — deferred).

## Done criteria

- [ ] `bash scripts/verify.sh` exits 0.
- [ ] `ClipboardStoreTest` (7 cases) passes.
- [ ] Copy → strip → tap → insert works on device.
- [ ] Emoji panel opens, scrolls, and inserts.
- [ ] Voice panel matches DeepSeek visual reference (rounded, dark,
      pulsing on record).
- [ ] `docs/dictus-inventory.md` "Plan 009 additions" present.
- [ ] `plans/README.md` row for Plan 009 shows `DONE`.

## STOP conditions

- Dictus already has a clipboard or emoji feature we're duplicating —
  extend it in place, do NOT create parallel files. Note the reuse in
  inventory.
- Adding `EmojiCompat` requires bumping Compose or AppCompat versions
  — do NOT bump. Use the built-in text renderer; emoji-13+ chars will
  render if the device font supports them (S22 does).
- `ClipboardManager.OnPrimaryClipChangedListener` doesn't fire in the
  IME process on Android 12+ — Android hardened clipboard access.
  Alternative: read the primary clip via `getPrimaryClipDescription()`
  only when the strip is about to be shown (i.e. `onStartInputView`).
  Note the change; update the strip's "freshness" behavior accordingly.
- The pulsing recording animation causes visible jank at 60 Hz on S22
  — reduce to 0.5 Hz or drop the animation. UX polish shouldn't
  regress performance.

## Maintenance notes

- Clipboard store is intentionally not persisted. If a future user
  request is "keep clipboard across sessions", it needs an ADR
  (crosses the CONTEXT.md "no long-term clipboard archive" line).
- Emoji categories are hardcoded — a future plan can generate them
  from the Unicode CLDR if v1 hardcoding proves stale. For v1, a
  single hand-picked ~280-emoji list is enough.
- The DeepSeek visual pass has no reference brand-asset dependency —
  if design ever formalizes a `docs/design-system.md`, retro-fit the
  tokens there. Until then, `GallopTheme.kt` is the single source of
  color truth.
- `ClipboardWatcher`'s Android 13+ toast-on-read is a UX cost —
  document in `docs/limitations.md` if you have to fall back to the
  "read on show" pattern.

