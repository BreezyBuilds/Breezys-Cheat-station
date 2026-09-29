#!/usr/bin/env bash
# Command-line build for Azahar Cheat Manager (Termux, Linux, macOS, WSL).
# Usage: ./build.sh [debug|release|test]     (default: debug)
set -euo pipefail
cd "$(dirname "$0")"

MODE="${1:-debug}"
fail() { echo "ERROR: $*" >&2; exit 1; }
info() { echo "==> $*"; }

IS_TERMUX=0
if [[ -n "${TERMUX_VERSION:-}" || -d /data/data/com.termux/files/usr ]]; then IS_TERMUX=1; fi

# ---- Java ---------------------------------------------------------------
command -v java >/dev/null 2>&1 || {
  if [[ $IS_TERMUX == 1 ]]; then fail "Java not found. Run: pkg install openjdk-17"
  else fail "Java not found. Install a JDK 17 (e.g. sudo apt install openjdk-17-jdk)."; fi
}
JV=$(java -version 2>&1 | head -n1 | sed -E 's/.*"([0-9]+)[."].*/\1/')
[[ "$JV" =~ ^[0-9]+$ ]] || JV=0
(( JV >= 17 )) || fail "JDK 17 or newer is required (found major version $JV)."
info "Java $JV OK"

# ---- Android SDK --------------------------------------------------------
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK" && -f local.properties ]]; then
  SDK=$(grep -E '^sdk.dir=' local.properties | head -n1 | cut -d= -f2- || true)
fi
if [[ -z "$SDK" ]]; then
  for c in "$HOME/android-sdk" "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" "/opt/android-sdk"; do
    [[ -d "$c" ]] && SDK="$c" && break
  done
fi
[[ -n "$SDK" && -d "$SDK" ]] || fail "Android SDK not found.
Set ANDROID_HOME to an SDK folder that contains 'platforms/android-35' and 'build-tools/'.
Termux: see README.md ('Building in Termux') for the exact commands.
Easiest alternative: build with GitHub Actions (no local SDK needed)."
[[ -d "$SDK/platforms/android-35" ]] || fail "Missing SDK platform: $SDK/platforms/android-35
Install it with: sdkmanager \"platforms;android-35\" \"build-tools;35.0.0\""
[[ -d "$SDK/build-tools" && -n "$(ls -A "$SDK/build-tools" 2>/dev/null)" ]] || fail "Missing SDK build-tools in $SDK/build-tools
Install with: sdkmanager \"build-tools;35.0.0\""
export ANDROID_HOME="$SDK"
echo "sdk.dir=$SDK" > local.properties
info "Android SDK: $SDK"

# ---- Gradle -------------------------------------------------------------
[[ -x ./gradlew ]] || chmod +x ./gradlew
EXTRA=()
if [[ $IS_TERMUX == 1 ]]; then
  # Google's aapt2 binary is x86-64 only; Termux ships a native one.
  AAPT2="${PREFIX:-/data/data/com.termux/files/usr}/bin/aapt2"
  [[ -x "$AAPT2" ]] || fail "aapt2 not found. Run: pkg install aapt2"
  EXTRA+=("-Pandroid.aapt2FromMavenOverride=$AAPT2")
  info "Using Termux aapt2: $AAPT2"
fi

case "$MODE" in
  debug)   TASKS=(assembleDebug) ;;
  release) TASKS=(assembleRelease) ;;
  test)    TASKS=(testDebugUnitTest) ;;
  *) fail "Unknown mode '$MODE' (use debug, release or test)" ;;
esac

info "Running Gradle: ${TASKS[*]}"
./gradlew --no-daemon "${EXTRA[@]}" "${TASKS[@]}"

if [[ "$MODE" == "test" ]]; then info "Unit tests passed."; exit 0; fi

APK=$(find "app/build/outputs/apk/$MODE" -name '*.apk' 2>/dev/null | head -n1 || true)
[[ -n "$APK" ]] || fail "Build finished but no APK was found under app/build/outputs/apk/$MODE"
mkdir -p output
OUT="output/AzaharCheatManager-$MODE.apk"
cp "$APK" "$OUT"
info "Done. APK: $(pwd)/$OUT"
echo "Install it with ./install.sh (ADB) or open the file from your file manager."
