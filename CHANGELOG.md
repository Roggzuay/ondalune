# Changelog

## Unreleased

### New
- **Sound Profiles.** Slow a song down, drop it a few semitones, give it a long tail, and Ondalune offers to keep that for the song, or for the whole album. A saved sound is never put on behind your back: the player offers it on a quiet strip above the title and waits for a tap.
- Ondalune writes down every play with the speed, pitch and reverb that were on at the time, saved or not, and a new Home card shows those plays with a tag across the cover saying what was done. Tapping one plays the song that way again, even if it is the song already playing.
- **Settings → Sound Profiles** keeps every sound you have saved, with the two switches that govern them and a way to forget any of them.
- **Reactive**, a sixth player background: the same blurred cover, swelling on the bass, drifting on the mids, with the cover's own colours lifting off it on the top end. It reads the music itself, and only while it is on screen.
- **Headphone crossfeed**, in Sonic Lab: a little of each side sent to the other ear, so hard-panned records stop splitting your head in two. Four settings, off by default.
- **Vinyl**, also in Sonic Lab: a record under the music, with the groove's hiss and crackle, the platter's drift, the roll-off a cut gives both ends, and the needle landing at the start of every song. Three records to choose from.
- **Your own equalizer curves.** Shape the five bands, give the shape a name, and it waits as a chip under the sliders.
- **Backup**, in Settings: one file with every setting, your curves, your saved sounds and the song details you have corrected. Your servers and passwords are not in it, and the card says so.
- **Casting.** Play on a Chromecast, a speaker or a TV, through the same device picker the rest of Android uses. Off until you switch it on in **Settings → Music sources → Play on another device**: until then Ondalune never starts the Cast framework and nothing goes looking around your network. The player says plainly that the sound engine stays on the phone while it is playing elsewhere, because a receiver plays the file itself. Only in the GitHub build: the Cast SDK is Google's and proprietary, so the F-Droid build cannot carry it. New, and not yet tried on every kind of device.
- **ListenBrainz.** An open record of what you listen to that you can take with you. Off until you put a token in, and the card says exactly what leaves the phone. A listen that could not be sent is kept and goes with the next one.
- **Your listening**: what you played, for how long, when, and how much of it you bent out of shape. Worked out on the phone and sent nowhere. It says the date Ondalune started counting rather than overstating what it can see.
- **Share a song** as a picture in its own colours, made on the phone and handed to Android's share sheet.
- **Make a mix** from three dials — energy, familiarity and era — instead of the recipe the app picked. Everything in the middle is Your Mix.

### Changed
- The equalizer bands themselves are now remembered between launches, not just the preset.

## 2.0.0 (2026-11-24)

### New
- A new look: the app wears your phone's colours, and the player is painted from the cover — the picture, then the same cover blurred behind everything.
- The whole app can follow the playing cover's colours, or keep one steady palette. Settings → Appearance → Theme.
- The repeat button now cycles off, repeat all, repeat one. Repeat off plays to the end of the queue and stops; repeat all starts it again.
- The navigation bar and the mini player blend into the page over a soft blur, and that is the new default. A note on first launch says where to change it back.
- The player has a volume slider under the transport, which follows the buttons on the side of the phone.
- Moving album covers: an animated cover on your server, or a cover.mp4, cover.webm, animated cover.webp or cover.gif in a music folder on this phone. Settings explains it, and covers can be pre-loaded so none of them loads while you wait.
- Open song files from any app with Ondalune, and pick it as your music player.

### Changed
- Appearance is rebuilt: theme, fonts, Home layout, navigation bar and corner roundness are collapsed groups that each show what they are set to, with previews drawn in the colours and shapes they produce.
- The Home layout editor separates the rows shown on Home from the hidden ones, with counts and clearer drag handles.
- Artwork shape is gone; covers use the theme's own shape.

### Fixes & improvements
- Repeat is remembered across restarts; it used to reset every launch.
- Android 7 no longer inherits the launcher's hidden-from-Recents flags, so Ondalune stays in recent apps after you leave it.
- Dolby Atmos songs play on tablets and phones without Dolby.

## 1.8.1 (2026-11-20)

### New
- Hold an album, artist, playlist or mix for quick options: play, shuffle, queue, download, add to a playlist and more.
- App-icon shortcuts: hold the Ondalune icon to play Your Mix, shuffle your favourites or shuffle everything, without opening the app.
- Ask your assistant to "Play … on Ondalune", and a headphone or car play button starts Your Mix even when the app is closed.
- A play/pause button while lyrics are open, and the audio quality details show where the sound goes out.
- Livelier, calmer motion: the heart pops when you favourite, the mini player resists once a swipe will skip, and lists slide instead of jumping.

### Fixes & improvements
- Switching between dark and light no longer closes the app when the icon follows the theme.
- Opening the app shows one splash, without a sideways slide.
- Faster transitions for things you do often, and Reduce animations is respected in more places.

## 1.8.0 (2026-11-18)

### New
- A new look: a proper light mode where the status bar stays readable, clearer search with the best match on top, player buttons that show when they're on, and album pages with one title.
- Settings in plain words, one row per choice, with the section title in the top bar.
- Updates show up when you open the app or pull Home down, once per version. The previous version is one tap away, with a clear warning that going back clears the app's data.

### Fixes & improvements
- Switching between dark and light no longer restarts the app.
- Removing downloads asks first and shows how much space it frees.
- Empty Favourites and Playlists tell you how to fill them; Library no longer repeats Artists and Playlists from the bottom bar.
- "1 song", not "1 songs". The battery tip waits until you've left the app with music playing.

## 1.6.1 (2026-11-08)

- **Fixes & improvements:**
  - The audio quality line under the cover is no longer cut off on taller phones (such as the Galaxy S26 Ultra); it is more compact, and the cover is a little larger there.
  - Back takes you to where you were on the previous page, such as your place in Artists after opening an artist, instead of its top.
  - Pressing Home or any other tab closes search and clears it.

## 1.6.0 (2026-11-05)

- **Your server's playlists:** Navidrome and other OpenSubsonic playlists appear in Playlists, marked as the server's, with their songs and covers, and refresh with the library or the refresh button. Your own server playlists can be added to and deleted from Ondalune; playlists shared with you are read-only. Playlists made in Ondalune stay separate.
- **Music folders on this phone:** pick the folders your music is in (Settings → Music sources); only those are read, with no storage permission. Add, remove and rescan them, and Ondalune looks for changes when it opens. Ringtones, recordings and other sounds stay out.
- **Your Mix, freshly dealt:** every play draws a new mix from your whole library, favouring what you love without one artist taking over. A **New mix** button sits under it.
- **Ratings are gone:** the stars are removed from the player and lists, and no longer weigh on Your Mix or Smart Shuffle. Hearts remain.
- **Real tempo only:** the speed controls show a song's tempo only when it's known (its tag or a confident song analysis), and show when it's being analysed.
- **Audio quality under the cover:** the file's format, what actually plays, how it goes out and what changes the sound, without claims the app can't back up.
- **Downloads are easy to find:** a one-time hint after your first download, and the **Downloaded** filter says what plays offline, including partly downloaded albums.
- **Update notices:** a background check once a day, a quiet notification once per version, and an indicator on Home until you update. Updates follow how Ondalune was installed.
- **Fixes & improvements:**
  - Songs no longer show made-up details when the server leaves them out (format, bit depth, tempo, year).
  - "Bit-perfect" labels removed: Ondalune's engine outputs 16-bit PCM.

## 1.5.0 (2026-11-05)

- **Music on this phone:** turn it on in Settings → Music sources and the songs stored on your phone join your library: Home, mixes, search and the queue. They play offline, show their embedded covers, read lyrics from a matching `.lrc` file, and update when you add or delete files.
- **Jellyfin:** add a Jellyfin server alongside OpenSubsonic ones. Your password is used once to sign in; only the sign-in token is kept, encrypted.
- **Edit song details:** change a song's title, artist, genre and year from its options. Edits stay in the app, survive every sync, and **Use original** puts the server's details back.
- **A player that moves with you:** the player grows out of the mini player, and the cover travels from the thumbnail to the middle of the screen. Drag the mini player up, or the player down, and it follows your finger.
- **Smoother song changes:** the old cover fades away as the new one fades and grows into place, the player's colours fade to the new cover, and lyrics glide from line to line. Scroll the lyrics yourself and they wait a moment before following again.
- **Fixes & improvements:**
  - Sonic Lab's mood cards play your Upbeat and Calm mixes, or offer to turn on song analysis.
  - The player and the queue open without a bounce.

## 1.4.0 (2026-11-05)

- **Artist pages:** a full-size portrait running to the top of the screen, and the whole app takes on its colours while you're there. Play, shuffle, favourite, download and share, the latest release, popular songs, albums, singles and EPs, and similar artists. Tablets show the portrait beside the lists.
- **Mixes for you:** genre, artist, favourites, new-music and forgotten-favourites mixes made from your library, on Home.
- **Smarter autoplay:** when the queue ends, songs like the last ones play next, favouring songs you love and leaving out what you just heard. Turn it off in Settings → Playback.
- **Song analysis (optional, off by default):** estimates the tempo and energy of downloaded songs on your phone, for Upbeat and Calm mixes and smoother autoplay.
- **Downloads:** the cloud you tap turns into a progress ring; a button bottom-right opens the downloads sheet with pause, resume, cancel and retry, and tapping a song plays it from its album.
- **Fixes & improvements:**
  - Home sections can be switched off again.
  - Newly synced albums show on Home right away.
  - The Your Mix launch covers the top bar and tab bar as it grows.
  - Swiping the cover in the player moves straight sideways and responds to a quick flick.
  - The sync count climbs steadily instead of in jumps.
  - Ondalune no longer crashes on Android 7 when adding a server.

## 1.3.0 (2026-11-02)

- **Downloads page:** see what's downloading, waiting, failed or saved, and cancel, retry or remove from one place. Open it from the filter button, the download ring in the top bar, or Settings → Sound & streaming.
- **Downloads that recover:** a failed download is tried again on its own and waits for a connection, and downloads cut off by closing the app carry on the next time you open it.
- **Download from the player:** download the playing song from the player; a tick shows once it's on your phone.
- **Fresher shuffle:** favourites lead less often, songs you just heard move to the back, and the same artist doesn't play twice close together.
- **Fixes & improvements:**
  - Two songs no longer play at once when a song was slow to load and you picked another.
  - The launch animation starts where the splash screen leaves the icon.
  - The tab bar is as wide as the mini player.

## 1.2.1 (2026-11-02)

- Bug fix.

## 1.2.0 (2026-11-02)

- **Update notices:** when a new version is out, a quiet card on Home says so. Check any time in Settings → Updates, or turn the daily check off.
- **Update from the app:** read what's new, then download and install the update without leaving Ondalune. Android asks you to allow installs once and to confirm each update, and only accepts updates signed by Ondalune.

## 1.1.0 (2026-11-02)

- **A calmer look:** colour now comes from your music, not the buttons. Labels, cards and controls are quieter so album art stands out.
- **Hold to reset:** hold the speed button in the player to reset speed, pitch and reverb at once.
- **Slide between tabs:** hold the tab bar and slide; a glass square follows your finger and opens the tab you let go on.
- **What's new:** after big updates, a short note shows what changed.
- **Fixes & improvements**, including a crash while the library syncs.

## 1.0.5 (2026-11-01)

- The mini player stays hidden until you play something. Before, it showed a song that wasn't playing, on a first launch even a song from the built-in demo library ("Rain on Shimokitazawa").
- On launch, Ondalune only brings back the song you last played, ready to resume from headphones or the notification.
- The mini player slides away as you scroll down a list and comes back as soon as you scroll up. Turn this off in Settings → Appearance → Hide mini player while scrolling.

## 1.0.4 (2026-11-01)

- Album cards on Home and in the library open the album wherever you tap them; tapping the artist name under an album no longer jumps to the artist's page. Artist names link only from the full-screen player.
- Back from a page opened from the player, "Go to artist/album" or the Top artists row returns to where you were (including the page and scroll position on Home), instead of leaving you on the Artists list.
- Home keeps its scroll position while an album or artist page is open.
- Playing from a playlist: the full-screen player shows "Playing from playlist" and its name; tap it to open the playlist. Tap the album name under the song title to open the album.

## 1.0.3 (2026-10-31)

- New Your Mix launch: as the play button zooms into the player, the artwork shapes and the title rush away from it and grow, instead of shattering.
- Smoother launch: the mix starts playing and the player opens once the zoom covers the screen, so starting playback no longer stutters the animation. The shapes and title redraw from cached layers instead of being recut every frame.

## 1.0.2 (2026-10-31)

- Your Mix on tablets, landscape screens and unfolded foldables: the title and play button sit on the left and the artwork collage stays at its intended size beside them, instead of stretching across the screen.
- The tilted artwork in Your Mix always fills its shape.
- The launch animation from Your Mix covers wide screens completely.

## 1.0.1 (2026-10-31)

- New app icon: a white crescent moon cradling a waveform, on black. Choose Moonlight, Paper, Slate or Graphite in Settings → App icon; themed icons on Android 13+ use the new shape.
- Splash screen and launch animation match the new icon.

## 1.0.0 (2026-10-31)

First public release.

- **Playback:**
  - Streaming from Subsonic and OpenSubsonic servers, with offline downloads
  - Smart shuffle, smart queue continuation, sleep timer and scrobbling
- **Sound:**
  - Speed and pitch engine, with preserve-key, varispeed and independent semitone modes
  - Live studio reverb with presets and custom room controls
  - Five-band EQ presets
- Gapless playback, album-aware crossfade (up to 12 s) and ReplayGain (track or album) with clipping protection
- **Lyrics:** time-synced lyrics with tap-to-seek, plus a lyrics line on tall widgets
- **Your Mix:** a home hero with an animated launch into the player
- **Look:**
  - Artwork-driven colours and five animated player backgrounds
  - Six interface styles, separate heading and body fonts, and artwork shapes
  - A drag-to-reorder Home layout
  - Eight launcher icons, with themed-icon support
- **Gestures:** swipe the cover or the mini player to skip, swipe a row to favourite or play next, and multi-select with Select all
- **Background playback:** media notification and resizable widgets, and lock-screen playback with an optional battery-optimisation prompt
- **About & licences** screen
