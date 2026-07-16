# Release signing (Plan 010)

GallopKeyboard v1 ships as a sideload APK. Release builds are signed locally;
keystore material is never committed to git.

## Generate a keystore (one-time)

```bash
mkdir -p ~/.gallopkeyboard
keytool -genkey -v \
  -keystore ~/.gallopkeyboard/keystore.jks \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -alias gallop-release
```

## Configure signing properties

Create `~/.gallopkeyboard/keystore.properties`:

```properties
storeFile=/home/<you>/.gallopkeyboard/keystore.jks
storePassword=***
keyAlias=gallop-release
keyPassword=***
```

Use an absolute path for `storeFile`.

## Build and install

```bash
./gradlew --no-daemon assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

The release APK packages **arm64-v8a only** (modern phones including Galaxy S22).
That drops ~17 MB of unused 32-bit JNI versus a universal APK. 32-bit-only
devices are not supported in v1 — see [`limitations.md`](limitations.md).

Install the same way as debug:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## CI / no keystore

If `keystore.properties` is absent and `SIGNING_KEYSTORE_PATH` is unset, the
release build falls back to the **debug** signing config so CI and agents can
still produce a testable APK.

## Backup

Back up `keystore.jks` and `keystore.properties` in at least two safe locations.
Losing the release key means existing installs cannot be updated in place — users
must uninstall and reinstall (losing local data).

## Environment variables (optional)

CI may still use:

| Variable | Purpose |
|----------|---------|
| `SIGNING_KEYSTORE_PATH` | Path to `.jks` file |
| `SIGNING_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | Key alias |
| `SIGNING_KEY_PASSWORD` | Key password |

`~/.gallopkeyboard/keystore.properties` takes precedence when present.
