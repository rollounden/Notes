# Notes

A private, local-only notes app for GrapheneOS (or any Android 12+). Kotlin + Jetpack Compose,
Material You dynamic colour, Room database. No network permission, no analytics, no accounts.

## Install

The easiest way is [Obtainium](https://github.com/ImranR98/Obtainium), which installs straight from
GitHub Releases and notifies you when a new version is tagged:

1. Open Obtainium, tap **+**, paste `https://github.com/rollounden/Notes`, tap **Add**.
2. Or tap this on the phone:
   [Add to Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/rollounden/Notes)

Or grab the signed APK from the [latest release](https://github.com/rollounden/Notes/releases/latest)
and open it on the phone.

## What's inside

- **Notes list**: two-column staggered grid, pinned section, live search (title, body, checklist
  items), colour filter chips, swipe a card sideways to archive (with Undo), long-press for
  multi-select (pin / colour / archive / delete).
- **Editor**: autosaves as you type (400 ms debounce, plus on back/pause). Empty notes are
  discarded. Pin, colour label (8 tones tuned for light and dark), share as plain text, archive,
  delete.
- **Checklists**: tap to tick, ticked items sink to a collapsible section, drag handle to reorder,
  Enter adds the next item, Backspace on an empty item removes it. Convert text <-> checklist.
- **Markdown**: headings, bold, italic, inline code, fenced code, bullet and numbered lists,
  quotes, rules and `- [ ]` tasks (tappable in preview). Toggle edit / preview from the top bar;
  notes containing Markdown open in preview.
- **Archive**: swipe to restore, or select and delete forever (still undo-able for a few seconds).
- **Backup & restore**: export every note to a JSON file via the system file picker; import with
  Merge (duplicates skipped) or Replace. Works with GrapheneOS storage scopes, no storage
  permission needed.
- **Widget** (Jetpack Glance, Material You): small tile = latest / pinned note + "+" shortcut;
  larger = scrollable list of pinned notes (falls back to recent). Tapping a row deep-links
  straight into that note. Refreshes whenever the database changes.
- **Share into Notes**: share text from any app and it lands in a new note.

## Build (Linux)

Toolchain (same as the sibling [Calander](https://github.com/rollounden/Calander) app):

- JDK 17 or newer (21 recommended)
- Android SDK with `platforms;android-35` and `build-tools;35.0.0`

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=~/Android/Sdk
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

If your SDK lives elsewhere, create `local.properties` with `sdk.dir=/path/to/sdk` (git-ignored).

## Releases

Releases are built by GitHub Actions (`.github/workflows/release.yml`). To ship a new version:

```bash
git tag v0.1.1
git push origin v0.1.1
```

The workflow derives `versionName` (`0.1.1`) and `versionCode` (`101`) from the tag, builds a
minified release APK, signs it with the keystore held in repository secrets, and attaches it to a
GitHub Release. Obtainium users get the update automatically.

Secrets the workflow expects: `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`,
`RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.

### Local release builds

Put a `keystore.properties` in the repo root (git-ignored):

```properties
storeFile=/absolute/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

then `./gradlew assembleRelease`. Without that file the release build is produced unsigned, so
anyone can still build from source. Never change the signing key once released: Android refuses to
update an app whose signature changed, forcing an uninstall (and with it, your notes; export a
backup first).

## Install on GrapheneOS

### One-time phone setup

1. Settings -> About phone -> tap **Build number** 7x
2. Settings -> System -> Developer options -> enable **USB debugging**
3. GrapheneOS-specific: Settings -> Security -> **USB peripherals** -> allow data when unlocked
   (the default blocks ADB). Unlock the screen before plugging in.

### Install

```bash
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or wireless: Developer options -> Wireless debugging -> **Pair device with pairing code**, then

```bash
adb pair <ip:port>     # pairing port + code
adb connect <ip:port>  # connection port
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then long-press the home screen -> Widgets -> Notes to add the widget.

## Privacy notes

- The manifest declares **no** `INTERNET` permission, so the app cannot talk to the network.
- Data lives in `notes.db` in app-private storage. `allowBackup` is on with explicit
  `data_extraction_rules` so Seedvault / device-to-device transfer can carry it; nothing is sent
  anywhere by the app itself.
- Backups are plain JSON you write to a location you pick. Treat them like any other unencrypted
  file.

## Layout

```
app/src/main/java/dev/apex/notes/
  NotesApp.kt              Application: database + repository singletons
  data/                    Room entities, DAO, database, repository, NoteColor
  ui/                      MainActivity + navigation, list, editor, archive, backup screens
  markdown/                Tiny Markdown parser + Compose renderer
  backup/                  JSON export/import
  widget/                  Glance widget + receiver
```

## License

[MIT](LICENSE)
