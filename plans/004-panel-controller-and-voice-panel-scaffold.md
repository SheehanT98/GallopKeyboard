# Plan 004: PanelController + VoicePanel scaffold + typing-panel toggle

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to
> the next step. If anything in the "STOP conditions" section occurs,
> stop and report — do not improvise. When done, update the status row
> for this plan in `plans/README.md` — unless a reviewer dispatched you
> and told you they maintain the index.
>
> **Drift check (run first)**:
> ```
> bash scripts/verify.sh
> cat docs/dictus-inventory.md | head -40
> ```
> If `verify.sh` fails, an earlier plan didn't land — STOP. If
> `docs/dictus-inventory.md` doesn't exist, Plan 002 didn't complete
> Step 3 — STOP. Read the inventory before writing any code; the class
> names and file paths below assume you know what Dictus's IME entry
> point is called.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED
- **Depends on**: `plans/003-android-ci-and-sideload-baseline.md`
- **Category**: feature
- **Planned at**: commit `99ca844`, 2026-07-16
- **Issue**: —

## Why this matters

This is Phase 1 of `HANDOFF.md`: the visible frame for everything that
follows. Users need to see two panels in the keyboard — the existing
typing panel and a new voice panel — and switch between them with a
bottom-right toggle. Nothing has to actually record or transcribe yet;
Plans 005–007 wire that in. But the panel needs to exist, hold its
state, and not break Dictus's existing typing behavior.

Acceptance from `HANDOFF.md` (verbatim): "toggle works in Notes,
WhatsApp, Gmail" without dismissing the keyboard.

## Current state

Read these before touching code:

- `docs/dictus-inventory.md` — the source of truth for Dictus class
  names. The plan below uses **placeholder names**
  (`GallopKeyboardImeService`, `TypingPanelView`, etc.). Map them to
  the actual Dictus class names your inventory recorded. If the
  inventory named things you can't map, STOP and update the inventory
  first.
- `docs/adr/0002-hybrid-stt-pipeline.md` — voice panel is where the
  hybrid pipeline lives. Layout must leave room for the smart button
  (Plan 005) and a minimal toolbar row (⌨️ back to typing, and two
  placeholders for future Think/Search buttons).
- `HANDOFF.md` "UX spec (voice panel)" — the ASCII mockup is the
  spec. Match the layout: full-width smart button + a minimal row
  underneath.
- `AGENTS.md` — Kotlin + Compose + Material 3, English only.

The Dictus IME service extends Android's `InputMethodService` and
returns a keyboard view from `onCreateInputView()`. Whatever the class
is called in your inventory (`DictusInputMethodService` → renamed to
`GallopKeyboardImeService` in Plan 002), that's where we intercept.

## Commands you will need

| Purpose            | Command                                                | Expected                    |
|--------------------|--------------------------------------------------------|-----------------------------|
| Build              | `./gradlew --no-daemon assembleDebug`                  | BUILD SUCCESSFUL            |
| Unit test module   | `./gradlew --no-daemon :ime:testDebugUnitTest`         | BUILD SUCCESSFUL            |
| Full verify        | `bash scripts/verify.sh`                               | OK                          |
| Install to device  | `adb install -r app/build/outputs/apk/debug/app-debug.apk` | Success                 |
| Logcat filter      | `adb logcat -s GallopKeyboardIme:*`                    | streaming logs from IME     |

## Suggested executor toolkit

- The `vercel-react-best-practices` skill does not apply here (this is
  Android Compose, not React).
- If a `shadcn` or design-system skill is available, ignore it — Dictus
  uses Material 3, not shadcn.
- `docs/dictus-inventory.md` is your Rosetta stone. Read it, then
  `rg 'InputMethodService' --type kt` to sanity-check.

## Scope

**In scope** (all under `ime/src/main/`):
- `ime/src/main/java/com/gallopkeyboard/ime/panel/PanelState.kt` (new)
- `ime/src/main/java/com/gallopkeyboard/ime/panel/PanelController.kt` (new)
- `ime/src/main/java/com/gallopkeyboard/ime/panel/VoicePanel.kt` (new — Compose)
- `ime/src/main/java/com/gallopkeyboard/ime/panel/PanelHost.kt` (new — Compose root that switches typing ↔ voice)
- The existing IME service class (name per inventory) — edit
  `onCreateInputView()` to return `PanelHost`, and route lifecycle
  events (`onStartInputView`, `onFinishInputView`) into `PanelController`.
- The existing typing panel view (name per inventory) — add the
  bottom-right voice-toggle button. If typing panel is XML rather than
  Compose in Dictus, do the minimal surgery there; do not rewrite it.
- `ime/src/main/res/values/strings.xml` — new keys `panel_toggle_voice`,
  `panel_toggle_typing`, `voice_panel_placeholder_button`.
- `ime/src/test/java/com/gallopkeyboard/ime/panel/PanelControllerTest.kt` (new)
- `docs/dictus-inventory.md` — append a "Plan 004 additions" section
  naming the new classes so Plans 005–007 can find them.
- `plans/README.md` status row.

**Out of scope**:
- Any audio recording, permissions, or `RECORD_AUDIO` handling —
  Plan 005.
- Any ASR / model integration — Plans 006–008.
- Emoji, clipboard, or typing-panel visual polish — Plan 009.
- Changing Dictus's key layouts, keycap sizes, or symbol pages.
- Multi-window / floating overlay — HANDOFF locks it as in-IME.
- Landscape-specific layouts — v1 targets Galaxy S22 portrait; add a
  `TODO(landscape)` in the layout if you find yourself tempted, but
  do not implement it.

## Git workflow

- Branch: `advisor/004-panel-scaffold` off Plan 003.
- Commit granularity:
  1. `feat(ime): add PanelState and PanelController`
  2. `feat(ime): add VoicePanel placeholder Compose view`
  3. `feat(ime): add PanelHost and wire into IME service`
  4. `feat(ime): add voice-toggle button on typing panel`
  5. `test(ime): PanelController state transitions`
  6. `docs: record Plan 004 class additions in inventory`

## Steps

### Step 1: Design `PanelState` and `PanelController`

Create `panel/PanelState.kt`:

```kotlin
package com.gallopkeyboard.ime.panel

enum class PanelState { TYPING, VOICE }
```

Create `panel/PanelController.kt`:

- A single-instance controller owned by the IME service (Hilt-injected
  if the surrounding code uses Hilt; a manual singleton otherwise —
  match Dictus's DI style per inventory).
- Holds `state: StateFlow<PanelState>` starting at `PanelState.TYPING`.
- Method `toggle()`: flips between `TYPING` and `VOICE`.
- Method `showTyping()` / `showVoice()`: explicit transitions.
- Method `reset()`: called on `onFinishInputView` to return to `TYPING`
  so each new focused field opens with the typing panel (least
  surprising behavior; matches Gboard).
- No dependencies on Android context — this is pure state. That makes
  it unit-testable without Robolectric.

**Verify**: `./gradlew --no-daemon :ime:compileDebugKotlin` — success.

### Step 2: Voice panel placeholder Compose view

Create `panel/VoicePanel.kt`. It renders the ASCII mockup from
`HANDOFF.md` "UX spec (voice panel)":

- Root: `Box(Modifier.fillMaxWidth().height(keyboardHeight))`
- Center: a large rounded `Button` (~64.dp tall, 80% width) with
  `text = stringResource(R.string.voice_panel_placeholder_button)`
  ("Hold / Tap to speak"). This is a placeholder — no touch behavior
  yet. Just visual.
- Bottom row (`Row(Modifier.fillMaxWidth().padding(...))`):
  - Left: two disabled `TextButton`s labeled "Think?" and "Search?"
    (mock — HANDOFF explicitly marks them as future).
  - Right: an `IconButton` with a keyboard icon (Material icon
    `Icons.Filled.Keyboard`), `contentDescription =
    stringResource(R.string.panel_toggle_typing)`, that calls
    `onSwitchToTyping()` from a callback param.
- Colors: use Dictus's Material 3 color scheme — do NOT hardcode
  hex values. If Dictus defines a `GallopTheme` (rename from
  `DictusTheme`), wrap in it.

Height should equal the height Dictus's typing view returns from its
`View.onMeasure()`; look up the current value in Dictus's IME code
and use the same. Panel toggle must not change keyboard height (a
height change during focus causes visible jumps in host apps).

Include a `@Preview` composable with a fixed size so the file is
inspectable in Android Studio.

**Verify**:
- `./gradlew --no-daemon :ime:compileDebugKotlin` — success.
- `grep -n Icons.Filled.Keyboard ime/src/main/java/com/gallopkeyboard/ime/panel/VoicePanel.kt` — one match.

### Step 3: `PanelHost` — switches between typing and voice

Create `panel/PanelHost.kt`. This is the composable the IME service
returns from `onCreateInputView()`.

```kotlin
@Composable
fun PanelHost(
    controller: PanelController,
    typingContent: @Composable () -> Unit,   // wraps existing typing panel
) {
    val state by controller.state.collectAsState()
    when (state) {
        PanelState.TYPING -> typingContent()
        PanelState.VOICE  -> VoicePanel(onSwitchToTyping = controller::showTyping)
    }
}
```

For `typingContent`, wrap Dictus's existing typing view. Two cases:

**Case A**: Dictus's typing panel is already a Compose view. Pass it as
`typingContent = { ExistingTypingPanel() }`.

**Case B**: Dictus's typing panel is a classic `KeyboardView` (XML/
`View`). Wrap it with `AndroidView(factory = { context -> ExistingKeyboardView(context) })`.
Do not rewrite Dictus's typing panel in Compose — that's out of scope.

Update the IME service:

- In `onCreateInputView()`, return a `ComposeView` whose `setContent`
  installs `PanelHost(controller = panelController, typingContent = ...)`.
- In `onFinishInputView(finishingInput: Boolean)`, call
  `panelController.reset()`.
- In `onStartInputView(info, restarting)`: if `restarting` is false,
  call `panelController.reset()`. (Guarantees the typing panel shows
  on every fresh focus.)

**Verify**:
- `./gradlew --no-daemon assembleDebug` — success.
- `adb install -r ...`, open Notes, tap a text field — the keyboard
  appears and shows the typing panel (identical to Dictus behavior).
- Nothing crashes; `adb logcat -s GallopKeyboardIme:*` shows no
  exceptions.

### Step 4: Bottom-right voice-toggle button on typing panel

Find Dictus's typing panel implementation (from inventory). Add a
small mic-icon button (`Icons.Filled.Mic` from Material Icons or
Dictus's existing mic icon if present) anchored bottom-right of the
typing panel.

- Size: 40 dp × 40 dp with 8 dp padding from the edges.
- Icon: `Icons.Filled.Mic`.
- `contentDescription`: `stringResource(R.string.panel_toggle_voice)`
  ("Switch to voice panel").
- `onClick`: `panelController.showVoice()`.
- Placement: an `overlay` in Compose, or a `FrameLayout` overlay in the
  XML case. Do NOT push existing keys out of the way — the toggle
  overlays a corner that has no key.

Do NOT add haptic feedback yet (Plan 010 audits UX polish across the
whole app).

**Verify**:
- Rebuild + reinstall.
- In Notes, tap the mic in the bottom-right — keyboard switches to
  voice panel. Tap the ⌨️ in the voice panel — switches back to
  typing.
- In WhatsApp and Gmail (if installed), same behavior.
- Focus another field — voice panel is reset to typing (per
  `reset()` in Step 3).

### Step 5: Unit test `PanelController`

Create `ime/src/test/java/com/gallopkeyboard/ime/panel/PanelControllerTest.kt`.

Structural pattern: model after any existing test in
`ime/src/test/java/**` in Dictus (find one with `find ime/src/test -name "*Test.kt"`). If Dictus's IME module has no unit tests, model after
`core/src/test/java/**` instead. If neither has one, add JUnit 4 as the
test framework (`ime/build.gradle.kts` — `testImplementation("junit:junit:4.13.2")`) and add the standard Robolectric-free test config.

Test cases:

- `initial state is TYPING`
- `toggle flips TYPING to VOICE`
- `toggle from VOICE returns to TYPING`
- `showVoice sets state to VOICE`
- `showTyping sets state to TYPING`
- `reset returns to TYPING regardless of previous state`
- `state is a hot StateFlow (collectors receive current value on subscribe)`

Use `kotlinx-coroutines-test` if Dictus already includes it — check
`gradle/libs.versions.toml` before adding a new dependency.

**Verify**: `./gradlew --no-daemon :ime:testDebugUnitTest` — all pass;
7 new tests reported.

### Step 6: Update `docs/dictus-inventory.md`

Append a section `## Plan 004 additions`:

- Files added (paths).
- The IME service class (post-Plan-002 name) and the two methods edited.
- The typing-panel class edited.
- The `strings.xml` keys added.

This is what Plan 005 will read to find the controller and add the
smart-button/audio wiring.

### Step 7: Update `plans/README.md`

Change row `004` status to `DONE`.

## Test plan

Unit tests: see Step 5.

Manual on-device (Galaxy S22 — this is the only reliable acceptance):

| # | Action | Expected |
|---|--------|----------|
| 1 | Open Samsung Notes, tap a text field | GallopKeyboard opens on typing panel |
| 2 | Tap the bottom-right mic on the typing panel | Voice panel appears; keyboard height unchanged |
| 3 | Tap the ⌨️ in voice panel | Typing panel returns |
| 4 | Repeat 1–3 in WhatsApp | Same behavior |
| 5 | Repeat 1–3 in Gmail | Same behavior |
| 6 | While in voice panel, tap another text field | New focus opens on typing panel (reset) |
| 7 | Rotate device to landscape | STOP — landscape is out of scope; if rotation crashes, add a `TODO(landscape)` and defer; if it just looks bad, that's acceptable for v1 |

Record results (pass/fail) in the PR description if you open one.

## Done criteria

- [ ] `bash scripts/verify.sh` exits 0.
- [ ] `./gradlew :ime:testDebugUnitTest` passes; 7 new tests exist.
- [ ] APK installs and manual test 1–6 all pass on the target device
      (or on an emulator if no S22 available — note which).
- [ ] No new `RECORD_AUDIO` or audio-related code (that's Plan 005).
- [ ] `docs/dictus-inventory.md` updated with "Plan 004 additions".
- [ ] `plans/README.md` row for Plan 004 shows `DONE`.

## STOP conditions

- Dictus's IME service is not a straightforward
  `InputMethodService` subclass and instead uses a custom framework
  the inventory didn't call out — pause and update
  `docs/dictus-inventory.md`; the wrapping strategy in Steps 3–4 will
  need to change.
- Dictus already ships a voice panel (an "advanced mode" or similar)
  — do NOT layer ours on top blindly. Compare gesture behavior against
  ADR-0003; if it matches, reuse and rename. If not, note the
  divergence and stop for a design call.
- The keyboard height Dictus returns changes based on which panel is
  shown — this violates the "no dismiss / no jump" acceptance
  criterion. Report so a new plan can normalize height.
- Compose interop with Dictus's XML-based typing panel causes visible
  flicker on toggle — try `AndroidView`'s `update` block first; if
  that still flickers, STOP and report so we can consider a shared
  parent `FrameLayout` instead.
- Adding `androidx.compose.material3` or `androidx.compose.material.icons`
  requires bumping Compose or Kotlin versions — do NOT bump. Report
  the exact version conflict and defer.

## Maintenance notes

- `PanelController.reset()` on `onFinishInputView` is the "always open
  on typing" behavior. If the owner prefers "remember last panel per
  app," add a new plan — do not silently change reset semantics here.
- The voice panel's placeholder button will be replaced by the smart
  button in Plan 005. Keep the composable's file layout so Plan 005
  can swap in `SmartVoiceButton()` in one line.
- If Dictus later adds a real voice panel upstream, we'll need to
  reconcile — track that as a follow-up ADR when it happens; snapshot
  fork means it doesn't happen automatically.
- If the toggle button ever gets moved (e.g. to a settings gesture),
  keep `PanelController.showVoice()` as the single entry point so
  Plan 005's smart-button code doesn't have to track the caller.
