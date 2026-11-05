# Privacy policy

_Last updated: 5 November 2026 (music folders, update notices)_

Ondalune is a music player for servers you run or have an account on. It is built so that your
listening stays between your phone and your server.

## What Ondalune does not do

- No accounts, ads, analytics, crash reporting or tracking SDKs.
- No data is sent to the developer. There is no Ondalune server.

## Network connections

Ondalune only connects to:

| Where | When | What is sent |
|---|---|---|
| The music servers you add | While you browse, play, download or sync | Your username and an authentication token or password, and standard Subsonic API requests (for example, which song to stream or scrobble) |
| `en.wikipedia.org` | When an artist page has no biography from your server (or, for music on your phone, no biography yet) | The artist's name |
| `api.github.com` | At most once a day in the background (only with a network and enough battery), to check for a newer Ondalune, and when you tap **Check now**. Turn the daily check off in **Settings → Updates → Check automatically**. Builds from F-Droid never make this check | A standard request for the latest release. Like any website, GitHub sees your IP address |
| `github.com` (release downloads) | Only when you tap **Update** in the update notice | A standard download of the new version's APK |
| `ko-fi.com` | Only if you tap **Buy me a coffee** | Nothing beyond opening the page in your browser |
| Image hosts your server links to | When showing artist photos or covers whose address your server returns (Navidrome, for example, may link artist photos on Last.fm or Wikimedia) | An ordinary image request |

## Data stored on your phone

- **Server details.** Passwords are encrypted with a key held in Android's Keystore, which never
  leaves the device. For a Jellyfin server the password is only used to sign in and isn't stored;
  the sign-in token Jellyfin returns is kept instead, encrypted the same way.
- **Song details you edit** (title, artist, genre, year). They are kept in the app's own
  storage and never sent to your server or written to your files.
- **Your library cache, settings, queue, and downloaded songs.**
- **Music folders on this phone, only the ones you add** (Settings → Music sources). Each folder
  is picked in Android's folder picker, which lets Ondalune read that folder (and the folders in
  it) and nothing else; no storage permission is asked for. Ondalune reads the songs' tags,
  `.lrc` lyrics and cover pictures, and plays the songs from where they are. It doesn't copy,
  change or upload them; an embedded cover is saved once per album in its private storage.
  Removing a folder removes its songs from Ondalune's library and gives back the access; the files
  stay on your phone.
- **Song analysis, only if you turn it on** (Settings → Playback → Song analysis). Ondalune reads up
  to 45 seconds of songs stored on your phone (your downloads, and your own music when that is on) and keeps an estimate of their tempo, energy, brightness
  and beat strength in its private storage. The audio never leaves the phone, and turning the setting
  off deletes the results.

All of this is stored in the app's private storage, which other apps can't read. Uninstalling
Ondalune deletes it.

Android's cloud backup may include your settings. It never includes the server database or your
downloads.

## Permissions

| Permission | Why |
|---|---|
| Internet, network state | To reach your music server |
| Notifications | Playback controls, download progress, and (if you leave it on) a quiet note when a new version is out |
| Foreground service (media playback), wake lock | To keep music playing with the screen off |
| Ignore battery optimisations | Only requested if you agree, so phone battery savers don't stop playback on the lock screen |
| Vibrate | Haptic feedback on gestures |
| Install apps | Only after you tap **Update**: hands the downloaded update to Android's installer. Android asks you to allow this once and to confirm every update, and only accepts an update signed with the same key as your installed Ondalune. Not in builds from F-Droid, which F-Droid updates |

## Contact

Questions about privacy: open an issue at <https://github.com/Roggzuay/ondalune/issues>.
