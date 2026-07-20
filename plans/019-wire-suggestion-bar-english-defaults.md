# Plan 019: Wire suggestion bar on typing panel with English defaults

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 86dfd89..HEAD -- ime/src/main/java/com/gallopkeyboard/ime/ui/KeyboardScreen.kt ime/src/main/java/com/gallopkeyboard/ime/DictusImeService.kt ime/src/main/java/com/gallopkeyboard/ime/suggestion/DictionaryEngine.kt ime/src/main/java/com/gallopkeyboard/ime/ui/SuggestionBar.kt app/src/main/java/com/gallopkeyboard/ui/settings/SettingsViewModel.kt core/src/main/java/com/gallopkeyboard/core/preferences/PreferenceKeys.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED
- **Depends on**: none
- **Category**: direction / bug
- **Planned at**: commit `86dfd89`, 2026-07-20

## Why this matters

The keyboard feels unfinished for daily typing: `DictionaryEngine` and
`SuggestionBar` already exist, `_suggestions` is updated in
`onUpdateSelection`, and Settings exposes a Suggestions toggle — but the
typing panel never renders the bar, the IME defaults suggestions **off**,
and the dictionary defaults to **French** (`dict_fr.txt`) on an English-only
product. Wiring this is the highest-leverage path to a Gboard-like typing
feel without inventing a new prediction stack.

## Current state

- `ime/.../DictusImeService.kt` — owns `_suggestionsEnabled` (default false),
  `_currentWord`, `_suggestions`, and `DictionaryEngine` lazy init. Updates
  suggestions in `onUpdateSelection` but never passes them to `KeyboardScreen`.
- `ime/.../ui/KeyboardScreen.kt` — typing panel; no suggestion params; fixed
  `Column(modifier = Modifier.height(310.dp))` for clipboard strip + keys.
- `ime/.../ui/SuggestionBar.kt` — ready Compose 3-slot bar (current word +
  two suggestions); unused from production UI.
- `ime/.../suggestion/DictionaryEngine.kt` — loads `dict_fr.txt` unless
  `TRANSCRIPTION_LANGUAGE == "en"`.
- `app/.../SettingsViewModel.kt` — `SUGGESTIONS_ENABLED ?: true` (opposite of IME).

Excerpts (confirm before editing):

```kotlin
// DictusImeService.kt — suggestions default OFF
private val _suggestionsEnabled = MutableStateFlow(false)
// ...
.map { it[PreferenceKeys.SUGGESTIONS_ENABLED] ?: false }

// DictionaryEngine.kt — French default
val lang = dataStore.data.first()[PreferenceKeys.TRANSCRIPTION_LANGUAGE] ?: "fr"
if (lang == "en") "dict_en.txt" else "dict_fr.txt"

// KeyboardScreen.kt — no SuggestionBar in the Column
Column(modifier = Modifier.height(310.dp)) {
    if (clipboardStore != null && clipboardItems.isNotEmpty()) {
        ClipboardStrip(...)
    }
    KeyboardView(...)
}
```

**Conventions**: Kotlin + Compose, Material 3, Hilt EntryPoint for IME deps,
English UI strings only (ADR-0004 / `AGENTS.md`). Match existing callback
style on `KeyboardScreen` (`onCommitText`, etc.). Do **not** edit
`CONTEXT.md` / `HANDOFF.md` / `BOOTSTRAP.md`.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Unit tests (ime) | `./gradlew :ime:testDebugUnitTest` | BUILD SUCCESSFUL |
| Unit tests (app settings if touched) | `./gradlew :app:testDebugUnitTest --tests '*Settings*'` | BUILD SUCCESSFUL |
| Verify gate | `bash scripts/verify.sh` | ends with `OK` |
| Assemble | `./gradlew assembleDebug` | BUILD SUCCESSFUL |

## Scope

**In scope**:
- `ime/src/main/java/com/gallopkeyboard/ime/ui/KeyboardScreen.kt`
- `ime/src/main/java/com/gallopkeyboard/ime/DictusImeService.kt`
- `ime/src/main/java/com/gallopkeyboard/ime/suggestion/DictionaryEngine.kt`
- `ime/src/main/java/com/gallopkeyboard/ime/ui/SuggestionBar.kt` (only if a tiny API tweak is required)
- `app/src/main/java/com/gallopkeyboard/ui/settings/SettingsViewModel.kt`
- New/updated tests under `ime/src/test/...` (and app settings test if one exists for the default)
- `docs/dictus-inventory.md` — append "Plan 019 additions"
- `plans/README.md` — status row

**Out of scope**:
- AOSP LatinIME JNI / neural LM (leave as future direction)
- Autocorrect-on-space (Plan-adjacent Direction; do not build here)
- Changing `SuggestionEngine` interface contract beyond what wiring needs
- Editing `CONTEXT.md` / `HANDOFF.md`
- Dual onboarding consolidation
- Swipe decoder (Plan 023)

## Git workflow

- Branch: `advisor/019-wire-suggestion-bar-english-defaults` (or cloud agent branch)
- Commit style: conventional, e.g. `feat(ime): wire suggestion bar with English defaults`
- Do NOT push/open a PR unless the operator instructed it.

## Steps

### Step 1: Fix dictionary language default to English

In `DictionaryEngine.kt` init:

1. Default missing language to `"en"` (not `"fr"`).
2. Treat `"auto"` (Settings default) as English for v1: load `dict_en.txt`
   whenever `lang != "fr"`. Keep `"fr"` → `dict_fr.txt` for any user who
   explicitly picked French.

**Verify**: `./gradlew :ime:testDebugUnitTest --tests '*DictionaryEngine*'` → pass.
Add or extend a unit test that constructs `DictionaryEngine` with a DataStore
pref of `"auto"` or missing key and asserts the loaded asset is English
(pattern: existing `DictionaryEngineTest` with `assetName` override is fine —
also add a small pure helper if asset selection is extracted for testability).

Preferred shape — extract a package-visible helper so tests do not need assets:

```kotlin
internal fun dictionaryAssetForLanguage(lang: String?): String =
    when (lang) {
        "fr" -> "dict_fr.txt"
        else -> "dict_en.txt" // en, auto, null, unknown → English
    }
```

### Step 2: Align Suggestions preference defaults (IME + Settings)

1. In `DictusImeService.onCreate` collector, change
   `SUGGESTIONS_ENABLED ?: false` → `?: true`.
2. Initialize `_suggestionsEnabled` to `true` (or keep false until first
   DataStore emit — either is OK if the collector default is `true`).
3. Confirm `SettingsViewModel` already uses `?: true`; leave it consistent.
   If any comment says "defaults to false — bar hidden in v1 UX", update that
   comment to say suggestions are on by default.

**Verify**: `rg -n 'SUGGESTIONS_ENABLED.*\?:' app ime` → both sites use `true`.

### Step 3: Add suggestion commit helper on the IME service

In `DictusImeService`, add a method used when the user taps a suggestion:

```kotlin
fun commitSuggestion(suggestion: String) {
    val ic = currentInputConnection ?: return
    val before = ic.getTextBeforeCursor(50, 0)?.toString().orEmpty()
    val currentWord = before.split(" ", "\n").lastOrNull().orEmpty()
    if (currentWord.isNotEmpty()) {
        ic.deleteSurroundingText(currentWord.length, 0)
    }
    ic.commitText("$suggestion ", 1)
    _currentWord.value = ""
    _suggestions.value = emptyList()
}
```

Also handle "tap current word" (left slot): commit nothing extra (word already
in field) OR finish composing equivalent — simply clear suggestion highlight
state (`_suggestions` empty is enough). Prefer: left slot no-ops on text
(already typed) and only clears the bar focus state.

**Verify**: add unit-testable pure helper if easier, e.g.
`suggestionReplacement(beforeCursor, suggestion)` returning
`(deleteCount, commitText)` — test that `"hel" + "hello"` → delete 3, commit
`"hello "`. Put tests in
`ime/src/test/java/com/gallopkeyboard/ime/suggestion/SuggestionCommitTest.kt`.

### Step 4: Wire SuggestionBar into KeyboardScreen

1. Add parameters to `KeyboardScreen`:

```kotlin
suggestionsEnabled: Boolean = true,
currentWord: String = "",
suggestions: List<String> = emptyList(),
onSuggestionSelected: (String) -> Unit = {},
onCurrentWordSelected: () -> Unit = {},
```

2. Above the `310.dp` keyboard column (or inside it at the top), when
   `suggestionsEnabled` is true, render:

```kotlin
SuggestionBar(
    currentWord = currentWord,
    suggestions = suggestions,
    onSuggestionSelected = onSuggestionSelected,
    onCurrentWordSelected = onCurrentWordSelected,
)
```

3. Height budget: SuggestionBar is `36.dp`. Prefer placing it **above** the
   fixed `310.dp` column so key sizes stay unchanged (total IME grows ~36.dp).
   Do **not** shrink key hit targets without measuring.

4. Hide the bar when emoji picker is open (already separate branch).

**Verify**: `./gradlew :ime:compileDebugKotlin` → success.

### Step 5: Pass state from DictusImeService.KeyboardContent

Inside the Idle `KeyboardScreen(` call:

```kotlin
val suggestionsEnabled by _suggestionsEnabled.collectAsState()
val currentWord by _currentWord.collectAsState()
val suggestions by _suggestions.collectAsState()
// ...
KeyboardScreen(
    // existing args...
    suggestionsEnabled = suggestionsEnabled,
    currentWord = currentWord,
    suggestions = suggestions,
    onSuggestionSelected = { commitSuggestion(it) },
    onCurrentWordSelected = { /* no-op or clear bar */ },
)
```

Ensure `collectAsState` imports already used in this file are reused.

**Verify**: `./gradlew :ime:testDebugUnitTest` → all pass.

### Step 6: Inventory + plans index

Append a short **Plan 019 additions** section to `docs/dictus-inventory.md`
listing wired UI + English default. Set this plan's status to DONE in
`plans/README.md`.

**Verify**: `bash scripts/verify.sh` → `OK`.

## Test plan

- `SuggestionCommitTest` — delete/replace cases: empty word, mid-word prefix,
  word after space, multiline last segment.
- `dictionaryAssetForLanguage` tests — `null`/`auto`/`en` → `dict_en.txt`;
  `fr` → `dict_fr.txt`.
- Existing `DictionaryEngineTest`, `SuggestionBarTest` (engine stub) still pass.
- Manual (owner): enable keyboard → type `hel` → see English suggestions →
  tap → field becomes `hello `.

## Done criteria

- [ ] `dictionaryAssetForLanguage` (or equivalent) defaults to English for
      missing/`auto`/`en`
- [ ] `SUGGESTIONS_ENABLED` defaults to `true` in both IME and SettingsViewModel
- [ ] `SuggestionBar` visible on typing panel when enabled
- [ ] Tapping a suggestion replaces the in-progress word and appends a space
- [ ] `./gradlew :ime:testDebugUnitTest` exits 0 with new tests
- [ ] `bash scripts/verify.sh` exits 0 / prints `OK`
- [ ] No files outside Scope modified (`git status`)
- [ ] `plans/README.md` status row updated; inventory appended

## STOP conditions

- `SuggestionBar` API or `KeyboardScreen` signature already wired differently
  than the excerpts (drift) — stop and reconcile.
- Placing the bar requires changing voice-panel height contracts in
  `PanelHost` / ADR height constants — stop; keep typing-only change.
- Dictionary assets `dict_en.txt` / `dict_fr.txt` missing from `ime` assets —
  stop and report.
- Fix appears to require LatinIME JNI — out of scope; stop.

## Maintenance notes

- Plan 023 (swipe decoder) should reuse the same `DictionaryEngine` /
  `SuggestionEngine` instance — consider exposing the engine via EntryPoint
  later; for this plan, service-local lazy is fine.
- If main-thread suggestion cost shows up on S22, revisit PERF early-exit in
  `DictionaryEngine.getSuggestions` (empty personal dict break condition).
- Reviewer should check IME height growth vs host apps (WhatsApp/Notes) and
  that clipboard strip + suggestion bar can coexist without clipping keys.
