# Job-020 code — Plan 030

## Implementation notes

### Step 1 — English dictionary

`dictionaryAssetForLanguage(lang)` in `DictionaryEngine.kt`:

- `"fr"` → `dict_fr.txt`
- everything else (`null`, `"auto"`, `"en"`, unknown) → `dict_en.txt`

### Step 2 — Pref default

`DictusImeService` observes `SUGGESTIONS_ENABLED ?: true`, matching
`SettingsViewModel`. Initial `MutableStateFlow` default set to `true`.

### Step 3 — Suggestion bar wiring

`KeyboardScreen` new params: `suggestionsEnabled`, `currentWord`, `suggestions`,
`onSuggestionSelected`, `onCurrentWordSelected`.

Layout order (top → bottom): `MicButtonRow` → `SuggestionBar` (when on) →
`ClipboardStrip` → `KeyboardView`.

`DictusImeService.KeyboardContent` collects `_suggestionsEnabled`, `_currentWord`,
`_suggestions` and wires handlers:

- **Suggestion tap**: delete typed fragment, commit `"$word "`, clear autocorrect undo
- **Current-word tap**: commit trailing space on raw input

### Personal dictionary (optional)

`recordWordTyped` on space commit (no autocorrect / JustSpace) and suggestion
selection.

## Tests added

`DictionaryEngineTest.dictionaryAssetForLanguage maps language codes to bundled assets`
