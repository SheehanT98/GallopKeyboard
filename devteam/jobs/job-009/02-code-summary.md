# Code summary — job-009

## STOP conditions

| Condition | Outcome |
|-----------|---------|
| Dictus already has emoji | **Hit** — reused `EmojiPickerScreen.kt` + `EmojiPickerView`; did not add `EmojiPanel.kt` or `arrays.xml` |
| EmojiCompat version bump | Not triggered |
| Clipboard listener Android 12+ | **Mitigated** — `refreshFromPrimaryClip()` on `onStartInputView`; documented in `docs/limitations.md` |
| Recording animation jank | Not observed in unit/build; 1 Hz `infiniteRepeatable` pulse |

## Files added

| File | Why |
|------|-----|
| `ime/.../clipboard/ClipboardStore.kt` | 3-item in-memory ring with dedup/trim |
| `ime/.../clipboard/ClipboardWatcher.kt` | Lifecycle-bound clip listener + show fallback |
| `ime/.../panel/ClipboardStrip.kt` | Chip row UI above typing keys |
| `ime/.../theme/GallopTheme.kt` | Voice-panel dark palette tokens |
| `ime/src/test/.../clipboard/ClipboardStoreTest.kt` | 7 unit cases per plan |
| `docs/limitations.md` | Clipboard Android 12+/13+ notes |

## Files edited

| File | Why |
|------|-----|
| `ime/.../DictusImeService.kt` | Wire watcher lifecycle, expose `itemsFlow` to keyboard |
| `ime/.../ui/KeyboardScreen.kt` | Render `ClipboardStrip` above `KeyboardView` |
| `ime/.../panel/VoicePanel.kt` | `GallopVoiceTheme`, flat dark layout, placeholder toolbar |
| `ime/.../panel/SmartVoiceButton.kt` | 72dp pill, mic icon, 1 Hz recording pulse ring |
| `ime/.../panel/PanelHost.kt` | Drop unused `themeMode` on voice panel |
| `docs/dictus-inventory.md` | Plan 009 additions (emoji reuse noted) |
| `plans/README.md` | Plan 009 → DONE |

## Verification

```
source scripts/android-env.sh && bash scripts/verify.sh  # exit 0
```

`ClipboardStoreTest` — 7/7 pass via `testAll`.

## Manual (device)

Not run in cloud agent — Galaxy S22 sideload per plan test section.
