# Job 009 — Test report

| Field | Value |
|-------|-------|
| **Job** | job-009 |
| **Branch** | `cursor/devteam-job-009-execute-plan-009-keyboard-polish-clipboard-emoji-c1fc` |
| **PR** | [#23](https://github.com/SheehanT98/GallopKeyboard/pull/23) |
| **Tester** | devteam-tester (composer-2.5) |
| **Tested at** | 2026-07-16T19:20:00Z |
| **SHA tested** | `2d3f74c62003fc09001e10830ed839e82f9333b2` |
| **Verdict** | **PASS** (automated); manual on-device deferred |

## Environment

```bash
npm run android:setup   # SDK marker present, toolchain ready
source scripts/android-env.sh
# ANDROID_HOME=/opt/android-sdk
# JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

| Check | Command | Result |
|-------|---------|--------|
| Branch | `git branch --show-current` | on job-009 branch |
| Device | `adb devices` | no devices attached |

## Commands run

| Check | Command | Result |
|-------|---------|--------|
| Drift guard | `bash scripts/verify.sh` | exit 0, printed `OK` |
| Plan 009 inventory | `grep -q "Plan 009 additions" docs/dictus-inventory.md` | OK |
| ClipboardStore unit tests | `./gradlew --no-daemon :ime:testDebugUnitTest --tests 'com.gallopkeyboard.ime.clipboard.ClipboardStoreTest'` | BUILD SUCCESSFUL |
| Plan status | `plans/README.md` row 009 | DONE |

### verify.sh breakdown

- `assembleDebug` — BUILD SUCCESSFUL
- `testAll` — BUILD SUCCESSFUL
- `lint` — BUILD SUCCESSFUL
- Dictus package grep guard — no hits outside `third_party/`
- Model binary grep guard — no hits outside `third_party/`

### ClipboardStoreTest (7/7)

| Test | Result |
|------|--------|
| `empty store returns empty items` | pass |
| `add 3 returns 3 in insertion order most recent first` | pass |
| `add 4th evicts oldest` | pass |
| `duplicate consecutive add is no-op` | pass |
| `blank and whitespace adds are no-op` | pass |
| `text over 500 chars is no-op` | pass |
| `clear empties store` | pass |

## Done criteria (Plan 009)

| Criterion | Result |
|-----------|--------|
| `bash scripts/verify.sh` exits 0 | PASS |
| `ClipboardStoreTest` (7 cases) passes | PASS |
| Copy → strip → tap → insert on device | **NOT RUN** — no device |
| Emoji panel opens, scrolls, inserts | **NOT RUN** — no device (emoji reused from Dictus `EmojiPickerScreen`) |
| Voice panel DeepSeek visual (rounded, dark, pulsing on record) | **NOT RUN** — no device |
| `docs/dictus-inventory.md` "Plan 009 additions" | PASS |
| `plans/README.md` row 009 `DONE` | PASS |

## STOP conditions (coder notes)

| Condition | Outcome |
|-----------|---------|
| Dictus emoji reuse | Coder reused `EmojiPickerScreen.kt` / `EmojiPickerView` instead of new `EmojiPanel.kt` — documented in inventory |
| Clipboard listener Android 12+ | Mitigated via `refreshFromPrimaryClip()` on `onStartInputView`; documented in `docs/limitations.md` |
| Recording animation jank | Not observed in build; 1 Hz pulse retained |

## Manual on-device (deferred)

Plan acceptance tests require Galaxy S22 sideload per plan test section. Not executed in this session.

| # | Action | Result |
|---|--------|--------|
| 1 | Copy 3 sentences → strip shows 3 chips → tap oldest → committed | not run |
| 2 | Copy 4th sentence → oldest chip evicted | not run |
| 3 | Long-press chip → confirm clear → strip disappears | not run |
| 4 | Open emoji panel → scroll categories → tap smiley → committed | not run |
| 5 | Voice panel visual (rounded button, animated recording ring) | not run |
| 6 | Rotation doesn't crash | not run |

## Blockers

- **Manual clipboard/emoji/voice acceptance (non-blocking for automated gate):** Clipboard strip, emoji picker, and voice-panel visual polish not run on device — no `adb` device attached. Reviewer or human should validate per Plan 009 manual test plan before merge.

## Advance

Automated verification passed → `npm run devteam:advance -- job-009 --to reviewing`
