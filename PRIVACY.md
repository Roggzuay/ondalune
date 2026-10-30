# Privacy policy

_Last updated: 30 October 2026_

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
| `en.wikipedia.org` | When an artist page has no biography from your server | The artist's name |
| `ko-fi.com` | Only if you tap **Buy me a coffee** | Nothing beyond opening the page in your browser |
| Image hosts your server links to | When showing artist photos or covers whose address your server returns (Navidrome, for example, may link artist photos on Last.fm or Wikimedia) | An ordinary image request |

## Data stored on your phone

- **Server details.** Passwords are encrypted with a key held in Android's Keystore, which never
  leaves the device.
- **Your library cache, settings, queue, and downloaded songs.**

All of this is stored in the app's private storage, which other apps can't read. Uninstalling
Ondalune deletes it.

Android's cloud backup may include your settings. It never includes the server database or your
downloads.

## Permissions

| Permission | Why |
|---|---|
| Internet, network state | To reach your music server |
| Notifications | Playback controls and download progress |
| Foreground service (media playback), wake lock | To keep music playing with the screen off |
| Ignore battery optimisations | Only requested if you agree, so phone battery savers don't stop playback on the lock screen |
| Vibrate | Haptic feedback on gestures |

## Contact

Questions about privacy: open an issue at <https://github.com/Roggzuay/ondalune/issues>.
