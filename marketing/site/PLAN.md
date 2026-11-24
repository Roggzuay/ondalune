# Ondalune landing page — plan

**Line 1 — kind:** software (page type `software`, site-kind `software`). An Android music player
for people who already run their own music server. Main action: **Download for Android**.

**Line 2 — the deal:** palette `blue-black-cornflower` (near-black ground #0a0d14, cornflower
accent #5ab9fc) — the app itself is a dark, cover-lit player, its own icon is white on black, and
every screenshot on this page glows; a near-black ground lets the covers be the only colour on the
page. Type `manrope` (Manrope + JetBrains Mono, first pairing). Radius `round` (consumer app).
Hero `split` · nav `nav-centre` · footer `footer-statement` · close `close-type` · rhythm
`dark-band` · buttons `arrow` · mark: the client's own icon replaces it.
Moments: `line-carries-colour` (§colour) and `letters-assemble` (§open). No shader effect, no
WebGL, no scene: the app's own UI is the subject and carries every section.

**Re-deals, with reasons:**
- Hero `film` → `split`. `film`'s use fits (there is a real 1-minute launch film), but every frame
  of that film is a title card with baked-in headlines, so no frame can sit under live text
  without words over words. Re-dealt with `--has ui`; the film keeps a real link in §colour.
- Nav `nav-cards` → `nav-mega` → `nav-centre`. `nav-cards` wants two to four distinct products;
  Ondalune is one app. `nav-mega` wants 6–12 feature pages or several product lines; this is a
  single page. `nav-centre` ("software with four or five plain words") is what is left.

**Voice:** technical-dry with proof (copy-registers 2) · Pairs: exact, not cold; the figure does
the praising · Person: "you" for what you get, the app is the subject in feature lines · Sentences
mostly 8–16 words · Humour: none · House words: server, cover, library, folder, the same word every
time · Never: virtue words, a pun on the name, invented figures.

**Fact sheet** (everything on the page comes from the repo: README, CHANGELOG, fastlane metadata,
`build.gradle.kts`, PRIVACY.md):
- What it is: an Android player for a Subsonic/OpenSubsonic, Navidrome or Jellyfin server, and for
  folders on the phone.
- Numbers: 0.80×–2.00×; ±12 semitones; 432 Hz; crossfade to 12 s; six reverb rooms; five animated
  backgrounds; nine typefaces; four layouts; version 2.0; Android 7.0 and newer (minSdk 24);
  GPL-3.0; seven formats from folders (FLAC, MP3, AAC, ALAC, Ogg Vorbis, Opus, WAV).
- The limit (one admission): no catalogue of its own — it needs a server or folders on the phone.
  Not on the Play Store or F-Droid yet; the APK comes from GitHub releases.
- The enemy (a category): server clients that read like a file browser.
- Offer: free, GPL-3.0, APK from GitHub releases.

## Sections

| # | id | one idea | picture | kind | screens |
|---|---|---|---|---|---|
| 1 | hero | a player for your own server | two real screens whole on the ground | object | 1 |
| 2 | `player` | the library beside what is playing | app-frame: library pane + player pane | object | 1 |
| 3 | `sound` | speed, pitch and room, changed live | speed-pitch capture, cut by the frame | UI | 1 |
| 4 | `colour` | the player takes the cover's colours | four cover-lit screens, four colours | UI | 2 |
| 5 | `open` | no ads, no trackers | — (statement moment's own section) | type | 1 |
| 6 | `sources` | your server, and this phone | diagram card, real Jellyfin + Android icons | diagram | 1 |
| 7 | `yours` | set the look | 2×2: styles, shapes, fonts, layouts | UI | 1 |
| 8 | `faq` | the last questions | — (FAQ) | — | 1 |
| 9 | `footer.closing` | download it | the big button | type | 1 |

Object lines: hero, `player`, `colour` — three, the cap. `open` and `faq` are the two picture-less
sections and are not neighbours. `dark-band` is `colour`; `line-carries-colour` rides it.
