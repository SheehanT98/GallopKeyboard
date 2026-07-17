# Job 012 — Test report

| Field | Value |
|-------|-------|
| **Job** | job-012 |
| **Branch** | `cursor/devteam-job-012-execute-plan-012-clipboard-pins-c1fc` |
| **PR** | [#33](https://github.com/SheehanT98/GallopKeyboard/pull/33) |
| **Plan** | `plans/012-clipboard-pins-and-symbols-entry.md` |
| **Tester** | devteam-tester (composer-2.5) |
| **Tested at** | 2026-07-17T13:15:00Z |
| **SHA tested** | `2e5e2e8363eea58c85f210e15e535c020b8626d0` |
| **Verdict** | **PASS** (automated); manual on-device deferred |

## Environment

```bash
source scripts/android-env.sh
# ANDROID_HOME=/opt/android-sdk
# JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

| Check | Command | Result |
|-------|---------|--------|
| Branch | `git branch --show-current` | on job-012 branch |
| Sync | `git pull origin cursor/devteam-job-012-execute-plan-012-clipboard-pins-c1fc` | up to date |

## Commands run

| Check | Command | Result |
|-------|---------|--------|
| Plan verify | `source scripts/android-env.sh && ./gradlew :ime:testDebugUnitTest :app:assembleDebug` | exit 0, BUILD SUCCESSFUL |
| Fresh unit tests | `source scripts/android-env.sh && ./gradlew :ime:testDebugUnitTest --rerun-tasks` | exit 0, 103 tests, 0 failures |
| Plan status | `plans/012-clipboard-pins-and-symbols-entry.md` | done checkboxes marked |

### Unit test summary

- **Total**: 103 (`:ime:testDebugUnitTest`)
- **Failures**: 0
- **Errors**: 0
- **New coverage**: `PinnedClipboardStoreTest` (4), `ClipboardEntryCodecTest` (4), `KeyboardLayoutTest` (symbols clipboard key), `PanelControllerTest` (clipboard state)

## Spot-check (code vs plan)

| Requirement | Evidence | Result |
|-------------|----------|--------|
| Clipboard key on **SYMBOLS** bottom row | `KeyboardLayouts.symbolsBottomRow` — `KeyType.CLIPBOARD` with 📋 label | PASS |
| Key opens clipboard panel | `KeyboardScreen` `KeyType.CLIPBOARD` → `onClipboardPanelToggle()`; `PanelController.showClipboard()` | PASS |
| **ClipboardPanel** UI | `ClipboardPanel.kt` — pinned section, recent section, tap-to-insert rows, pin toggle icons, keyboard return | PASS |
| Pin persists across process death | `PinnedClipboardStore` + DataStore `PINNED_CLIPBOARD_ENTRIES`; `PinnedClipboardStoreTest` round-trip + new-instance reload | PASS |
| Unpin removes from persistent store | `PinnedClipboardStore.unpin` / `togglePin`; tested in `PinnedClipboardStoreTest` | PASS |
| Recent clips in-memory ring | `ClipboardStore` default capacity 10; panel filters pinned from recent list | PASS |
| Insert via `onCommitText` | `PanelHost` `onInsert` → `onClipboardInsert` + `showTyping()` | PASS (code) |
| Back key dismisses panel | `DictusImeService` handles `PanelState.CLIPBOARD` on back | PASS (code) |
| Security note (plaintext on-device) | Comment in `PinnedClipboardStore.kt` | PASS |

## Done criteria (Plan 012)

| Criterion | Result |
|-----------|--------|
| Clipboard icon on symbols keyboard opens clipboard UI | PASS |
| Pin persists; unpin removes from persistent store | PASS |
| Tap inserts text | PASS (code); **NOT RUN** on device |
| Tests pass; README updated | PASS |
| `plans/README.md` updated to DONE for 012 | PASS |

## STOP conditions

| Condition | Outcome |
|-----------|---------|
| Syncing clipboard to network / cloud | **Not hit** |
| Reading other apps' private data beyond system clipboard APIs | **Not hit** |
| Showing clipboard contents in notifications | **Not hit** |

## Manual on-device (deferred)

Clipboard panel UX and pin persistence across IME restart were not exercised — no `adb` device attached.

| # | Action | Result |
|---|--------|--------|
| 1 | Switch to symbols layer → tap 📋 → clipboard panel opens | not run |
| 2 | Tap clip row → text inserted, panel closes | not run |
| 3 | Pin a recent clip → kill IME → reopen → pinned clip still listed | not run |
| 4 | Unpin → clip removed from pinned section | not run |
| 5 | Keyboard icon / back returns to typing | not run |

## Blockers

None for automated gate. Manual clipboard UX validation deferred to reviewer or owner with a sideloaded build.

## Advance

Left to orchestrator: `npm run devteam:advance -- job-012 --to reviewing`
