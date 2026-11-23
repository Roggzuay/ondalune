# Moving artwork

Ondalune's player can show an album cover that moves. This explains where that cover comes from,
why it sometimes doesn't appear, how to stop it loading while you wait, and how to make your own.

**Ondalune never downloads artwork from a third party.** There is no artwork service behind this,
no scraper and no catalogue. It reads what is already in your own library and nothing else. If an
album has no moving cover, it is because your library has none for it.

## Where it looks

Two places, in this order:

1. **A music folder on this phone.** A file sitting next to the songs, played from where it is —
   nothing is copied or downloaded.
2. **Your server's album cover.** Ondalune asks your server for the album cover *at its original
   size*, reads the first 4 KB to see what it is, and only fetches the whole file if those bytes
   say it animates. A still JPEG costs you four kilobytes and is dropped.

A folder holding songs from more than one album claims no album, so its artwork is never put on
the wrong record.

## File names and formats

In a music folder, Ondalune looks for these names, best match first:

| Looked for | Accepted |
| --- | --- |
| `motion`, `animated`, `cover-motion`, `motion-cover`, `animated-cover`, `cover-animated` | `.mp4`, `.m4v`, `.webm`, `.webp`, `.gif` |
| `cover`, `folder`, `front`, `album`, `albumart` | the same |

So `cover.mp4`, `motion.webm`, an animated `cover.webp` or `cover.gif` beside the songs all work.
Video (MP4, WebM) is preferred over animated WebP, which is preferred over GIF — a GIF is capped
at 256 colours and will band on a gradient.

On a server, only the album cover itself is read, so it has to **be** the animated file: an
animated WebP or GIF saved as the album's cover art.

## It depends on your server

This is the usual reason a moving cover does not show.

Ondalune requests the cover with no size argument, which asks the server to hand back the file as
stored. A server that resizes, re-encodes or converts covers to JPEG will flatten an animated WebP
into a single still frame before Ondalune ever sees it, and there is nothing the app can do about
that from the other end.

- **Navidrome** serves the original when no size is requested, and is what this was built against.
- **Gonic**, **Airsonic-Advanced** and other Subsonic servers vary; try it and see.
- **Jellyfin** libraries are not checked for moving covers at all — only Subsonic/OpenSubsonic
  servers are. A music folder on the phone still works regardless of which server you use.

If your server will not serve the original, put the file in a music folder on the phone instead.

## Stop it loading while you wait

By default Ondalune looks for an album's moving cover **when you first play it**, which is the one
play that waits.

Turn on pre-loading instead: **Settings → Motion artwork → When to look → Whole library, in the
background**. Ondalune then walks the whole library once a day, never on low battery, and respects
the Wi-Fi-only switch, so a cover is ready before you press play. The settings screen offers this
as a one-tap tip when it is off.

Still covers need no setting: Ondalune already keeps every album cover and artist photo on the
phone after a sync, so browsing never waits for one and downloaded songs keep their covers
offline. Progress is shown in Settings.

**Storage** is a ceiling, not a reservation: 250 MB by default, settable from 50 MB to 100 GB.
The least recently used artwork is dropped first when the limit is reached. Clearing it in
Settings removes saved copies only — files in your music folders are never touched.

## Making your own

Ondalune reads ordinary files, so any tool that writes MP4, WebM, animated WebP or GIF works.
These are the usual ones:

- **[FFmpeg](https://github.com/FFmpeg/FFmpeg)** — converts a video clip into a cover. A short,
  silent, looping MP4 is the best-looking option:
  ```sh
  ffmpeg -i clip.mp4 -t 6 -an -vf "scale=640:640:force_original_aspect_ratio=increase,crop=640:640" \
         -movflags +faststart cover.mp4
  ```
- **[libwebp](https://github.com/webmproject/libwebp)** — Google's WebP tools. `gif2webp` converts
  an existing GIF, `img2webp` builds an animated WebP from frames. Use this when the file has to be
  the album cover on a server, since servers store cover art as an image rather than a video.
- **[beets](https://github.com/beetbox/beets)** — organises a library and can put a chosen cover
  file in every album folder for you.
- **[MusicBrainz Picard](https://github.com/metabrainz/picard)** — tags releases and writes cover
  art into album folders.
- **[Navidrome](https://github.com/navidrome/navidrome)** — the server this was built against; its
  docs cover which filenames it picks up as album art.

## Copyright

Moving artwork is yours to supply, and yours to be responsible for. Album art, music videos and
promotional clips are usually owned by the artist, the label or whoever made them, and converting
a clip into a cover file does not change who owns it.

Ondalune ships no artwork, hosts none, and fetches none from any third party — it plays files that
are already in your library, wherever you got them. Whether you hold the rights to a file, or your
use of it is permitted where you live, is your call and your responsibility, not the app's. The
tools listed above are general-purpose media and library tools; none of them is a way to take
artwork from a streaming service, and that is deliberate.
