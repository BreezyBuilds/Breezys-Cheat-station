# Retro Loader v2.1 — consolidated UI patch

This source overlay retains the existing emulator, scanner, repository, and cheat installation implementations. UI-only modifications:

- Centralized the soft press/haptic behavior in `Ui.interact`.
- Applied the same behavior to `Ui.button`, including compact buttons, so shared buttons and selection rows have the same response.
- Kept Navy/OLED Black/Grey Light preferences and the three existing locally rendered console icon styles.
- Kept selected-state ticks and all existing appearance preference keys.

## Apply in Termux

```sh
cd ~/Breezys-Cheat-station
mkdir -p ~/storage/downloads
cp -a app/src/main/java/com/breezybuilds/cheatstation/ui/Ui.kt app/src/main/java/com/breezybuilds/cheatstation/ui/Ui.kt.before-v2.1
# Extract this archive at the repository root:
tar -xzf ~/storage/downloads/Breezy-Retro-Loader-v2.1-UI-patch.tar.gz
./gradlew assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/BreezyCheatStation.apk
```

This is an incremental, source-level UI patch, **not** a verified complete screen-by-screen overhaul. Build and device testing are required; the uploaded source did not contain the Gradle wrapper or full project files.
