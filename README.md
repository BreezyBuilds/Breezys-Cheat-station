# Azahar Cheat Manager

An unofficial companion app for the any 3DS emulator. It finds the
games in your user folder, downloads matching Gateway/Citra-style cheat files from a
configurable GitHub source, and installs them into your designated 3ds emulators `cheats` folder — with automatic, verified backups before every change.

This app is not affiliated with, endorsed by, or supported by the Azahar or Citra projects, or by
Nintendo. It only reads and writes plain-text cheat files in the format Azahar/Citra already use;
it does not modify Azahar itself, download ROMs, or provide any game content.

## What it does

- **Finds your games.** Scans your `sdmc/Nintendo3DS` installed-titles folder and, optionally,
  a folder of `.cia` / `.3ds` / `.cci` / `.cxi` files, reading each title's real header (Title ID,
  product code, region, version) rather than guessing from the file name.
- **Fetches cheats.** Downloads Gateway-style cheat files for a game's Title ID from a GitHub
  repository (configurable — owner/repo/branch/path/file-name pattern), and caches them for
  offline use.
- **Installs safely.** Merges the cheats you pick into the game's existing `cheats/<TITLEID>.txt`
  file. Only the entries you touch are changed — anything else already in the file (including
  cheats from other tools) is preserved as-is. Every write is preceded by a verified backup of the
  previous file.
- **Warns about version mismatches.** If the cheat file's expected game version doesn't match what
  was detected, or the version couldn't be read at all, you're told before you install anything.
- **Backups and restore.** Every change creates a timestamped backup; Settings lets you list,
  restore, or delete them per game.

## Requirements

- An Android device or emulator, Android 10 (API 29) or newer.
- Any 3ds emulator installed, with its data folder (the one containing `sdmc`,
  `nand`, and `cheats`) accessible to file pickers on your device.
- To **build** the app: either GitHub Actions (no local setup at all) or a Linux/Termux
  environment with JDK 17 and the Android SDK command-line tools.

## Building with GitHub Actions (recommended, no local setup)

1. Push this project to a GitHub repository.
2. GitHub Actions will build it automatically on every push to `main`/`master`, on tags, and on
   pull requests (see `.github/workflows/build.yml`), or you can trigger it manually from the
   **Actions** tab ("Build APK" → **Run workflow**).
3. When the run finishes, open it and download the **AzaharCheatManager-debug.apk** artifact (and
   **AzaharCheatManager-release.apk** if you've configured a signing keystore — see below).
4. Copy the APK to your device and install it (you'll need to allow "install unknown apps" for
   whichever app you use to open it).

### Signing a release build (optional)

By default the release build is signed with the debug key, so it installs fine but isn't suitable
for redistribution. To sign it with your own key, add these **repository secrets** in
GitHub → Settings → Secrets and variables → Actions:

| Secret | Meaning |
|---|---|
| `KEYSTORE_PATH` | Path to a `.jks`/`.keystore` file checked into a private location the workflow can reach, or base64-decoded from another secret before the build step |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias inside the keystore |
| `KEY_PASSWORD` | Key password |

If any of these are missing, the release build silently falls back to debug signing — the build
never fails because of a missing keystore.

## Building in Termux (on-device, no computer needed)

```sh
pkg update
pkg install openjdk-17 aapt2 curl unzip git

# Android SDK command-line tools (one-time setup):
pkg install android-tools   # gives you adb for install.sh, optional
mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools
curl -fLO https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-*.zip && mv cmdline-tools latest
export ANDROID_HOME=~/android-sdk
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin"
yes | sdkmanager --licenses
sdkmanager "platforms;android-35" "build-tools;35.0.0"

# Build:
cd /path/to/AzaharCheatManager
./build.sh debug        # or: ./build.sh release   /   ./build.sh test
```

`build.sh` auto-detects Termux and points Gradle at Termux's native `aapt2` binary (Google's
`aapt2` is x86-64 only and won't run on an ARM phone). It also writes `local.properties` for you
and validates that the SDK platform/build-tools you need are actually installed, with the exact
`sdkmanager` command to fix it if not.

The finished APK is copied to `output/AzaharCheatManager-<mode>.apk`.

**About the Gradle wrapper:** this project ships a `gradlew` that behaves like the standard Gradle
wrapper *if* `gradle/wrapper/gradle-wrapper.jar` is present, but that binary jar couldn't be
generated in the environment this project was built in. Instead, `gradlew` transparently downloads
Gradle 8.10.2 itself (into `~/.cache/azahar-gradle`) the first time it runs, which needs `curl` and
`unzip` and an internet connection. If you have your own `gradle-wrapper.jar` (e.g. generated by
running `gradle wrapper` once with a system-installed Gradle), drop it into
`gradle/wrapper/gradle-wrapper.jar` and `gradlew` will use it directly with no download step.

## Building on Linux/macOS/WSL with Android Studio's SDK

Same as Termux, minus the `aapt2` override: set `ANDROID_HOME` to your existing SDK (e.g.
`~/Android/Sdk`), make sure `platforms;android-35` and `build-tools;35.0.0` are installed, then run
`./build.sh debug`. You do not need to open the project in Android Studio at all; it's a plain
Gradle project.

## Installing the APK

```sh
./install.sh debug      # installs output/AzaharCheatManager-debug.apk via adb, if a device is connected
```

Without a connected `adb` device, `install.sh` falls back to copying the APK to your device's
Downloads folder (Termux: run `termux-setup-storage` once first) and opening it with the system
installer. You can always just copy the APK manually and tap it in a file manager instead.

## First-time setup in the app

1. Open the app and tap **Select Azahar folder**. Pick the folder that contains `sdmc`, `nand`,
   and `cheats` (this is Azahar's user data directory).
2. The app scans for installed titles and any loose `.cia`/`.3ds`/`.cci`/`.cxi` files under that
   folder. You can also point it at a separate "games" folder in Settings if your ROM files live
   elsewhere, or add a game manually by Title ID if scanning finds nothing.
3. Tap a game to see available cheats (downloaded from the configured GitHub source) alongside
   whatever's already installed. Select the ones you want and tap **Install Selected**.

## How game detection works

For installed titles, the app reads the real NCCH/TMD headers under
`sdmc/Nintendo3DS/<id0>/<id1>/title/<high>/<low>/content` — Title ID, version, and (from the SMDH)
title/region — rather than trusting folder or file names. For loose `.cia`/`.3ds`/`.cci`/`.cxi`
files, it parses the NCSD/CIA container header the same way; the file name is only used as a last
resort if the header can't be read (e.g. an encrypted or unusual dump), and only to extract a
Title ID pattern from it.

## How the cheat source works

The default source is a public GitHub repository of Gateway/CTRPluginFramework-style cheat files,
one per Title ID. You can point the app at a different repository (owner, repo, branch, path
inside the repo, and file name pattern) in **Settings → Cheat source** — useful if you maintain
your own cheat collection or prefer a different public one. An optional personal access token can
be set if you're hitting GitHub's unauthenticated rate limit.

## How backups work

Before any write to a game's cheat file, the previous contents (if any) are saved to
`<cheats>/backups/<TITLEID>_cheats_backup_YYYYMMDD_HHMMSS.txt` and verified by reading it back. If
the write itself then fails or the on-disk result doesn't match what was written, the previous
content is automatically restored. Backups are pruned to the 25 most recent per title; you can
list, restore, or delete them from **Settings → Backups**.

## Troubleshooting

- **"Azahar folder not found automatically."** Android 11+ blocks apps from browsing another app's
  private `Android/data` folder through the file picker. If Azahar stores its data there and gives
  you no way to relocate it, you may need to use Azahar's own "change user folder" option (if it
  has one) to move its data somewhere the picker can reach, or use a file manager with
  "All files access" to copy/symlink it. This is an Android platform restriction, not something
  this app can bypass.
- **A game shows "Version unknown (could not be verified)".** Some dumps are encrypted in a way
  that hides the TMD/NCCH version field. Cheats will still install, but the compatibility check is
  skipped and you'll see a warning instead of a confirmed match.
- **"No cheats found in the cheat source."** Not every Title ID has a matching file in the
  configured repository. Try a different source in Settings, or add cheats manually by editing the
  installed file directly (they're plain text).
- **GitHub errors / rate limited.** Unauthenticated GitHub API/raw requests are rate-limited per
  IP. Add a personal access token in Settings → Cheat source to raise the limit, or wait and use
  **Refresh cheat database** later — the app falls back to whatever it last cached.
- **`./gradlew` wants to download something on first run.** See "About the Gradle wrapper" above —
  this is expected the first time, and only that one time per machine.

## Known limitations

- No wrapper JAR binary is vendored (see above); the first build on a new machine needs internet
  access to fetch Gradle itself, separately from the app's own dependencies.
- Encrypted ROM headers can prevent reading a title's real name, region, or version; the app falls
  back to Title ID only in that case, and cheats install without a compatibility check.
- The default cheat source is a general-purpose Gateway/CTRPF-style cheat repository; formats
  across different community sources vary slightly, and not every cheat published elsewhere will
  necessarily be 100% compatible with Azahar's cheat engine. Always check a cheat's own notes.
- Unauthenticated GitHub requests are subject to GitHub's standard public rate limits.
- This app only edits cheat files; it does not launch, configure, or otherwise integrate with
  Azahar beyond sharing its data folder.

## License

See [LICENSE](LICENSE).
