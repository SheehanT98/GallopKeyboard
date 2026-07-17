# Job 012 — Code summary

## What shipped

- **Symbols clipboard key** (`📋` / `KeyType.CLIPBOARD`) on the symbols bottom row opens a full clipboard panel.
- **ClipboardPanel** lists pinned clips (top) and recent in-memory clips; tap inserts text and returns to typing; pin icon toggles persistence.
- **PinnedClipboardStore** persists pinned clips via DataStore `stringSetPreferencesKey` with base64-safe encoding.
- **ClipboardStore** default capacity increased from 3 → 10.
- **PanelState.CLIPBOARD** + `PanelController.showClipboard()` integrated in `PanelHost` and `DictusImeService` (back key dismisses panel).

## Files changed

| File | Why |
|------|-----|
| `core/.../PreferenceKeys.kt` | `PINNED_CLIPBOARD_ENTRIES` key |
| `ime/.../clipboard/ClipboardEntry.kt` | Entry model |
| `ime/.../clipboard/ClipboardEntryCodec.kt` | Encode/decode pinned set |
| `ime/.../clipboard/PinnedClipboardStore.kt` | DataStore-backed pins |
| `ime/.../clipboard/ClipboardStore.kt` | Capacity 10 |
| `ime/.../model/KeyType.kt` | `CLIPBOARD` |
| `ime/.../model/KeyboardLayouts.kt` | Symbols bottom row with clipboard key |
| `ime/.../panel/PanelState.kt` | `CLIPBOARD` state |
| `ime/.../panel/PanelController.kt` | `showClipboard()` |
| `ime/.../panel/ClipboardPanel.kt` | Panel UI |
| `ime/.../panel/PanelHost.kt` | Clipboard panel routing |
| `ime/.../ui/KeyboardScreen.kt` | Clipboard key handler |
| `ime/.../ui/KeyButton.kt` | Clipboard key styling |
| `ime/.../DictusImeService.kt` | Wire stores + panel |
| `ime/src/test/.../PinnedClipboardStoreTest.kt` | Pin persistence tests |
| `ime/src/test/.../ClipboardEntryCodecTest.kt` | Codec tests |
| `ime/src/test/.../ClipboardStoreTest.kt` | Explicit capacity in eviction test |
| `ime/src/test/.../KeyboardLayoutTest.kt` | Symbols clipboard key assertion |
| `ime/src/test/.../PanelControllerTest.kt` | Clipboard state tests |
| `plans/012-clipboard-pins-and-symbols-entry.md` | Done checkboxes |
| `plans/README.md` | Plan 012 → DONE |

## Verification

```bash
source scripts/android-env.sh && ./gradlew :ime:testDebugUnitTest :app:assembleDebug
```

Result: **BUILD SUCCESSFUL** — 103 unit tests passed.
