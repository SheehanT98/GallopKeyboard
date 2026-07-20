# Job-022 code — Plan 033

## Step 1 — Async mic permission

`SmartVoiceButton` gesture handler:

- If `RECORD_AUDIO` missing: launch `permissionRequester.request(context)` on
  Compose `scope` (not pointer thread).
- **Second-press pattern**: on grant, user taps again to start recording (avoids
  FSM races with hold threshold while permission dialog is open).
- `permissionRequestInFlight` prevents duplicate concurrent requests.
- Denied → toast; gesture returns without blocking.

## Step 2 — Pin IC through polish

`InputConnectionSupplier`:

- `beginPolishCommit()` captures live `supplier()` into `pinnedConnection`.
- `connection()` returns pinned IC when set, else live supplier.
- `clearSupplierIfIdle()` — used from `onFinishInputView`; defers nulling
  supplier while `polishInFlight`; applies deferred clear in `endPolishCommit()`.

`PolishingTranscriber.onSessionStop`:

- After streaming finalize, `beginPolishCommit()` → polish → commit →
  `endPolishCommit()` in `finally`.

`ImeTextCommitter`:

- Checks `InputConnection` method return values; logs and drops on inactive IC.

## Tests added

- `InputConnectionSupplierTest` — pin survives supplier clear; defer clear while polish in flight.
- `PolishingTranscriberTest.polish commit uses pinned IC when supplier cleared mid-polish` — blocks polish engine, nulls supplier mid-flight, asserts fake IC received text.
