# Privacy policy

_Last updated: 21 November 2026 (online lyrics, Google Play)_

Ondalune is a music player for servers you run or have an account on. It is built so that your
listening stays between your phone and your server.

This policy covers every version: the GitHub release, F-Droid and Google Play. Where they differ,
it says so. The app shows the same policy in **Settings → Privacy**.

## What Ondalune does not do

- No accounts, ads, analytics, crash reporting or tracking SDKs.
- No data is sent to the developer. There is no Ondalune server.

## Network connections

Ondalune only connects to:

| Where | When | What is sent |
|---|---|---|
| The music servers you add | While you browse, play, download or sync | Your username and an authentication token or password, and standard Subsonic API requests (for example, which song to stream or scrobble) |
| `lrclib.net` | When a song has no lyrics from your server and **Settings → Lyrics → Find missing lyrics online** is on (the default), and when you search for lyrics yourself | The song's title, artist, album and length. Never the audio. Lyrics you pick are kept on your phone |
| `en.wikipedia.org` | When an artist page has no biography from your server (or, for music on your phone, no biography yet) | The artist's name |
| `api.github.com` | At most once a day in the background (only with a network and enough battery), to check for a newer Ondalune, and when you tap **Check now**. Turn the daily check off in **Settings → Updates → Check automatically**. Builds from F-Droid and Google Play never make this check | A standard request for the latest release. Like any website, GitHub sees your IP address |
| `github.com` (release downloads) | Only when you tap **Update** in the update notice | A standard download of the new version's APK. Not in F-Droid or Google Play builds |
| Cast devices on your own network | Only in the GitHub build, and only once **Settings → Music sources → Play on another device** is switched on. Off (the default) Ondalune never starts the Cast framework and nothing looks for a device | Google Play services looks around your network for Cast devices and, when you pick one, hands it the address your server streams the song from. The device fetches the song itself; Ondalune sends it no audio and no account details. Not in the F-Droid build, which has no Cast code at all |
| `api.listenbrainz.org` | Only when you have put a ListenBrainz token into **Settings → Music sources → ListenBrainz**. Nothing is sent before that, and the switch stops it without forgetting the token | The playing song's title, artist, album, length and track number, and the time a play finished. Nothing about you beyond the token that says which account it is. A play that could not be sent is kept on your phone and goes with the next one |
| `ko-fi.com` | Only if you tap **Buy me a coffee** | Nothing beyond opening the page in your browser |
| Image hosts your server links to | When showing artist photos or covers whose address your server returns (Navidrome, for example, may link artist photos on Last.fm or Wikimedia) | An ordinary image request |

## Data stored on your phone

- **Your ListenBrainz token**, if you add one, is encrypted with the same Keystore key as your server passwords, and is never written into a backup file.
- **Server details.** Passwords are encrypted with a key held in Android's Keystore, which never
  leaves the device. For a Jellyfin server the password is only used to sign in and isn't stored;
  the sign-in token Jellyfin returns is kept instead, encrypted the same way.
- **Song details you edit** (title, artist, genre, year). They are kept in the app's own
  storage and never sent to your server or written to your files.
- **Your library cache, settings, queue, downloaded songs and the lyrics you choose.**
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

## Servers without HTTPS

Plain `http://` addresses are allowed, because many home servers have no certificate. Anything
sent to such a server, including your password, can be read by others on the same network; the
app says so when you enter one. Use `https://` for servers reached over the internet.

## Permissions

| Permission | Why |
|---|---|
| Internet, network state | To reach your music server |
| Notifications | Playback controls, download progress, and (if you leave it on) a quiet note when a new version is out |
| Foreground service (media playback), wake lock | To keep music playing with the screen off |
| Ignore battery optimisations | Only requested if you agree, so phone battery savers don't stop playback on the lock screen. |
| Vibrate | Haptic feedback on gestures |
| Install apps | Only after you tap **Update**: hands the downloaded update to Android's installer. Android asks you to allow this once and to confirm every update, and only accepts an update signed with the same key as your installed Ondalune. Not in builds from F-Droid or Google Play, which the store updates |
| Delete apps | Only when you choose to go back to an earlier version: Android asks you to confirm removing the current one first. Not in F-Droid or Google Play builds |

## Google Play

If you installed Ondalune from Google Play, Google handles the install and updates under
[Google's privacy policy](https://policies.google.com/privacy). Ondalune itself adds nothing for
Google: no Play services, Firebase or Google analytics are in the app.

## Children

Ondalune is not directed at children and collects no personal data from anyone.

## Changes

Changes are published in this file with the date at the top; the full history is in the
repository's commit log.

## Contact

Questions about privacy: open an issue at <https://github.com/Roggzuay/ondalune/issues>.
