#!/bin/sh
# Gradle launcher for BreezyCheatStation.
# - If gradle/wrapper/gradle-wrapper.jar exists, behaves like the standard wrapper.
# - Otherwise downloads the Gradle distribution once (needs curl + unzip) and runs it.
APP_HOME=$(cd "$(dirname "$0")" && pwd)
GRADLE_VERSION=8.10.2
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -f "$JAR" ]; then
  exec java -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
fi

CACHE="${GRADLE_BOOTSTRAP_DIR:-$HOME/.cache/breezy-cheat-station-gradle}"
DIST="$CACHE/gradle-$GRADLE_VERSION"
if [ ! -x "$DIST/bin/gradle" ]; then
  command -v curl  >/dev/null 2>&1 || { echo "ERROR: curl is required (Termux: pkg install curl)"; exit 1; }
  command -v unzip >/dev/null 2>&1 || { echo "ERROR: unzip is required (Termux: pkg install unzip)"; exit 1; }
  mkdir -p "$CACHE"
  echo "Downloading Gradle $GRADLE_VERSION ..."
  curl -fL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$CACHE/gradle.zip" || { echo "ERROR: Gradle download failed"; exit 1; }
  unzip -q -o "$CACHE/gradle.zip" -d "$CACHE" && rm -f "$CACHE/gradle.zip"
fi
exec "$DIST/bin/gradle" -p "$APP_HOME" "$@"
