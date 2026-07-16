#!/usr/bin/env bash
# Install Android SDK + JDK 17 for GallopKeyboard Gradle builds (Plans 002+).
# Idempotent — safe to re-run on cloud agent startup (.cursor/environment.json).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-11076708_latest.zip"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}"
MARKER="$ANDROID_HOME/.gallopkeyboard-setup-complete"

export ANDROID_HOME

echo "==> GallopKeyboard Android toolchain setup"
echo "    ANDROID_HOME=$ANDROID_HOME"

if ! command -v java >/dev/null 2>&1; then
	echo "Error: java not found" >&2
	exit 1
fi

# JDK 17 matches CI (.github/workflows/ci.yml in Plan 003) and Dictus AGP expectations.
if ! java -version 2>&1 | grep -qE 'version "17'; then
	if command -v apt-get >/dev/null 2>&1; then
		echo "==> Installing OpenJDK 17"
		sudo apt-get update -qq
		sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-17-jdk wget unzip ca-certificates
	fi
fi

if [ -d /usr/lib/jvm/java-17-openjdk-amd64 ]; then
	export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
elif [ -d /usr/lib/jvm/java-17-openjdk ]; then
	export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
fi

mkdir -p "$ANDROID_HOME/cmdline-tools"

if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
	echo "==> Downloading Android command-line tools"
	tmp="$(mktemp -d)"
	trap 'rm -rf "$tmp"' EXIT
	wget -q "$CMDLINE_TOOLS_URL" -O "$tmp/$CMDLINE_TOOLS_ZIP"
	unzip -q "$tmp/$CMDLINE_TOOLS_ZIP" -d "$tmp/extract"
	rm -rf "$ANDROID_HOME/cmdline-tools/latest"
	mv "$tmp/extract/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi

export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

if [ ! -f "$MARKER" ]; then
	echo "==> Accepting SDK licenses and installing packages"
	yes | sdkmanager --licenses >/dev/null 2>&1 || true
	sdkmanager \
		"platform-tools" \
		"platforms;android-34" \
		"build-tools;34.0.0"
	touch "$MARKER"
else
	echo "==> SDK packages already installed (marker present)"
fi

ENV_FILE="$ROOT/scripts/android-env.sh"
cat >"$ENV_FILE" <<'EOF'
# Source before Gradle: `source scripts/android-env.sh`
# Generated/updated by `npm run android:setup` — safe to re-run setup.
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
EOF
chmod +x "$ENV_FILE"

# Persist for interactive shells in cloud agents
if [ -d "$HOME/.bashrc" ] || [ -f "$HOME/.bashrc" ]; then
	grep -q 'gallopkeyboard android-env' "$HOME/.bashrc" 2>/dev/null || {
		cat >>"$HOME/.bashrc" <<'BASHRC'

# gallopkeyboard android-env (scripts/setup-android.sh)
for _gallop_android_env in "$PWD/scripts/android-env.sh" "/workspace/scripts/android-env.sh"; do
  if [ -f "$_gallop_android_env" ]; then
    # shellcheck source=/dev/null
    source "$_gallop_android_env"
    break
  fi
done
unset _gallop_android_env
BASHRC
	}
fi

# shellcheck source=/dev/null
source "$ENV_FILE"

echo "==> Verification"
echo "ANDROID_HOME=$ANDROID_HOME"
echo "JAVA_HOME=${JAVA_HOME:-<default>}"
java -version 2>&1 | head -1
adb version 2>&1 | head -1
sdkmanager --list_installed 2>/dev/null | grep -E 'platform-tools|platforms;android-34|build-tools;34.0.0' || true
echo "==> Android toolchain ready"
