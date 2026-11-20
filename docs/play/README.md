# Google Play

Same app, same package (`io.github.roggzuay.ondalune`) and version numbers as the GitHub release,
built with one switch:

```bash
./gradlew bundleRelease -Pondalune.playStore=true
# → app/build/outputs/bundle/release/app-release.aab
```

`-Pondalune.playStore=true` does three things:

- `BuildConfig.SELF_UPDATE = false`: no GitHub update check, no in-app installer, no "go back a
  version". R8 strips that code from the bundle. Play forbids apps that update themselves.
- Merges `app/src/playStore/AndroidManifest.xml`, which removes `REQUEST_INSTALL_PACKAGES`,
  `REQUEST_DELETE_PACKAGES` and the updates FileProvider.
- Keeps `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: without the exemption, playback stops on the lock
  screen on many phones. Play only allows it for apps whose core feature breaks
  without it, so declare it with that reason in the Console. If review refuses it, remove it in
  the Play manifest again: "Allow background playback" then opens the app's info page instead.
- `BuildConfig.PLAY_STORE = true`: Settings → Privacy says updates come from Google Play.

Check a bundle before uploading:

```bash
unzip -p app-release.aab base/manifest/AndroidManifest.xml | strings | grep -o "android.permission.[A-Z_]*" | sort -u
```

Expected: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and no other `REQUEST_*` permission.

## Signing (decide once, can't be changed)

Play re-signs every install with the app signing key. To let GitHub and Play installs update each
other, give Play the **existing release key** (SHA-256 starting `2B:46:08`):

1. Play Console → app → Test and release → App integrity → App signing → "Use a different key" →
   "Export and upload a key from Java keystore". It gives you `pepk.jar` and an encryption key.
2. `java -jar pepk.jar --keystore=<release.jks> --alias=<alias> --output=encrypted.zip --include-cert --rsa-aes-encryption --encryption-key-path=<downloaded key>`
3. Upload `encrypted.zip`. Then either keep using the same key as upload key, or register a
   separate upload key (safer: a lost upload key can be reset, a lost signing key can't).

If Play generates its own key instead, Play installs and GitHub/F-Droid installs can't update each
other; users must uninstall to switch.

## Play Console answers

**Privacy policy URL:** `https://github.com/Roggzuay/ondalune/blob/main/PRIVACY.md` (live once
`main` has PRIVACY.md).

**App access:** no login needed. Reviewers tap "Start Listening" on the welcome screen to use the
built-in sandbox library. Say so in the instructions field.

**Ads:** no.

**Data safety:** "No data collected, no data shared" (the usual answer for self-hosted server
clients). Nothing reaches the developer; the app talks to the user's own servers, and LRCLIB and
Wikipedia get only song or artist names for one lookup each. Read Google's current definition of
"collection" when filling it in. If it seems to cover those lookups, declare "App activity → Other
actions", not shared, processed ephemerally, optional ("Find missing lyrics online" can be off).
No account, so nothing to delete on request; uninstalling deletes everything.

**Foreground service (media playback):** declare it; use case "Media playback". Needs a short
video: start a song, lock the phone, music keeps playing with lock-screen controls.

**Android Auto:** the manifest declares Auto support (`automotive_app_desc`), so Play runs an Auto
quality review on the first release with it. If it blocks the launch, ship without Auto first.

**Content rating:** questionnaire, music player, no user-generated content shared in the app.
**Target audience:** 13+ (not designed for children).

## Store listing

Text: `fastlane/metadata/android/en-US/` (title, short and full description). Still needed:

- 512 × 512 icon (PNG, 32-bit)
- 1024 × 500 feature graphic
- 2–8 phone screenshots (`docs/screenshots/` has some; Play wants 16:9 or 9:16, min 320 px)
- Contact email (shown publicly on the listing)

## Testing track

New personal developer accounts must run a **closed test with at least 12 testers for 14 days in a
row** before applying for production. The internal and closed tracks replace the preview channel
for Play users; GitHub previews continue as they are.

## Each release

1. Same versionCode/versionName as the GitHub release (one release commit serves all stores).
2. `./gradlew bundleRelease -Pondalune.playStore=true`, check permissions as above.
3. Upload to the track, paste the short changelog from `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
