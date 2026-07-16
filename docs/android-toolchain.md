# Android toolchain

GallopKeyboard Plans 002+ require JDK 17, Android SDK 34, and Gradle wrapper (added in Plan 002).

## Cloud agents

`.cursor/environment.json` runs on every agent startup:

```bash
npm install && bash scripts/setup-android.sh
```

The Dockerfile at `.cursor/Dockerfile` pre-installs the same SDK packages for faster cold starts.

## Local setup

```bash
npm run android:setup
source scripts/android-env.sh
echo "$ANDROID_HOME"   # should be non-empty
java -version          # should report 17 when JAVA_HOME is set
```

## Before Gradle (Plans 002+)

Always source the env file in the same shell:

```bash
source scripts/android-env.sh
./gradlew --no-daemon assembleDebug
```

## Packages installed

| Package | Purpose |
|---------|---------|
| `platform-tools` | `adb` for sideload (Plan 003) |
| `platforms;android-34` | Compile target (Dictus minSDK 29) |
| `build-tools;34.0.0` | APK build |

## Troubleshooting

- **`ANDROID_HOME` empty** — run `npm run android:setup`
- **`sdkmanager` not found** — re-run setup; ensure `cmdline-tools/latest` exists under `$ANDROID_HOME`
- **Gradle uses wrong JDK** — `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` (Linux) before `./gradlew`
