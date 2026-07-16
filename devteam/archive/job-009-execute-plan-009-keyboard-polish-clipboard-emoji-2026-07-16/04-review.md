# Review — job-009 (Keyboard polish — clipboard + emoji)

| Field | Value |
|-------|-------|
| **Job** | job-009 |
| **Branch** | `cursor/devteam-job-009-execute-plan-009-keyboard-polish-clipboard-emoji-c1fc` |
| **PR** | [#23](https://github.com/SheehanT98/GallopKeyboard/pull/23) |
| **Reviewer** | devteam-reviewer (cursor-grok-4.5-high) |
| **Reviewed at** | 2026-07-16T19:20:00Z |
| **Feature SHA** | `d813f91` |
| **HEAD at review** | `1f7937b` (includes test report + advance to reviewing) |
| **Verdict** | **approve** |

## Summary

**Approve.** Plan 009 scope is met: 3-item `ClipboardStore` + lifecycle `ClipboardWatcher` (with Android 12+ `onStartInputView` fallback), `ClipboardStrip` above typing keys, DeepSeek-inspired voice panel via `GallopVoiceTheme` / 72.dp pill + 1 Hz pulse, Dictus emoji picker reused per STOP (no parallel `EmojiPanel`/`arrays.xml`), seven `ClipboardStoreTest` cases, inventory + limitations + `plans/README.md` DONE. Automated gate green per tester (`verify.sh` exit 0). Manual on-device clipboard/emoji/voice acceptance deferred — risk for human, not a merge blocker.

**Blockers:** none.

## Scope compliance

| Plan item | Status |
|-----------|--------|
| `ClipboardStore` (capacity 3, dedup, blank skip, >500 skip, no persistence) | Done |
| `ClipboardWatcher` (listener onCreate/onDestroy, plain text only) | Done |
| Android 12+ listener STOP → `refreshFromPrimaryClip` on `onStartInputView` | Done — documented in `docs/limitations.md` |
| `ClipboardStrip` (32.dp row, chips ≤120.dp, tap insert, long-press clear confirm) | Done |
| `EmojiPanel` + `arrays.xml` | **Skipped correctly** — STOP: reuse Dictus `EmojiPickerScreen` / `EmojiPickerView` |
| VoicePanel DeepSeek pass (flat dark, placeholders, ⌨️ back) | Done |
| Smart voice button 72.dp / `RoundedCornerShape(36.dp)` / mic / pulse ring | Done in `SmartVoiceButton.kt` |
| `GallopTheme.kt` tokens (~`#1A1A1D`, single accent) | Done (`GallopColors` + `GallopVoiceTheme`) |
| `ClipboardStoreTest` (7 cases) | Done |
| `docs/dictus-inventory.md` Plan 009 additions | Done (emoji reuse noted) |
| `plans/README.md` → DONE | Done |
| Out of scope (long clipboard, emoji search/recents, stickers, landscape, custom fonts) | Respected |

### Acceptable deviations

- Wiring edits to `DictusImeService.kt`, `KeyboardScreen.kt`, `SmartVoiceButton.kt`, `PanelHost.kt` — required integration, not scope creep.
- No new `EmojiPanel.kt` / `arrays.xml` — mandated by STOP when Dictus already ships emoji.
- Voice horizontal padding is 16.dp on panel + 16.dp on button (≈64.dp total inset vs plan’s “32.dp margins”) — still flat/full-width pill; visual intent preserved.
- `ClipboardStrip` sits inside the fixed 264.dp keyboard column with `KeyboardView` `weight(1f)` — keys shrink slightly when strip is visible; acceptable for v1, worth eyes-on.

## Verification evidence

From `03-test-report.md` (SHA `2d3f74c` feature+meta at test time; code commit `d813f91`):

| Check | Result |
|-------|--------|
| `bash scripts/verify.sh` | exit 0 (`assembleDebug` + `testAll` + `lint` + guards) |
| `:ime:testDebugUnitTest` — `ClipboardStoreTest` | 7/7 PASS |
| Inventory grep `Plan 009 additions` | OK |
| `plans/README.md` row 009 | DONE |
| PR #23 | open, mergeable, base `main`; CI `build` in progress at review time |

### Done criteria

| Criterion | Result |
|-----------|--------|
| `verify.sh` exits 0 | PASS |
| `ClipboardStoreTest` 7 cases | PASS |
| Copy → strip → tap → insert on device | **DEFERRED** (no adb device) |
| Emoji panel opens / scrolls / inserts | **DEFERRED** (reused Dictus picker; no device) |
| Voice panel DeepSeek visual (rounded, dark, pulse) | **DEFERRED** (no device) |
| Plan 009 inventory section | PASS |
| `plans/README.md` DONE | PASS |

## Risks for the human reviewer

1. **Manual acceptance not run** — clipboard strip (3 chips, eviction, long-press clear), emoji insert, and voice pulse/visual need Galaxy S22 (or AVD) before high-confidence merge. Highest gap.
2. **Android 13+ clipboard toast** — `refreshFromPrimaryClip()` on every keyboard show may toast “pasted from clipboard”; documented in `docs/limitations.md`, still a UX surprise.
3. **Strip inside 264.dp column** — when history is non-empty, key rows lose ~32.dp; check for cramped keys / clipped bottom row on device.
4. **Emoji path is upstream Dictus widget** — not the plan’s custom 7-category `arrays.xml` grid; behavior depends on `EmojiPickerView` (categories/recents may exceed v1 “no recents” intent). STOP reuse is correct; confirm product acceptance.
5. **Pulse uses `infiniteRepeatable` while recording** — STOP warned about S22 jank; not measured here. Drop to 0.5 Hz or disable if frame drops appear.
6. **CI** — GitHub `build` was still pending at review; confirm green before approve/merge.

## PR

Already open: https://github.com/SheehanT98/GallopKeyboard/pull/23 — no `devteam:open-pr` needed.
