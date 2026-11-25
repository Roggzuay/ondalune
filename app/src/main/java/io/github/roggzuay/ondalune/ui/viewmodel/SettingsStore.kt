package io.github.roggzuay.ondalune.ui.viewmodel

import android.content.SharedPreferences
import io.github.roggzuay.ondalune.data.model.AppFont
import io.github.roggzuay.ondalune.data.repository.LibrarySyncWorker
import io.github.roggzuay.ondalune.data.model.AppIconVariant
import io.github.roggzuay.ondalune.data.model.AppThemeMode
import io.github.roggzuay.ondalune.data.model.CornerStylePreset
import io.github.roggzuay.ondalune.data.model.DockStyle
import io.github.roggzuay.ondalune.data.model.DownloadSpeedLimit
import io.github.roggzuay.ondalune.data.model.HomeSection
import io.github.roggzuay.ondalune.data.model.LibrarySortOption
import io.github.roggzuay.ondalune.data.model.LibraryViewMode
import io.github.roggzuay.ondalune.data.model.PlayerBackgroundAnimationStyle
import io.github.roggzuay.ondalune.data.model.PlayerCustomizationSettings
import io.github.roggzuay.ondalune.data.model.CrossfeedLevel
import io.github.roggzuay.ondalune.data.model.ReplayGainMode
import io.github.roggzuay.ondalune.data.model.SeekBarStyle
import io.github.roggzuay.ondalune.data.model.SmartShuffleProfile
import io.github.roggzuay.ondalune.data.model.TabDisplayPreference
import io.github.roggzuay.ondalune.data.model.WidgetLayoutMode
import io.github.roggzuay.ondalune.data.model.WidgetThemeMode
import io.github.roggzuay.ondalune.data.motion.MotionArtworkPreferences
import io.github.roggzuay.ondalune.data.motion.MotionArtworkSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app's saved preferences: appearance, playback and widget options
 * ([PlayerCustomizationSettings]) and each library tab's view, sort and grid. Every change is
 * written straight to [prefs]. Options that also act on something else (the engine, the launcher
 * icon) are set through the view model, which calls [update].
 */
class SettingsStore(private val prefs: SharedPreferences) {
    companion object {
        val CATEGORY_KEYS = listOf("ALBUMS", "ARTISTS", "PLAYLISTS", "GENRES", "FOLDERS", "SONGS", "FAVOURITES")
        /** How many next songs may be saved ahead: 3, 5 or 10, or any custom count in here. */
        val SAVE_AHEAD_RANGE = 1..100
    }

    private val _customization = MutableStateFlow(load())
    val customization: StateFlow<PlayerCustomizationSettings> = _customization.asStateFlow()

    private val _tabPreferences = MutableStateFlow(loadTabs())
    /** View mode, sort and grid columns of each library tab, by [CATEGORY_KEYS] key. */
    val tabPreferences: StateFlow<Map<String, TabDisplayPreference>> = _tabPreferences.asStateFlow()

    fun update(transform: (PlayerCustomizationSettings) -> PlayerCustomizationSettings) {
        val next = transform(_customization.value)
        _customization.value = next
        persist(next)
    }

    fun putTab(categoryKey: String, pref: TabDisplayPreference) {
        val key = categoryKey.uppercase()
        _tabPreferences.value = _tabPreferences.value + (key to pref)
        persistTab(key, pref)
    }

    /** Factory defaults for every option and tab; servers and the library are untouched. */
    fun restoreDefaults(): Map<String, TabDisplayPreference> {
        update { PlayerCustomizationSettings() }
        val defaultTabs = CATEGORY_KEYS.associateWith { key ->
            val pref = TabDisplayPreference.defaultForCategory(key)
            persistTab(key, pref)
            pref
        }
        _tabPreferences.value = defaultTabs
        return defaultTabs
    }

    fun setDockStyle(style: DockStyle) {
        update { it.copy(dockStyle = style) }
    }

    fun setHeaderFont(font: AppFont) = update { it.copy(headerFont = font) }

    fun setBodyFont(font: AppFont) = update { it.copy(bodyFont = font) }

    fun setAppColorSource(source: io.github.roggzuay.ondalune.data.model.AppColorSource) =
        update { it.copy(appColorSource = source) }

    fun markDockIntroSeen() = update { it.copy(seenDockIntro = true) }


    /** Saves a custom Home order (visible sections only). */
    fun setHomeSections(sections: List<HomeSection>) = update { it.copy(homeSections = sections) }

    /** Drops the custom Home order so Home follows the theme's layout again. */
    fun resetHomeSections() = update { it.copy(homeSections = emptyList()) }

    fun setCornerStylePreset(preset: CornerStylePreset) {
        update { it.copy(cornerStylePreset = preset) }
    }

    fun setHideMiniPlayerOnScroll(hide: Boolean) {
        update { it.copy(hideMiniPlayerOnScroll = hide) }
    }

    fun setFallbackArtistToAlbumArt(enabled: Boolean) {
        update { it.copy(fallbackArtistToAlbumArt = enabled) }
    }

    fun setCrossfadeSeconds(seconds: Int) {
        update { it.copy(crossfadeSeconds = seconds.coerceIn(0, 12)) }
    }

    fun setGaplessPlayback(enabled: Boolean) {
        update { it.copy(gaplessPlayback = enabled) }
    }

    fun setAutoplaySimilar(enabled: Boolean) {
        update { it.copy(autoplaySimilar = enabled) }
    }

    fun setFindLyricsOnline(enabled: Boolean) {
        update { it.copy(findLyricsOnline = enabled) }
    }

    fun setDolbyPassthrough(enabled: Boolean) {
        update { it.copy(dolbyPassthrough = enabled) }
    }

    fun setReplayGainMode(mode: ReplayGainMode) {
        update { it.copy(replayGainMode = mode) }
    }

    fun setAutoScrobble(enabled: Boolean) {
        update { it.copy(autoScrobble = enabled) }
    }

    fun setAutoApplySoundProfiles(enabled: Boolean) {
        update { it.copy(autoApplySoundProfiles = enabled) }
    }

    fun setKeepSoundHistory(enabled: Boolean) {
        update { it.copy(keepSoundHistory = enabled) }
    }

    fun setCrossfeed(level: CrossfeedLevel) {
        update { it.copy(crossfeed = level) }
    }

    fun setRescanFoldersOnLaunch(enabled: Boolean) {
        update { it.copy(rescanFoldersOnLaunch = enabled) }
    }

    fun setLibrarySyncHours(hours: Int) {
        update { it.copy(librarySyncHours = hours) }
    }

    fun setAnimatedSquigglyWave(enabled: Boolean) {
        update {
            it.copy(
                animatedSquigglyWave = enabled,
                seekBarStyle = if (enabled) SeekBarStyle.SQUIGGLY_WAVE else SeekBarStyle.CLASSIC_LINEAR
            )
        }
    }

    fun setMotionArtwork(transform: (MotionArtworkSettings) -> MotionArtworkSettings) =
        update { it.copy(motionArtwork = transform(it.motionArtwork)) }

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        update { it.copy(hapticWobbleButtons = enabled) }
    }

    fun setReadAheadBuffer(tracksCount: Int) {
        update { it.copy(readAheadTrackBuffer = tracksCount.coerceIn(SAVE_AHEAD_RANGE)) }
    }

    fun setWidgetShowArtwork(show: Boolean) {
        update { it.copy(widgetShowArtwork = show) }
    }

    fun setWidgetShowFavoriteButton(show: Boolean) {
        update { it.copy(widgetShowFavoriteButton = show) }
    }

    fun setWidgetShowLyrics(show: Boolean) {
        update { it.copy(widgetShowLyrics = show) }
    }

    private fun loadTabs(): Map<String, TabDisplayPreference> {
        return CATEGORY_KEYS.associateWith { key ->
            val defaultPref = TabDisplayPreference.defaultForCategory(key)
            val viewName = prefs.getString("tab_${key}_view", defaultPref.viewMode.name)
            val sortName = prefs.getString("tab_${key}_sort", defaultPref.sortOption.name)
            val asc = prefs.getBoolean("tab_${key}_asc", defaultPref.ascending)
            val cols = prefs.getInt("tab_${key}_cols", defaultPref.gridColumns).coerceIn(2, 4)
            TabDisplayPreference(
                viewMode = LibraryViewMode.entries.find { it.name == viewName } ?: defaultPref.viewMode,
                sortOption = LibrarySortOption.entries.find { it.name == sortName } ?: defaultPref.sortOption,
                ascending = asc,
                gridColumns = cols
            )
        }
    }

    private fun persistTab(categoryKey: String, pref: TabDisplayPreference) {
        val key = categoryKey.uppercase()
        prefs.edit()
            .putString("tab_${key}_view", pref.viewMode.name)
            .putString("tab_${key}_sort", pref.sortOption.name)
            .putBoolean("tab_${key}_asc", pref.ascending)
            .putInt("tab_${key}_cols", pref.gridColumns.coerceIn(2, 4))
            .apply()
    }

    private fun load(): PlayerCustomizationSettings {
        val defaults = PlayerCustomizationSettings()
        if (!prefs.contains("cfg_initialized")) return defaults
        return PlayerCustomizationSettings(
            appIconVariant = AppIconVariant.entries.find { it.name == prefs.getString("cfg_app_icon_variant", defaults.appIconVariant.name) } ?: defaults.appIconVariant,
            dockStyle = DockStyle.entries.find { it.name == prefs.getString("cfg_dock_style", defaults.dockStyle.name) } ?: defaults.dockStyle,
            themeMode = AppThemeMode.entries.find { it.name == prefs.getString("cfg_theme_mode", defaults.themeMode.name) } ?: defaults.themeMode,
            cornerStylePreset = CornerStylePreset.entries.find { it.name == prefs.getString("cfg_corner_preset", defaults.cornerStylePreset.name) } ?: defaults.cornerStylePreset,
            headerFont = AppFont.entries.find { it.name == prefs.getString("cfg_header_font", null) } ?: defaults.headerFont,
            bodyFont = AppFont.entries.find { it.name == prefs.getString("cfg_body_font", null) } ?: defaults.bodyFont,
            appColorSource = io.github.roggzuay.ondalune.data.model.AppColorSource.entries
                .find { it.name == prefs.getString("cfg_app_color_source", null) } ?: defaults.appColorSource,
            seenDockIntro = prefs.getBoolean("cfg_seen_dock_intro", defaults.seenDockIntro),
            homeSections = prefs.getString("cfg_home_sections", "").orEmpty().split(',')
                .mapNotNull { name -> HomeSection.entries.find { it.name == name } },
            iconFollowsTheme = prefs.getBoolean("cfg_icon_follows_theme", defaults.iconFollowsTheme),
            playerBackgroundStyle = PlayerBackgroundAnimationStyle.entries.find { it.name == prefs.getString("cfg_bg_style_v2", defaults.playerBackgroundStyle.name) } ?: defaults.playerBackgroundStyle,
            seekBarStyle = SeekBarStyle.entries.find { it.name == prefs.getString("cfg_seekbar_style", defaults.seekBarStyle.name) } ?: defaults.seekBarStyle,
            showRemainingTime = prefs.getBoolean("cfg_show_remaining_time", defaults.showRemainingTime),
            showTechnicalFormatBadge = prefs.getBoolean("cfg_show_tech_badge", defaults.showTechnicalFormatBadge),
            showPlayerStarButton = prefs.getBoolean("cfg_show_player_star", defaults.showPlayerStarButton),
            hideMiniPlayerOnScroll = prefs.getBoolean("cfg_hide_mini_player_on_scroll", defaults.hideMiniPlayerOnScroll),
            showCodecBadgesInLists = prefs.getBoolean("cfg_show_codec_lists", defaults.showCodecBadgesInLists),
            // "cfg_fallback_artist_album" is deliberately not read back. No screen ever set it, so
            // every phone holds the false this used to default to, and reading it would leave those
            // artists on an empty header for good.
            defaultGridColumns = prefs.getInt("cfg_default_grid_cols", defaults.defaultGridColumns).coerceIn(2, 4),
            crossfadeSeconds = prefs.getInt("cfg_crossfade_sec", defaults.crossfadeSeconds).coerceIn(0, 12),
            gaplessPlayback = prefs.getBoolean("cfg_gapless", defaults.gaplessPlayback),
            autoplaySimilar = prefs.getBoolean("cfg_autoplay", defaults.autoplaySimilar),
            findLyricsOnline = prefs.getBoolean("cfg_find_lyrics_online", defaults.findLyricsOnline),
            dolbyPassthrough = prefs.getBoolean("cfg_dolby_passthrough", defaults.dolbyPassthrough),
            songAnalysis = prefs.getBoolean("cfg_song_analysis", defaults.songAnalysis),
            replayGainMode = ReplayGainMode.entries.find { it.name == prefs.getString("cfg_replay_gain", defaults.replayGainMode.name) } ?: defaults.replayGainMode,
            autoScrobble = prefs.getBoolean("cfg_auto_scrobble", defaults.autoScrobble),
            autoApplySoundProfiles = prefs.getBoolean("cfg_auto_sound_profiles", defaults.autoApplySoundProfiles),
            keepSoundHistory = prefs.getBoolean("cfg_keep_sound_history", defaults.keepSoundHistory),
            crossfeed = runCatching {
                CrossfeedLevel.valueOf(prefs.getString("cfg_crossfeed", defaults.crossfeed.name)!!)
            }.getOrDefault(defaults.crossfeed),
            rescanFoldersOnLaunch = prefs.getBoolean("cfg_rescan_folders", defaults.rescanFoldersOnLaunch),
            librarySyncHours = prefs.getInt(LibrarySyncWorker.PREF_HOURS, defaults.librarySyncHours),
            resumePlaybackOnLaunch = prefs.getBoolean("cfg_resume_on_launch", defaults.resumePlaybackOnLaunch),
            pauseOnHeadphoneDisconnect = prefs.getBoolean("cfg_pause_noisy", defaults.pauseOnHeadphoneDisconnect),
            animatedSquigglyWave = prefs.getBoolean("cfg_squiggly_wave", defaults.animatedSquigglyWave),
            hapticWobbleButtons = prefs.getBoolean("cfg_haptics", defaults.hapticWobbleButtons),
            highRefreshRate120Hz = prefs.getBoolean("cfg_120hz", defaults.highRefreshRate120Hz),
            smartShuffleProfile = SmartShuffleProfile.entries.find { it.name == prefs.getString("cfg_smart_shuffle", defaults.smartShuffleProfile.name) } ?: defaults.smartShuffleProfile,
            downloadSpeedLimit = DownloadSpeedLimit.entries.find { it.name == prefs.getString("cfg_dl_limit", defaults.downloadSpeedLimit.name) } ?: defaults.downloadSpeedLimit,
            readAheadTrackBuffer = prefs.getInt("cfg_read_ahead", defaults.readAheadTrackBuffer).coerceIn(SAVE_AHEAD_RANGE),
            wifiTranscodeFormat = prefs.getString("cfg_wifi_fmt", defaults.wifiTranscodeFormat) ?: defaults.wifiTranscodeFormat,
            wifiMaxBitRate = prefs.getInt("cfg_wifi_kbps", defaults.wifiMaxBitRate),
            mobileTranscodeFormat = prefs.getString("cfg_mobile_fmt", defaults.mobileTranscodeFormat) ?: defaults.mobileTranscodeFormat,
            mobileMaxBitRate = prefs.getInt("cfg_mobile_kbps", defaults.mobileMaxBitRate),
            widgetLayoutMode = WidgetLayoutMode.entries.find { it.name == prefs.getString("cfg_widget_layout", defaults.widgetLayoutMode.name) } ?: defaults.widgetLayoutMode,
            widgetThemeMode = WidgetThemeMode.entries.find { it.name == prefs.getString("cfg_widget_theme", defaults.widgetThemeMode.name) } ?: defaults.widgetThemeMode,
            widgetShowArtwork = prefs.getBoolean("cfg_widget_artwork", defaults.widgetShowArtwork),
            widgetShowFavoriteButton = prefs.getBoolean("cfg_widget_fav", defaults.widgetShowFavoriteButton),
            widgetShowLyrics = prefs.getBoolean("cfg_widget_lyrics", defaults.widgetShowLyrics),
            checkForUpdates = prefs.getBoolean("cfg_check_updates", defaults.checkForUpdates),
            updateNotifications = prefs.getBoolean("cfg_update_notifications", defaults.updateNotifications),
            updatePrereleases = prefs.getBoolean("cfg_update_prereleases", defaults.updatePrereleases),
            motionArtwork = MotionArtworkPreferences.read(prefs)
        )
    }

    private fun persist(s: PlayerCustomizationSettings) {
        prefs.edit()
            .putBoolean("cfg_initialized", true)
            .putString("cfg_app_icon_variant", s.appIconVariant.name)
            .putString("cfg_dock_style", s.dockStyle.name)
            .putString("cfg_theme_mode", s.themeMode.name)
            .putString("cfg_corner_preset", s.cornerStylePreset.name)
            .putString("cfg_header_font", s.headerFont.name)
            .putString("cfg_body_font", s.bodyFont.name)
            .putString("cfg_app_color_source", s.appColorSource.name)
            .putBoolean("cfg_seen_dock_intro", s.seenDockIntro)
            .putString("cfg_home_sections", s.homeSections.joinToString(",") { it.name })
            .putBoolean("cfg_icon_follows_theme", s.iconFollowsTheme)
            .putString("cfg_bg_style_v2", s.playerBackgroundStyle.name)
            .putString("cfg_seekbar_style", s.seekBarStyle.name)
            .putBoolean("cfg_show_remaining_time", s.showRemainingTime)
            .putBoolean("cfg_show_tech_badge", s.showTechnicalFormatBadge)
            .putBoolean("cfg_show_player_star", s.showPlayerStarButton)
            .putBoolean("cfg_hide_mini_player_on_scroll", s.hideMiniPlayerOnScroll)
            .putBoolean("cfg_show_codec_lists", s.showCodecBadgesInLists)
            .putInt("cfg_default_grid_cols", s.defaultGridColumns)
            .putInt("cfg_crossfade_sec", s.crossfadeSeconds)
            .putBoolean("cfg_gapless", s.gaplessPlayback)
            .putBoolean("cfg_autoplay", s.autoplaySimilar)
            .putBoolean("cfg_find_lyrics_online", s.findLyricsOnline)
            .putBoolean("cfg_dolby_passthrough", s.dolbyPassthrough)
            .putBoolean("cfg_song_analysis", s.songAnalysis)
            .putString("cfg_replay_gain", s.replayGainMode.name)
            .putBoolean("cfg_auto_scrobble", s.autoScrobble)
            .putBoolean("cfg_auto_sound_profiles", s.autoApplySoundProfiles)
            .putBoolean("cfg_keep_sound_history", s.keepSoundHistory)
            .putString("cfg_crossfeed", s.crossfeed.name)
            .putBoolean("cfg_rescan_folders", s.rescanFoldersOnLaunch)
            .putInt(LibrarySyncWorker.PREF_HOURS, s.librarySyncHours)
            .putBoolean("cfg_resume_on_launch", s.resumePlaybackOnLaunch)
            .putBoolean("cfg_pause_noisy", s.pauseOnHeadphoneDisconnect)
            .putBoolean("cfg_squiggly_wave", s.animatedSquigglyWave)
            .putBoolean("cfg_haptics", s.hapticWobbleButtons)
            .putBoolean("cfg_120hz", s.highRefreshRate120Hz)
            .putString("cfg_smart_shuffle", s.smartShuffleProfile.name)
            .putString("cfg_dl_limit", s.downloadSpeedLimit.name)
            .putInt("cfg_read_ahead", s.readAheadTrackBuffer)
            .putString("cfg_wifi_fmt", s.wifiTranscodeFormat)
            .putInt("cfg_wifi_kbps", s.wifiMaxBitRate)
            .putString("cfg_mobile_fmt", s.mobileTranscodeFormat)
            .putInt("cfg_mobile_kbps", s.mobileMaxBitRate)
            .putString("cfg_widget_layout", s.widgetLayoutMode.name)
            .putString("cfg_widget_theme", s.widgetThemeMode.name)
            .putBoolean("cfg_widget_artwork", s.widgetShowArtwork)
            .putBoolean("cfg_widget_fav", s.widgetShowFavoriteButton)
            .putBoolean("cfg_widget_lyrics", s.widgetShowLyrics)
            .putBoolean("cfg_check_updates", s.checkForUpdates)
            .putBoolean("cfg_update_notifications", s.updateNotifications)
            .putBoolean("cfg_update_prereleases", s.updatePrereleases)
            .let { MotionArtworkPreferences.write(it, s.motionArtwork) }
            .apply()
    }
}
