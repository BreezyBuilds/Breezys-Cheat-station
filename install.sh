#!/usr/bin/env bash
# Installs the built APK. Usage: ./install.sh [debug|release|path/to.apk]
set -euo pipefail
cd "$(dirname "$0")"
ARG="${1:-debug}"
if [[ -f "$ARG" ]]; then APK="$ARG"; else APK="output/AzaharCheatManager-$ARG.apk"; fi
[[ -f "$APK" ]] || { echo "ERROR: $APK not found. Run ./build.sh first." >&2; exit 1; }

if command -v adb >/dev/null 2>&1 && adb devices | awk 'NR>1 && $2=="device"' | grep -q .; then
  echo "==> Installing with ADB: $APK"
  adb install -r "$APK"
  echo "Installed."
  exit 0
fi

echo "No ADB device connected."
if [[ -n "${TERMUX_VERSION:-}" || -d /data/data/com.termux/files/usr ]]; then
  echo "Termux options:"
  echo "  1) Wireless debugging: pkg install android-tools, then 'adb pair HOST:PORT' and"
  echo "     'adb connect HOST:PORT' (Developer options > Wireless debugging), then rerun ./install.sh"
  echo "  2) Manual install: opening the APK with the system installer."
  DEST=""
  for d in "$HOME/storage/downloads" /sdcard/Download; do [[ -d "$d" && -w "$d" ]] && DEST="$d" && break; done
  if [[ -n "$DEST" ]]; then
    cp "$APK" "$DEST/AzaharCheatManager.apk" && echo "Copied to $DEST/AzaharCheatManager.apk"
    command -v termux-open >/dev/null 2>&1 && termux-open "$DEST/AzaharCheatManager.apk" || echo "Open it from your Files app and allow 'install unknown apps'."
  else
    echo "Run 'termux-setup-storage' first so the APK can be copied to Downloads."
  fi
else
  echo "Connect a device with USB debugging enabled, or copy $APK to your phone and open it."
  exit 1
fi
