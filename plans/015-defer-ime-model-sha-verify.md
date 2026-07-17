# Plan 015: Move daily model SHA-256 verify off the IME `onCreate` critical path

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**:
> ```
> git diff --stat 3571aab..HEAD -- \
>   ime/src/main/java/com/gallopkeyboard/ime/DictusImeService.kt \
>   core/src/main/java/com/gallopkeyboard/core/models/ModelInstaller.kt \
>   ime/src/main/java/com/gallopkeyboard/ime/panel/PanelHost.kt \
>   core/src/test
> ```
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none (can land in parallel with 014)
- **Category**: perf
- **Planned at**: commit `3571aab`, 2026-07-17
- **Issue**: —

## Why this matters

`DictusImeService.onCreate` calls `ModelInstaller.verifyInstalledIfDue()`,
which — once per 24 h — SHA-256s the entire default voice bundle (~220 MB
across Parakeet + Whisper files) **synchronously on the IME service create
path**. That stalls keyboard bring-up on the day the verify is due and
risks ANR / "keyboard won't open" reports. The codebase already documents
the intended split: hot path uses size/existence checks only; full SHA is
for settings / daily background work (`areFilesPresent` KDoc and
`PanelHost` comment).

After this plan: IME `onCreate` only does the cheap `areFilesPresent`
check (already present). Full SHA verify runs on a background executor /
coroutine, never blocks first paint of the input view, and still updates
the once-per-day preference + corrupt detection.

## Current state

`ime/src/main/java/com/gallopkeyboard/ime/DictusImeService.kt` (~163–175):

```kotlin
override fun onCreate() {
    super.onCreate()
    CrashHandler.install(this)
    // ...
    bindDictationService()

    val installer = ModelInstaller(applicationContext)
    installer.verifyInstalledIfDue()
    if (!installer.areFilesPresent(ModelRegistry.defaultVoiceBundle)) {
        entryPoint.voiceModelPromptState().showBanner()
    }
    // ...
}
```

`core/src/main/java/com/gallopkeyboard/core/models/ModelInstaller.kt`:

```kotlin
/**
 * Fast readiness check for the IME hot path (voice panel open).
 * Only checks that each file exists and matches the expected byte length —
 * no SHA-256. Full integrity stays on [isInstalled] / [verifyInstalledIfDue]
 * (settings + daily IME startup).
 */
fun areFilesPresent(bundle: List<ModelSpec>): Boolean = ...

fun verifyInstalledIfDue(): Boolean {
    val now = System.currentTimeMillis()
    val last = prefs.getLong(KEY_LAST_VERIFY_MS, 0L)
    if (now - last < VERIFY_INTERVAL_MS) return false
    prefs.edit().putLong(KEY_LAST_VERIFY_MS, now).apply()
    return ModelRegistry.defaultVoiceBundle.any { spec ->
        fileStatus(spec) == ModelFileStatus.Corrupt
    }
}
```

`fileStatus` always SHA-256s the whole file via `sha256Of`.

`PanelHost.kt` already comments: `// Cheap presence check (exists + size). Full SHA is daily / settings only.`

### Conventions

- Prefer `bindingScope.launch(Dispatchers.IO)` already used on
  `DictusImeService` for DataStore collection — same scope pattern.
- Do not invent a new preference key unless needed; keep
  `KEY_LAST_VERIFY_MS` / `VERIFY_INTERVAL_MS`.
- English comments only; match existing KDoc tone.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Core unit tests | `./gradlew :core:testDebugUnitTest` | BUILD SUCCESSFUL |
| IME unit tests | `./gradlew :ime:testDebugUnitTest` | BUILD SUCCESSFUL |
| Full verify | `bash scripts/verify.sh` | ends with `OK` |
| Grep | `rg -n "verifyInstalledIfDue" ime/src/main app/src/main` | IME call is async / deferred; settings may still sync |

## Scope

**In scope**:

- `ime/src/main/java/com/gallopkeyboard/ime/DictusImeService.kt`
- `core/src/main/java/com/gallopkeyboard/core/models/ModelInstaller.kt`
  (KDoc + optional `verifyInstalledIfDueAsync`-style helper if it keeps
  the service thinner — helper must stay in `core`)
- Tests under `core/src/test/` for `ModelInstaller` if a test harness
  already exists; otherwise add a small JVM/Robolectric test next to
  existing core model tests (create only if none exist — check first)
- `docs/dictus-inventory.md` — "Plan 015 additions"
- `plans/README.md` — status row

**Out of scope**:

- Changing SHA pins / `ModelRegistry` URLs
- Reworking download UX (`OnboardingActivity` / `ModelInstaller.download*`)
- Streaming ASR threading (Plan 014)
- Disabling verify entirely

## Git workflow

- Branch: `cursor/defer-ime-model-sha-verify-1534`
- Commit style: `perf(ime): defer daily model SHA verify off onCreate`
- Do NOT push/PR unless instructed

## Steps

### Step 1: Remove synchronous verify from IME `onCreate`

In `DictusImeService.onCreate`:

1. Keep the cheap `areFilesPresent` banner check **on the create path**.
2. Replace `installer.verifyInstalledIfDue()` with a background launch on
   `bindingScope` using `Dispatchers.IO` (or `Dispatchers.Default`).
3. If corrupt is detected, show the existing voice-model banner /
   `VoiceModelPromptState` the same way missing files do — post UI work
   back to Main if required.

Target shape:

```kotlin
val installer = ModelInstaller(applicationContext)
if (!installer.areFilesPresent(ModelRegistry.defaultVoiceBundle)) {
    entryPoint.voiceModelPromptState().showBanner()
}
bindingScope.launch(Dispatchers.IO) {
    val corrupt = installer.verifyInstalledIfDue()
    if (corrupt) {
        // hop to Main if promptState requires it
        entryPoint.voiceModelPromptState().showBanner()
    }
}
```

Update `ModelInstaller` KDoc: daily verify is **scheduled from IME
startup off the critical path**, not "IME startup" synchronous.

**Important**: `verifyInstalledIfDue` currently writes
`KEY_LAST_VERIFY_MS` **before** hashing. Keep that behavior (or document
a deliberate change). Do not hash twice.

**Verify**:
```
rg -n "verifyInstalledIfDue" ime/src/main/java/com/gallopkeyboard/ime/DictusImeService.kt
```
→ only appears inside `bindingScope.launch` (or equivalent), not as a
bare call before `areFilesPresent`.

### Step 2: Confirm settings / other callers

Search the repo for other `verifyInstalledIfDue` call sites. Settings UI
may keep a **user-initiated** synchronous or progress-indicated verify —
that is fine. Do not remove those without replacement.

**Verify**: `rg -n "verifyInstalledIfDue" --glob '*.kt'` → every call site
is intentional (background IME **or** explicit settings action).

### Step 3: Tests

If `ModelInstaller` has no unit tests, add a focused test that:

- With `last_verify_ms` recent → `verifyInstalledIfDue()` returns false
  quickly without needing real model files (temp dir / fake filesRoot if
  the class allows injection; if `filesRoot` is hard-wired to
  `context.filesDir`, use Robolectric Application context and empty
  files → not Corrupt if missing? Check: `fileStatus` returns `Missing`
  not `Corrupt` for absent files — corrupt path needs a file with wrong
  hash. Create a tiny temp file with wrong content + matching
  `ModelSpec` only if the installer API allows overriding registry —
  **if that requires large refactor, skip and rely on integration
  grep + verify.sh**, and note it in the inventory).

Prefer not inventing a huge test harness. Minimum bar: compile +
`verify.sh` green + grep proof of async call.

**Verify**: `./gradlew :ime:testDebugUnitTest :core:testDebugUnitTest` → SUCCESS.

### Step 4: Inventory + full verify

Append "Plan 015 additions" to `docs/dictus-inventory.md`.

**Verify**: `bash scripts/verify.sh` → `OK`.

## Test plan

- Prefer extending existing core model tests if present.
- Manual (document in inventory, not automated): cold-start IME on a
  device with models installed on a "verify due" day — keyboard chrome
  appears without multi-second freeze; logcat may show verify completing
  afterward.

## Done criteria

- [ ] `DictusImeService.onCreate` does not call `verifyInstalledIfDue()`
      on the calling thread before returning
- [ ] Cheap `areFilesPresent` banner check remains
- [ ] Corrupt detection still triggers the voice-model banner
- [ ] `bash scripts/verify.sh` → `OK`
- [ ] Inventory section present; `plans/README.md` 015 → `DONE`
- [ ] No out-of-scope files modified

## STOP conditions

- `bindingScope` is cancelled so early that verify never runs and you
  cannot find another long-lived scope without redesigning service
  lifecycle — report options.
- Drift in `ModelInstaller` API / preference keys.
- Fix seems to require deleting daily verify entirely.

## Maintenance notes

- Reviewers: ensure `KEY_LAST_VERIFY_MS` is not stamped on Main then
  immediately re-read incorrectly under races (single writer is fine).
- If model bundle size grows, keep SHA work off any IME binder / create
  path forever.
