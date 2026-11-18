# Ondalune launch film

A 60-second, 1920×1080, 60 fps launch film built with HyperFrames 0.9.65. Every screen is a real capture of Ondalune 1.8.0.

Output: `renders/ondalune-launch-film.mp4` (16:9) and `renders/ondalune-launch-film-9x16.mp4` (9:16, 1080×1920). Renders are not in git.

## Structure

| Scene | File | Starts at | What it shows |
|---|---|---|---|
| 1 | `compositions/intro.html` | 0.00 s | The mark assembles. "Ondalune" and "Your music. Your universe." |
| 2 | `compositions/library.html` | 6.20 s | Home, Library, Artists, Favourites, Album |
| 3 | `compositions/player.html` | 14.84 s | Player colour from artwork, synced lyrics, speed/pitch, EQ |
| 4 | `compositions/custom.html` | 26.36 s | Light, dark and AMOLED; styles; music sources; Home layout |
| 5 | `compositions/ethos.html` | 40.75 s | "Your library. Your server. Your experience." Open source, no ads |
| 6 | `compositions/finale.html` | 49.39 s | "Music without boundaries." Logo lockup, GitHub link, credits |

`index.html` mounts the scenes and the audio, and adds the vignette and grain on top. Each scene overlaps the next by 0.6 s. Scene timing is locked to the music at 83.35 BPM: one beat is 0.72 s and one bar is 2.88 s. `SPEC.md` holds the look and motion rules.

Each scene file can be edited and previewed on its own. The repo URL is a composition variable (`repo`).

## Commands

```bash
npx hyperframes preview --background   # Studio at http://127.0.0.1:3002
npx hyperframes check                  # lint, layout, motion, contrast, audio
npx hyperframes snapshot --at 10,20,30
npx hyperframes render --fps 60 --quality delivery --output renders/ondalune-launch-film.mp4
```

## 9:16 version

`vertical/` is generated from the 16:9 scenes by `tools/make_vertical.py`, so timing, copy and motion stay in one place. Edit the landscape scenes, then:

```bash
python3 tools/make_vertical.py
cd vertical && npx hyperframes check
npx hyperframes render --fps 60 --quality delivery --output ../renders/ondalune-launch-film-9x16.mp4
```

Text sits inside the TikTok, Reels and Shorts safe areas (x 120–888, y 288–1248; narrower below y 840). The phone runs lower, under the platforms' captions. `vertical/assets` is a symlink to `../assets`.

## Re-capturing screens

The screens in `assets/screens/` come from emulator-5554 running the release APK against the Creative Commons demo server (`~/Projects/ondalune-promo/tools/ccserver/server.py`). `tools/capture.py` drives the app over adb. `tools/icon_svg.py` rebuilds `assets/brand/mark.svg` from the launcher vector.

## Credits

- Music: Aquatone, "Mountains", CC BY 4.0. Trimmed to 60 s with fades.
- Album art: Creative Commons netlabel releases (CC BY / CC0). Full list in `~/Projects/ondalune-promo/public/cc/CREDITS.md`.
- Lyrics on screen: Alfred Tennyson, "Crossing the Bar" (public domain). These are not the songs' own words.
- Sound effects: synthesised for this film.
- Fonts: Unbounded and Figtree (SIL OFL), the app's own typefaces.
