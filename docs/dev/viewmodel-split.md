# Splitting OndaluneViewModel

`ui/viewmodel/OndaluneViewModel.kt` is 2,644 lines and holds about 360 members: playback wiring,
library and sync, servers and onboarding, local folders, downloads, lyrics, sound (speed, pitch,
reverb, EQ), appearance settings, app updates, and navigation. Every change touches the same file,
and one domain's state can quietly depend on another's.

The goal is a ViewModel that only wires parts together (under ~600 lines), with each domain in its
own class that can be read and tested alone. Nothing the user sees changes.

## What already exists

- `playback/PlaybackCoordinator.kt` (605 lines) already holds the queue, shuffle, repeat and
  transitions. It talks to the app through two small interfaces, `PlaybackEngine` and
  `PlaybackHost`, and has its own tests (`PlaybackCoordinatorTest`). **Every new part copies this
  pattern.**
- `SettingsStore` holds saved options; `LibraryQueries` holds pure library filtering.
- Only `MainShellScreen.kt` (about 135 members) and `MainActivity.kt` (four) call the ViewModel, so call sites are in
  two files.

## The pattern

```kotlin
class LyricsController(
    private val scope: CoroutineScope,
    private val service: LyricsService,
    private val host: Host,
) {
    interface Host {               // only what this part needs from the others
        fun showBanner(message: String)
    }
    val current: StateFlow<TrackLyricsResult> ...
    fun loadFor(track: SubsonicTrack) { ... }
}

// in OndaluneViewModel
val lyrics = LyricsController(viewModelScope, lyricsService, host = object : LyricsController.Host { ... })
```

Rules:

1. **Plain Kotlin classes**, not more ViewModels. The ViewModel owns them and passes `viewModelScope`,
   so they live and die with it, as now.
2. **A `Host` interface for anything from another domain.** Parts never reach into each other
   directly; the ViewModel connects them. This is how `PlaybackCoordinator` already works.
3. **Move code, don't rewrite it.** Bodies move verbatim first; tidying is a separate commit.
4. **Same saved keys.** Preference names, database tables and worker names don't change, so no
   migration and no lost settings.
5. **No shims.** Call sites in `MainShellScreen` move to the new path in the same commit
   (`viewModel.startUpdate()` becomes `viewModel.updates.start()`); no forwarding functions left
   behind.
6. **The audio engine and DSP are not touched.** The sound part only moves state and saving;
   `processLiveDspStereoBlock` stays a straight pass-through.

## Order

Least connected first, so the pattern is proven on easy parts before the busy ones.

| Step | Part | Moves out | Approx. lines | Talks to |
|---|---|---|---|---|
| 1 ✓ | `UpdateController` | update check, banner, install, rollback, What's new | ~230 | banner only |
| 2 ✓ | `LyricsController` | lyrics loading, search, choosing, server lyrics | ~130 | track start (from playback host) |
| 3 | `SoundController` | speed and pitch, reverb, EQ state and saving | ~260 | audio engine setters |
| 4 | `FolderController` | phone music folders, scans, permissions | ~110 | library refresh |
| 5 | `NavigationState` | destination, detail ids, sheets, jumps, scroll-to-top | ~250 | none (pure UI state) |
| 6 | `AppearanceController` | theme, colours, icon, style, widget, refresh rate | ~180 | icon manager |
| 7 | `DownloadsController` | offline downloads, cache, hints, storage summary | ~170 | library, servers |
| 8 | `ServerController` | servers, test and save, onboarding, ping | ~250 | library sync |
| 9 | `LibraryController` | synced tracks, filters, albums/artists, Home shelves, Your Mix, sync, play counts | ~600 | most parts; last on purpose |

Playback stays in `PlaybackCoordinator`; the ViewModel keeps only its adapter.

## Each step

1. Create the class and its `Host`, move the members verbatim.
2. Wire it in the ViewModel **after** the things it reads. Kotlin builds properties in order, so a
   part created before its dependencies sees nulls at start-up. That is the main crash risk of this
   work.
3. Move the matching lines of the `init` block into the part's own `start()` and call it from the
   same place in `init`, so start-up order stays the same.
4. Update call sites in `MainShellScreen`.
5. `compileDebugKotlin`, `testDebugUnitTest`, `lintDebug`.
6. Add unit tests for the new part using a fake `Host`, at least for its main paths.
7. Emulator smoke test of what moved (for example: update check, lyrics on a new song, speed and
   reverb surviving a restart, folder scan, theme switch).
8. One commit per step on `preview`. Previews get cut every few steps, never mid-step.

## Risks and how they are handled

- **Start-up order** (above): parts are declared after their dependencies; host getters read
  values lazily, as `PlaybackCoordinator`'s host already does.
- **Shared status banner:** stays in the ViewModel; parts post through `Host.showBanner`.
- **Background workers** (`UpdateCheckWorker`, `LibrarySyncWorker`) keep their names and schedules.
- **`NavigationJumpTest`** builds the whole ViewModel; it must keep passing at every step.
- **Settings survive:** test each step on an install that already has saved settings, not only a
  fresh one.

## Done when

- `OndaluneViewModel.kt` is wiring only, under ~600 lines.
- Each part has its own file and tests.
- All existing tests pass, and a full emulator run (sign-in, Home, playback, lyrics, speed and
  reverb, downloads, theme switch, update check) behaves exactly as 1.8.0.2-preview did.
