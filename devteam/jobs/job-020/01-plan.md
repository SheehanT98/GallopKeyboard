# Plan 030: Wire suggestion bar + English dictionary defaults (finish 019)

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 32b0d20..HEAD -- ime/src/main/java/com/gallopkeyboard/ime/DictusImeService.kt ime/src/main/java/com/gallopkeyboard/ime/ui/KeyboardScreen.kt ime/src/main/java/com/gallopkeyboard/ime/ui/SuggestionBar.kt ime/src/main/java/com/gallopkeyboard/ime/suggestion/DictionaryEngine.kt app/src/main/java/com/gallopkeyboard/ui/settings/SettingsViewModel.kt core/src/main/java/com/gallopkeyboard/core/preferences/PreferenceKeys.kt`
> If Phase 9 missing or excerpts mismatch, STOP.
>
> **Supersedes incomplete Plan 019**: index marked DONE; code never wired the
> bar or fixed English defaults.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED
- **Depends on**: none (Phase 9 assumed on main)
- **Category**: bug / direction
- **Planned at**: commit `32b0d20`, 2026-07-20

## Why this matters

`DictusImeService` already computes `_suggestions` / `_currentWord` when the
pref is on, and `SuggestionBar` composable exists — but `KeyboardScreen` never
renders it and never receives those flows. Settings defaults
`SUGGESTIONS_ENABLED` to **true**; IME observes `?: false` — fresh installs
disagree. `DictionaryEngine` defaults missing/`auto` language to **French**
`dict_fr.txt` on an English-only product, so autocorrect (026) and any bar
would rank French words.

Without this, Plans 023/032 swipe decode and personal-dict learning stay
pointless.

## Current state

```kotlin
// DictusImeService.kt — computed but never passed to UI
_suggestions.value = if (_suggestionsEnabled.value) {
    suggestionEngine.getSuggestions(currentWord)
} else emptyList()

// IME default
.map { it[PreferenceKeys.SUGGESTIONS_ENABLED] ?: false }

// SettingsViewModel.kt
.map { it[PreferenceKeys.SUGGESTIONS_ENABLED] ?: true }

// DictionaryEngine.kt init
val lang = dataStore.data.first()[PreferenceKeys.TRANSCRIPTION_LANGUAGE] ?: "fr"
if (lang == "en") "dict_en.txt" else "dict_fr.txt"  // "auto" → French!

// KeyboardScreen.kt — no SuggestionBar params / call
fun KeyboardScreen( onCommitText: ..., ... clipboardStore: ... )
```

`SuggestionBar.kt` exists with 3-slot Gboard layout (~40+).

**Conventions**: English-only (`AGENTS.md`, ADR-0004/0005). Compose Material3.
Match existing `collectAsState` patterns in `KeyboardContent`.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| IME tests | `./gradlew :ime:testDebugUnitTest` | BUILD SUCCESSFUL |
| Focused | `./gradlew :ime:testDebugUnitTest --tests '*Dictionary*' --tests '*Suggestion*'` | BUILD SUCCESSFUL |
| Verify | `bash scripts/verify.sh` | `OK` |

## Scope

**In scope**:
- `ime/.../DictusImeService.kt` — pass suggestion state + commit handlers;
  align default `?: true` with Settings (or both false — pick **true** to
  match Settings and Plan 019 intent)
- `ime/.../ui/KeyboardScreen.kt` — render `SuggestionBar` when enabled
- `ime/.../suggestion/DictionaryEngine.kt` — English default for missing/`auto`
- Optional: call `personalDictionary.recordWordTyped` on space / suggestion
  commit (small; include if one-liner path is clear)
- Unit tests for language→asset mapping
- `docs/dictus-inventory.md` Plan 030
- `plans/README.md`

**Out of scope**:
- SwipeWordResolver (Plan 032)
- Autocorrect default-ON promote
- Number row
- Changing SuggestionBar visual design

## Git workflow

- Branch: `cursor/030-wire-suggestion-bar-english-defaults`
- Commit: `feat(ime): wire SuggestionBar; default English dictionary`
- Do NOT push/PR unless instructed.

## Steps

### Step 1: English dictionary asset selection

```kotlin
fun dictionaryAssetForLanguage(lang: String?): String =
    when (lang?.lowercase()) {
        "fr" -> "dict_fr.txt"
        else -> "dict_en.txt" // en, auto, null, unknown
    }
```

Use in `DictionaryEngine` init instead of `?: "fr"` / `lang == "en"` check.

**Verify**: unit test — `null`/`"auto"`/`"en"` → `dict_en.txt`; `"fr"` → `dict_fr.txt`.

### Step 2: Align suggestions pref default

IME DataStore collect: `?: true` to match `SettingsViewModel`. Document that
v1 UX shows the bar by default (height budget: SuggestionBar already documents
fixed height).

**Verify**: Settings + IME both use `?: true`.

### Step 3: Wire SuggestionBar into KeyboardScreen

1. Add params: `suggestionsEnabled: Boolean`, `currentWord: String`,
   `suggestions: List<String>`, `onSuggestionSelected: (String) -> Unit`,
   `onCurrentWordSelected: () -> Unit`.
2. When `suggestionsEnabled`, place `SuggestionBar` above the key grid (and
   above clipboard strip if that is above keys — match Gboard: bar above
   keys; keep clipboard strip behavior unchanged).
3. From `DictusImeService.KeyboardContent`, `collectAsState` the flows and
   pass them in. On suggestion select: delete current word fragment via
   `deleteSurroundingText(currentWord.length, 0)` then `commitText("$word ")`
   (or existing helper if any). Clear autocorrect undo on suggestion commit.
4. Pass `suggestionEngine` / dictionary into `KeyboardView` only if already
   parameterized — do **not** implement swipe resolver here (032).

**Verify**: `./gradlew :ime:compileDebugKotlin` success; existing UI tests pass.

### Step 4: Docs + verify

Inventory Plan 030. `bash scripts/verify.sh` → `OK`.

## Test plan

- `dictionaryAssetForLanguage` table tests.
- Optional Robolectric/UI: SuggestionBar receives lists (if cheap; else manual).
- Manual: enable Suggestions in Settings → type `hel` → see hello; tap center.

## Done criteria

- [ ] SuggestionBar visible when pref on; hidden when off
- [ ] IME and Settings default agree (`true`)
- [ ] Missing/`auto` language loads `dict_en.txt`
- [ ] Suggestion tap replaces current word + space
- [ ] `verify.sh` OK; scope respected

## STOP conditions

- Suggestion bar + clipboard strip + Plan 019 height exceeds host usable
  area on a density you can measure — reduce strip, don’t shrink key hit
  targets without reporting.
- `KeyboardScreen` signature conflicts with unmerged Phase 9 — rebase on
  025/026 first.

## Maintenance notes

- Plan 032 needs this engine wired for swipe decode quality.
- Reviewer: S22 height with suggestions ON + clipboard strip.
- Mark Plan 019 superseded / completed by 030 in the index.
