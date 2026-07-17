# Plan 012: Symbols clipboard entry + pinned persistent clips

> **Executor instructions**: Follow this plan step by step. Run every
> verification. STOP conditions apply. Update `plans/README.md` when done
> unless a reviewer maintains the index.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: LOW–MED (DataStore persistence)
- **Depends on**: `plans/011-toolbar-voice-and-thin-voice-panel.md`
- **Category**: feature / UX
- **Planned at**: 2026-07-17

## Why this matters

Users need fast access to repetitive text (emails, passwords they choose to
store). Clipboard should not only be a transient strip — they want **pin**
for persistent items, opened from the **symbols** layer via a clipboard icon.

## Product requirements

1. On **SYMBOLS** (and optionally NUMBERS) layer bottom/utility row, add a
   **clipboard** key/icon.
2. Tapping it opens a **Clipboard panel** (Compose overlay or panel state)
   listing recent clips + **pinned** clips.
3. Each item: tap to insert into the focused field; pin/unpin toggle.
4. **Pinned** items persist across process death (DataStore or app files).
5. Recent (unpinned) can remain in-memory ring (existing `ClipboardStore`)
   but capacity may increase slightly (e.g. 10).
6. Light theme, simple list — no cards overload; one job: pick or pin.

## Current state

- `ClipboardStore` — in-memory ring, capacity 3, no pin, no persistence.
- `ClipboardStrip` — horizontal chips above keys when non-empty.
- `ClipboardWatcher` — captures primary clip on IME show.

## Implementation steps

### Step 1 — Model + persistence

- Extend clipboard model: `ClipboardEntry(id, text, pinned, updatedAt)`.
- `PinnedClipboardStore` (or evolve `ClipboardStore`) using DataStore
  preferences / JSON string set under `PreferenceKeys`.
- Keep unpinned recent separate (memory) vs pinned (persisted).
- Security note in code comment: pinned text is stored on-device plaintext —
  suitable for emails / frequent phrases; user chooses what to pin.

### Step 2 — Symbols key + panel UI

- `KeyboardLayouts.symbolsRows` (and numbers if space): clipboard key
  (`KeyType` new `CLIPBOARD` or reuse with label).
- `KeyboardScreen` / `PanelController`: state for clipboard panel vs typing.
- UI: list of pinned (top) + recent; pin icon; insert on tap; close /
  back to symbols or typing.

### Step 3 — Wire insert + watcher

- Insert via existing `onCommitText`.
- Watcher continues feeding recent store.

### Step 4 — Tests

```bash
./gradlew :ime:testDebugUnitTest
```

Unit tests: pin/unpin persistence round-trip (Robolectric or pure JVM with
fake DataStore if already patterned).

## STOP conditions

- Syncing clipboard to network / cloud.
- Reading other apps' private data beyond system clipboard APIs.
- Showing clipboard contents in notifications.

## Done when

- [x] Clipboard icon on symbols keyboard opens clipboard UI
- [x] Pin persists; unpin removes from persistent store
- [x] Tap inserts text
- [x] Tests pass; README updated
