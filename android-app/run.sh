#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
ADB="$SDK_DIR/platform-tools/adb"
PACKAGE_NAME="dev.pages.paxx12.spoollink"
MAIN_ACTIVITY="$PACKAGE_NAME/.MainActivity"
GRADLE_VERSION="8.10.2"
GRADLE_HOME="$ROOT_DIR/.gradle/gradle-$GRADLE_VERSION"
GRADLE_BIN="$GRADLE_HOME/bin/gradle"

if [[ -x "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java" ]]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi

if [[ ! -x "$ADB" ]]; then
    echo "adb not found at $ADB" >&2
    echo "Install Android platform-tools or set ANDROID_HOME/ANDROID_SDK_ROOT." >&2
    exit 1
fi

if [[ ! -x "$GRADLE_BIN" ]]; then
    mkdir -p "$ROOT_DIR/.gradle"
    ZIP="$ROOT_DIR/.gradle/gradle-$GRADLE_VERSION-bin.zip"
    URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
    echo "Downloading Gradle $GRADLE_VERSION..."
    curl -fL "$URL" -o "$ZIP"
    unzip -q "$ZIP" -d "$ROOT_DIR/.gradle"
fi

mapfile -t DEVICES < <("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [[ ${#DEVICES[@]} -eq 0 ]]; then
    echo "No authorized Android device found." >&2
    echo "Connect a device, enable USB debugging, then run: $ADB devices -l" >&2
    exit 1
fi
if [[ ${#DEVICES[@]} -gt 1 && -z "${ANDROID_SERIAL:-}" ]]; then
    echo "Multiple devices found. Set ANDROID_SERIAL to one of:" >&2
    printf '  %s\n' "${DEVICES[@]}" >&2
    exit 1
fi

export ANDROID_SERIAL="${ANDROID_SERIAL:-${DEVICES[0]}}"
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"

"$GRADLE_BIN" -p "$ROOT_DIR" :app:installDebug
"$ADB" -s "$ANDROID_SERIAL" shell am start -n "$MAIN_ACTIVITY"
sleep 1
"$ADB" -s "$ANDROID_SERIAL" shell pidof "$PACKAGE_NAME" >/dev/null

echo "Launched $PACKAGE_NAME on $ANDROID_SERIAL"
