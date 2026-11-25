# Ondalune 2.1 — Sound Profiles, reactive artwork, and reach

Date: 2026-11-25
Status: approved to build (phases 1–3 first)

## Intent

Ondalune's audio engine is the thing no other Subsonic client has. Today every
knob on it — speed, pitch, reverb, EQ — is **global**: set a song to 0.80× with
−3.86 semitones and a Dreamscape tail, and the next song inherits it. The user's
own words:

> when I slow down a song and reduce the semitone and add reverb, I like that
> version more than the original in some cases, so I would've preferred if I had
> a preset/profile for it to quickly toggle it.

That is the centre of this release. The adjustments stop being a mode you are in
and become **a version of a song** — something Ondalune remembers, offers back,
and keeps a record of.

Around it: the artwork backdrop starts moving with the sound instead of a clock,
and the app grows the reach it has been missing (casting, scrobbling elsewhere,
backup).

## Goals

- A sound profile (speed, pitch mode, semitones, reverb, EQ) can be **saved
  against a track or an album** and re-applied in one tap.
- Ondalune keeps a **play history that records the adjustments in force**, saved
  or not, and surfaces it as a Home card.
- None of it costs battery, background work, or a single frame of playback.
- The artwork backdrop can **move with the music**, driven by the same blurred
  cover already on screen.
- Casting, ListenBrainz, jukebox output, and backup/restore.

## Non-goals

- The physical-object player (spinning record/cassette). Explicitly cut; it
  existed once and was removed for clutter.
- Writing sound profiles back to the server. They are Ondalune's, like track
  overrides.
- Profiles that follow a *genre* or auto-apply by rule. Per-track and per-album
  only; anything cleverer is a later release.

## Anti-goals (what would make this a failure even if it worked)

- A profile that applies itself and surprises the user mid-song.
- A save prompt that nags. It appears once per adjustment session, and never
  again for a track that already has a profile unless the sound changed.
- Background scanning, a service, or a wakelock for any of this.
- An audio tap that runs when nothing is looking at it.
- Latency or a dropout in the PCM write loop.

## Constraints found in the code

- `SoundController` (276 lines) holds speed/pitch/reverb/EQ as three
  `StateFlow`s persisted to `SharedPreferences`. It is the only writer, and it
  hands state to `OndaluneAudioEngine`. Profiles slot in here cleanly.
- The models already exist and are serialisable value types: `SpeedPitchState`,
  `ReverbState`, `PitchCorrectionMode`, `EqualizerPreset`, plus a free-form
  `customEqBands: List<Float>` (five bands, −10..+10 dB).
- Room DB is `OndaluneDatabase`; entities live in `SubsonicModels.kt`
  (`subsonic_tracks`, `track_overrides`, `local_files`, `subsonic_downloads`, …).
  Migrations are hand-written with a `hasColumn` guard — the house pattern.
- **There is no timestamped listen history.** `subsonic_tracks.playCount` is an
  integer only. Any history card or stats view needs a new table, and can only
  be honest from the version it ships in.
- `HomeSection` is an enum; the Home layout editor and `PlayerCustomizationSettings.homeSections`
  persist it by `name`. A new section is one enum entry plus a renderer.
- `PlayerBackdrop` (`ArtworkGlowBackground.kt`) already runs a frame clock that
  only ticks while playing, scaled by playback speed, and reads animated values
  in the draw phase so nothing recomposes. On Android 12+ it draws the real
  cover, zoomed and blurred (`BlurredCover`); below that it falls back to the
  cover's colours. This is the right place for reactivity, and the right shape.
- The PCM path has exactly one point where fully-processed stereo float frames
  exist in a buffer before `AudioTrack.write` — inside `startPcmPipeline`, right
  after `processRealtimeStereoChunk`. One tap, nothing else needed.
- `DolbyPassthrough` and the `MediaPlayer` fallback never see PCM. Those paths
  get no levels and must degrade to the existing clock-driven motion.

## Approaches considered

### Sound profiles — where the state lives

1. **A `sound_profiles` Room table keyed by (serverId, trackId) and
   (serverId, albumId), with `SoundController` reading and writing it.**
   Matches `track_overrides` exactly, survives sync, queryable for the Home card,
   and joins to tracks for free. *Chosen.*
2. A JSON blob in `SharedPreferences`. Cheapest, but unqueryable, unbounded, and
   it would have to be parsed to render a Home card. Rejected.
3. Profiles as a first-class library object the user creates by name, applied
   manually. More powerful, but it isn't what was asked for: the user wants *this
   song, the way I like it*, not a preset library. A named-profile layer can sit
   on top later — the table already carries a nullable `name`.

### Sound profiles — when they apply

1. **Never automatically. A quiet, transparent strip above the track title says
   "Apply saved sound?" and applies on tap.** *Chosen* — it is what the user
   described, and it keeps the anti-goal (no surprises) intact.
2. Auto-apply on play, with a toggle. Faster, but a slowed, reverbed song
   starting unannounced is exactly the surprise we said we would not ship. Kept
   as an opt-in setting (`autoApplySoundProfiles`, default off) because some
   people will want it once they trust it.

### The reactive backdrop — where the signal comes from

1. **A level tap in the PCM write loop, gated by a volatile flag the UI sets
   when a reactive style is actually on screen.** Zero cost when unused, no
   allocation per chunk, no permission. *Chosen.*
2. Android's `Visualizer` API. Needs `RECORD_AUDIO`, which breaks the privacy
   promise in `PRIVACY.md` outright. Rejected on sight.
3. Tempo-driven fake motion from the existing BPM analysis. Free, but it is not
   reacting to anything — it would pulse through a silent passage. Rejected as
   dishonest, though the BPM stays as the fallback on the Dolby/MediaPlayer
   paths where no PCM is visible.

### Casting

1. **Hand the Cast device the stream URL and tell the user plainly that the
   sound engine is off while casting.** *Chosen.* A Cast receiver plays the file
   itself; speed, pitch, reverb, EQ and crossfade are Ondalune's pipeline and
   cannot follow the stream. Pretending otherwise would be the worst outcome.
2. Render locally and stream the processed PCM to the device. Keeps the engine,
   but means encoding and serving audio from the phone — a different app. Rejected.

## The design

### 1. Sound profiles

**Data.** New Room entity `sound_profiles`:

| column | meaning |
|---|---|
| `serverId`, `scopeId` | primary key with `scope`; `scopeId` is a track or album id |
| `scope` | `TRACK` or `ALBUM` |
| `name` | nullable; room for named profiles later |
| `speed`, `pitchMode`, `independentPitchRatio` | the `SpeedPitchState` fields |
| `reverbEnabled`, `reverbPreset`, `wetDryMix`, `roomSize`, `decay`, `damping` | the `ReverbState` fields |
| `eqPreset`, `eqBandsDb` | preset name plus the five bands, comma-joined |
| `updatedAt` | epoch millis |

A `SoundProfile` maps to and from the three live states with two pure functions,
`SoundProfile.of(speed, reverb, eq, bands)` and `profile.applyTo(controller)`.
Both are pure and unit-tested; neither touches Room or the engine.

**Saving.** `SoundController` keeps a *baseline* — the sound as it was when the
current track started. When the live state diverges from the baseline by more
than a hair, `pendingProfileSave` turns on. The Speed & Pitch studio sheet then
slides a card in **between the telemetry readout and the Playback Speed card**
(exactly where the user pointed), reading:

```
┌─────────────────────────────────────────┐
│  Save this sound for "Song title"?      │
│  0.80×  ·  −3.86 st  ·  Dreamscape 60%  │
│                                          │
│   [ Save for this song ]  [ Not now ]   │
│          Save for the album →            │
└─────────────────────────────────────────┘
```

It animates in with the same expand/fade the sheet's other cards use. "Not now"
dismisses it for the rest of this track's play.

**Applying.** When a track with a saved profile starts, the player shows a
transparent strip above the title — no card, no scrim, `onSurface` at ~70 %
opacity over the backdrop:

```
          ♪  Apply last audio profile?  ·  0.80× −3.86 st
                    ─────────────────────────
                        Song Title
                         Artist
```

Tap applies and the strip fades out. It auto-fades after 8 seconds untouched.
With `autoApplySoundProfiles` on, the profile applies silently and the strip
instead says *Playing your saved sound* for 3 seconds.

**Removing.** Long-press the strip, or the Sound Profiles menu, offers *Forget
this sound*.

### 2. Play history with adjustments

New Room entity `play_history`: `id`, `serverId`, `trackId`, `playedAt`,
`speed`, `pitchSemitones`, `reverbSummary`, `eqPreset`, `hadProfile`,
`adjusted` (computed: anything differs from neutral). One row written when a
track has played past the scrobble point — reusing the existing scrobble
trigger, so no new timer and no new work. Capped at 500 rows, trimmed on insert.

**Home card** (`HomeSection.SOUND_PROFILES`, off by default, togglable in the
Home layout editor like every other section): a horizontal row of recently
played tracks in the usual track-card shape, but each adjusted play wears a
**ribbon** across the corner of its cover carrying its adjustment — `0.80×`,
`+2 st`, `REVERB`. Unadjusted plays render plain. Tapping plays the track *with
that adjustment restored*, saved profile or not — which is the whole point: the
user liked that version and never saved it.

A **Sound Profiles** screen behind the card's header shows the full history and
every saved profile, with search and delete.

### 3. Reactive artwork backdrop

`OndaluneAudioEngine` gains:

```kotlin
@Volatile var levelsWanted: Boolean = false   // set by the UI
@Volatile private var levelLow = 0f           // ~<250 Hz
@Volatile private var levelMid = 0f
@Volatile private var levelHigh = 0f
fun audioLevels(): FloatArray                 // snapshot, no allocation on the audio thread
```

In the write loop, when and only when `levelsWanted` is true, every 4th frame of
the already-processed buffer feeds three one-pole filters; the band energies are
smoothed with an attack/release envelope (fast attack, slow release) and stored.
Cost when off: one boolean test per chunk. Cost when on: a few hundred
multiply-adds per ~10 ms chunk, no allocation, no lock.

`PlayerBackdrop` gains a sixth style, **Reactive** (`ARTWORK_REACTIVE`): the
same blurred cover as today, whose zoom breathes with the low band, whose drift
rate follows the mid band, and over which the existing `mesh` is drawn with its
amplitude driven by the high band. Nothing new is painted — the cover already on
screen is what moves. It sets `levelsWanted` while composed and clears it in
`onDispose`, and falls back to the clock when levels are flat (Dolby
passthrough, MediaPlayer fallback, or paused). `LocalReducedMotion` disables it
exactly as it disables the others.

### 4. The rest, in order

Each ships on its own with tests and a green pipeline:

| # | Feature | Notes |
|---|---|---|
| 4 | Named EQ curves | `customEqBands` already exists; add save/name/delete and a drawn curve. |
| 5 | Headphone crossfeed | Bauer-style, in `processRealtimeStereoChunk`, one slider in Sonic Lab. |
| 6 | Backup & restore | One JSON file: servers (without secrets), settings, playlists, overrides, profiles. |
| 7 | ListenBrainz scrobbling | Token in the Keystore like server passwords; opt-in, listed in `PRIVACY.md`. |
| 8 | Shareable now-playing card | Render the player's own composition to a bitmap; no new assets. |
| 9 | Year in Ondalune / stats | Built on `play_history`; totals seeded from `playCount`, timeline honest from 2.1. |
| 10 | Mix seeds | Energy/familiarity/era dials over the existing Your Mix machinery. |
| 11 | Smart playlists | Saved rules re-evaluated on sync. |
| 12 | Jukebox mode | Subsonic `jukeboxControl`; mutually exclusive with local playback. |
| 13 | Chromecast | DSP off while casting, said plainly in the UI. |
| 14 | Beat-matched transitions | Tempo ramp in `TransitionMixer`; needs BPM on both sides. |
| 15 | Vinyl mode | Surface noise, wow/flutter, needle drop, in the reverb DSP chain. |
| 16 | Wear OS companion | A new Gradle module and its own APK — a project of its own, last. |

## What is left to the builder

- Exact ribbon geometry and the strip's type scale: follow `StyleSpec`, match
  Signature and Bloom.
- Whether the Sound Profiles screen lives under Library or Settings — put it
  where the Home card's header leads, and nowhere else.
- Trim policy for `play_history` beyond the 500-row cap.

## Testing

Per `AGENTS.md`: unit → integration → e2e → perf → ship, and no change lands
without tests in the same commit.

- Unit: profile ↔ state round-trip, divergence detection, history row building,
  level envelope maths, migration.
- Integration: `SoundController` + fake engine + in-memory Room — save, restart,
  apply, forget.
- E2E (Robolectric, as the repo already does): the save card appears after an
  adjustment, the strip appears on the next play, tapping applies.
- Perf: the level tap must add < 1 % to a synthetic 10-minute render, and must
  measure as a no-op when `levelsWanted` is false.
