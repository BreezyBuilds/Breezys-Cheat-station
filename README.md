# Breezy's Cheat Station

An easy way to add cheats to your 3DS emulator games.

Breezy's Cheat Station looks at the games you already have in the emulator, finds matching cheat codes
online, and installs the ones you pick — automatically backing up your existing cheats first, so
you can always undo a change.

This is an independent app. It isn't made by, endorsed by, or officially connected to any emulator project,
Nintendo, or the emulator scene in general — it just works alongside them.

## What it does

- **Finds your games automatically** — no typing in game names or IDs by hand.
- **Downloads cheats for you** — pulls matching cheat codes from an online cheat database.
- **One tap to install** — pick the cheats you want, tap Install, done.
- **Never loses your existing cheats** — every change is backed up first, and can be undone from
  the Backups screen.
- **Tells you about version mismatches** — if a cheat might not match your copy of the game, you'll
  see a warning before installing.

## Getting started

1. **Install the app** (see "Getting the app" below if you don't have it yet).
2. Open it and tap **Select 3DS emulator data folder**. Pick the folder on your device that has the emulator's data
   in it — the one containing `sdmc`, `nand`, and `cheats` inside it.
3. The app will scan and show you your games.
4. Tap a game to see what cheats are available for it.
5. Select the ones you want and tap **Install Selected**.
6. Launch the game in your emulator — your cheats will be active.

That's it. If a game doesn't have cheats available in the current source, you can try a different
cheat source under **Settings → Cheat source**.

## Undoing a change

Every time the app changes a cheat file, it saves a backup of what was there before. If something
goes wrong, or you just want to go back:

1. Open **Settings → Backups**.
2. Find the game.
3. Tap **Restore** on the backup you want.

## Common questions

**A game isn't showing up.**
Make sure you selected the emulator's actual data folder (the one with `sdmc`, `nand`, and `cheats`
inside). If your ROM files live somewhere else, you can also point the app at a separate "games"
folder in Settings.

**It says "no cheats found for this game."**
Not every game has cheats available in every source. Try switching to a different cheat source in
**Settings → Cheat source**.

**It says the game's version couldn't be verified.**
Some game files don't expose their version number clearly. Cheats will still install, but the app
can't double-check they're an exact match, so keep an eye out for anything unexpected in-game.

**Cheats aren't loading / lots of errors when refreshing.**
The app is likely getting rate-limited by the cheat source's server. Wait a bit and try again, or
add a personal access token under **Settings → Cheat source** to raise the limit (only needed for
heavy use).

## Getting the app

The latest version is built automatically and available as a download:

1. Go to this project's page on GitHub.
2. Click the **Actions** tab.
3. Click the most recent successful build (green checkmark).
4. Scroll down to **Artifacts** and download the APK.
5. Open the downloaded file on your phone to install it — you may need to allow "install unknown
   apps" for whichever app you used to open it, the first time you do this.

---

## For developers: building it yourself

Everything below this line is only relevant if you want to build the app from source rather than
downloading the pre-built version above.

### Building with GitHub Actions (no local setup)

Push this project to a GitHub repository. A workflow (`.github/workflows/build.yml`) builds it
automatically on every push to `main`, and can also be triggered manually from the **Actions** tab
("Build APK" → **Run workflow**). Download the finished APK from that run's **Artifacts** section.

### Building on your own machine or phone

Requires JDK 17 and the Android SDK (`platforms;android-35`, `build-tools;35.0.0`).

```sh
./build.sh debug      # or: release / test
```

`build.sh` works the same way on Linux, macOS, WSL, and Termux (on-device on an Android phone —
it auto-detects Termux and uses its native `aapt2`). It writes `local.properties` for you and
checks that the SDK components you need are installed, with the exact command to fix it if not.
The finished APK lands in `output/BreezyCheatStation-<mode>.apk`.

**Gradle wrapper note:** `gradlew` downloads Gradle 8.10.2 itself on first run if
`gradle/wrapper/gradle-wrapper.jar` isn't present (it needs `curl`, `unzip`, and internet access
for that one-time download). If you have your own wrapper jar, drop it into that path and it'll be
used directly instead.

### Installing a locally-built APK

```sh
./install.sh debug
```

Installs via `adb` if a device is connected; otherwise copies the APK to your device's Downloads
folder and opens the system installer.

### How it identifies games

For installed titles, it reads the real NCCH/TMD headers under the emulator's title folder structure —
Title ID, version, and (from the SMDH) title/region — rather than trusting file or folder names.
For loose `.cia`/`.3ds`/`.cci`/`.cxi` files, it parses the same header info from the file itself,
falling back to guessing a Title ID from the filename only if the header can't be read.

### How the cheat source works

Cheat files are downloaded from a configurable GitHub repository — one cheat file per Title ID.
You can change the source (owner/repo/branch/path/file-name pattern) in **Settings → Cheat
source**. An optional personal access token can be added if you're hitting GitHub's rate limit for
unauthenticated requests.

### How backups work

Before any write to a game's cheat file, the previous contents (if any) are saved to
`<cheats>/backups/<TITLEID>_cheats_backup_YYYYMMDD_HHMMSS.txt` and verified by reading it back. If
a write fails or the result doesn't match what was written, the previous content is automatically
restored. Backups are pruned to the 25 most recent per title.

### Known limitations

- No wrapper JAR binary is vendored — the first build on a new machine needs internet access to
  fetch Gradle itself (separately from the app's own dependencies).
- Encrypted ROM headers can prevent reading a title's real name, region, or version; the app falls
  back to Title ID only in that case, and skips the compatibility check.
- Cheat formats and quality vary across community sources — always treat an installed cheat as
  "try it and see," which is exactly why backups happen automatically.
- Unauthenticated GitHub requests are subject to GitHub's standard public rate limits.

## License

See [LICENSE](LICENSE).


## v1.1 UI improvements

- Dashboard summary for games, cached cheats and installed cheats.
- Clearer game cards showing region, version, Title ID and cheat status.
- Cheat search on the game cheat screen.
- Cheat rows now show a lightweight category inferred from the cheat name.
- Existing automatic database refresh, caching, compatibility checks and backup/restore behaviour are unchanged.

## v1.2 universal emulator + source improvements

- Setup now asks for both a **3DS emulator data folder** and a **games folder** on first use.
- Emulator wording is generic so the app can be used with supported 3DS emulators rather than being tied to one emulator brand.
- **FlagBrew / Sharkive** is now the default cheat source.
- **Settings → Recommended sources** provides several pre-loaded GitHub cheat repositories/forks that can be switched without manually entering repository details.
- A custom GitHub source can still be entered manually.


### v1.2.1 UI polish
- Dashboard now shows game/cheat totals, storage folders, and the active cheat source.
- Quick access to folder and cheat-source settings from the dashboard.
- First-run setup has a clear welcome heading and explains the two required folders.
- Recommended cheat sources show the active source and make the built-in list easier to browse.
- User-facing terminology remains emulator-neutral and does not depend on a specific 3DS emulator.
