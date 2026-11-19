package io.github.roggzuay.ondalune.ui.viewmodel

import android.app.Application
import android.content.SharedPreferences
import io.github.roggzuay.ondalune.BuildConfig
import io.github.roggzuay.ondalune.data.local.AppUpdateInstaller
import io.github.roggzuay.ondalune.data.local.RollbackState
import io.github.roggzuay.ondalune.data.local.UpdateInstallState
import io.github.roggzuay.ondalune.data.network.AppUpdate
import io.github.roggzuay.ondalune.data.network.UpdateCheckStatus
import io.github.roggzuay.ondalune.data.update.InstallSource
import io.github.roggzuay.ondalune.data.update.UpdateStore
import io.github.roggzuay.ondalune.data.update.installSourceOf
import io.github.roggzuay.ondalune.data.update.updateChecksBuiltIn
import io.github.roggzuay.ondalune.ui.components.ReleaseNotes
import io.github.roggzuay.ondalune.ui.components.majorMinor
import io.github.roggzuay.ondalune.ui.components.whatsNewToShow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App updates: the What's new note, the GitHub update check and its Home banner, downloading and
 * installing a new version, and going back to the previous one. Lives as long as the screen's
 * ViewModel, which owns it.
 */
class UpdateController(
    private val app: Application,
    private val prefs: SharedPreferences,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val _whatsNew = MutableStateFlow(
        whatsNewToShow(BuildConfig.VERSION_NAME, prefs.getString("whats_new_seen", null), isAppUpdate(app))
            // Nothing to say on this launch: remember the version, so a later patch update of a
            // fresh install doesn't count as news.
            .also { if (it == null) markWhatsNewSeen() }
    )
    val whatsNew: StateFlow<ReleaseNotes?> = _whatsNew.asStateFlow()

    fun dismissWhatsNew() {
        _whatsNew.value = null
        markWhatsNewSeen()
    }

    private fun markWhatsNewSeen() {
        prefs.edit().putString("whats_new_seen", majorMinor(BuildConfig.VERSION_NAME)).apply()
    }

    private val updateStore = UpdateStore(app)

    /** How this copy was installed, which decides how it is updated (never by the wrong route). */
    val installSource: InstallSource by lazy { installSourceOf(app) }

    private val _availableUpdate = MutableStateFlow<AppUpdate?>(null)

    /**
     * A newer release than this one, while there is one. It stays (as a small indicator on Home)
     * after its notification is dismissed or its notes are read, and goes once the app is updated.
     */
    val availableUpdate: StateFlow<AppUpdate?> = _availableUpdate.asStateFlow()

    private val _updateBannerFolded = MutableStateFlow(false)

    /** "Later" folds the Home banner into a small indicator; it never hides the update. */
    val updateBannerFolded: StateFlow<Boolean> = _updateBannerFolded.asStateFlow()

    private val _updateDetailsOpen = MutableStateFlow(false)
    val updateDetailsOpen: StateFlow<Boolean> = _updateDetailsOpen.asStateFlow()

    fun showUpdateDetails() {
        if (_availableUpdate.value != null) _updateDetailsOpen.value = true
    }

    fun closeUpdateDetails() {
        _updateDetailsOpen.value = false
    }

    /**
     * Shows the cached release if it is newer (no network), then asks GitHub when the answer is more
     * than an hour old (or [force], as with pulling Home down). Runs at start, each time the app
     * comes back to the front, and on a pull; GitHub's ETag keeps the repeats cheap.
     */
    fun refreshUpdateInfo(force: Boolean = false) {
        val prefs = settings.customization.value
        if (!updateChecksBuiltIn || !prefs.checkForUpdates) return
        publishAvailableUpdate()
        scope.launch {
            updateStore.refresh(force = force, includePrereleases = prefs.updatePrereleases)
            publishAvailableUpdate()
        }
    }

    private val _previousVersion = MutableStateFlow<AppUpdate?>(null)

    /** The release right before this one, which Settings → Updates offers to go back to. */
    val previousVersion: StateFlow<AppUpdate?> = _previousVersion.asStateFlow()

    private fun publishAvailableUpdate() {
        val includePre = settings.customization.value.updatePrereleases
        val update = updateStore.availableFor(BuildConfig.VERSION_NAME)?.takeIf { it.prerelease.not() || includePre }
        _availableUpdate.value = update
        _previousVersion.value = updateStore.previousFor(BuildConfig.VERSION_NAME, includePre)
        _updateBannerFolded.value = update != null && prefs.getString(PREF_UPDATE_FOLDED, null) == update.version
        if (update == null) {
            // Up to date, maybe through an in-app update: its downloaded APK can go.
            if (_rollback.value == null) scope.launch(Dispatchers.IO) { AppUpdateInstaller.clearDownloads(app) }
        } else promptOnce(update.version)
    }

    /**
     * Opens the update's details by itself, once per version, so nobody has to go looking in
     * Settings. Closing it is a "no": Home keeps its banner, and it doesn't pop up again.
     */
    private fun promptOnce(version: String) {
        if (_updateBannerFolded.value || prefs.getString(PREF_UPDATE_PROMPTED, null) == version) return
        prefs.edit().putString(PREF_UPDATE_PROMPTED, version).apply()
        scope.launch {
            delay(2500) // let the launch intro finish first
            if (_availableUpdate.value?.version == version) _updateDetailsOpen.value = true
        }
    }

    private val _updateCheckStatus = MutableStateFlow(UpdateCheckStatus.IDLE)
    val updateCheckStatus: StateFlow<UpdateCheckStatus> = _updateCheckStatus.asStateFlow()

    /** Settings → Updates → Check now: asks GitHub right away, even with daily checks off. */
    fun checkForUpdatesNow() {
        if (!updateChecksBuiltIn || _updateCheckStatus.value == UpdateCheckStatus.CHECKING) return
        scope.launch {
            _updateCheckStatus.value = UpdateCheckStatus.CHECKING
            if (updateStore.refresh(force = true, includePrereleases = settings.customization.value.updatePrereleases) == UpdateStore.Result.FAILED &&
                updateStore.cached() == null
            ) {
                _updateCheckStatus.value = UpdateCheckStatus.FAILED
                return@launch
            }
            publishAvailableUpdate()
            if (_availableUpdate.value != null) {
                _updateBannerFolded.value = false
                _updateCheckStatus.value = UpdateCheckStatus.IDLE
                _updateDetailsOpen.value = true
            } else {
                _updateCheckStatus.value = UpdateCheckStatus.UP_TO_DATE
            }
        }
    }

    /** "Later": the banner folds into the small indicator for this version. */
    fun dismissUpdate() {
        _availableUpdate.value?.let { prefs.edit().putString(PREF_UPDATE_FOLDED, it.version).apply() }
        _updateBannerFolded.value = true
    }

    fun setUpdateNotifications(enabled: Boolean) = settings.update { it.copy(updateNotifications = enabled) }

    fun setUpdatePrereleases(enabled: Boolean) {
        settings.update { it.copy(updatePrereleases = enabled) }
        checkForUpdatesNow()
    }

    private val _updateInstall = MutableStateFlow<UpdateInstallState>(UpdateInstallState.Idle)
    val updateInstall: StateFlow<UpdateInstallState> = _updateInstall.asStateFlow()
    private var updateJob: Job? = null

    /** Downloads the update shown in the banner, then hands it to Android's installer. */
    fun startUpdate() {
        // Only a copy signed like the release APK can install it; anything else gets instructions.
        if (!updateChecksBuiltIn || installSource != InstallSource.GITHUB) return
        val version = _availableUpdate.value?.version ?: return
        if (updateJob?.isActive == true) return
        updateJob = scope.launch {
            val app = app
            _updateInstall.value = UpdateInstallState.Downloading(0f)
            val apk = try {
                AppUpdateInstaller.download(app, version) { _updateInstall.value = UpdateInstallState.Downloading(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _updateInstall.value = UpdateInstallState.Failed("Couldn't download the update. Check your connection and try again.")
                return@launch
            }
            if (!withContext(Dispatchers.IO) { AppUpdateInstaller.isTrustedUpdate(app, apk) }) {
                AppUpdateInstaller.clearDownloads(app)
                _updateInstall.value = UpdateInstallState.Failed("This download isn't signed by Ondalune, so it wasn't installed.")
                return@launch
            }
            _updateInstall.value = if (AppUpdateInstaller.canInstall(app)) {
                UpdateInstallState.Ready(apk)
            } else {
                UpdateInstallState.NeedsPermission(apk)
            }
        }
    }

    private val _rollback = MutableStateFlow<RollbackState?>(null)
    val rollback: StateFlow<RollbackState?> = _rollback.asStateFlow()
    private var rollbackJob: Job? = null

    /** Only a GitHub copy can go back: the older APK is signed with the same key as this one. */
    val canGoBack: Boolean get() = updateChecksBuiltIn && installSource == InstallSource.GITHUB

    fun openRollback() {
        val previous = _previousVersion.value ?: return
        if (canGoBack) _rollback.value = RollbackState.Confirm(previous.version)
    }

    fun closeRollback() {
        rollbackJob?.cancel()
        _rollback.value = null
    }

    /** Downloads the previous version, checks it is Ondalune, and leaves it in Downloads. */
    fun startRollback() {
        val version = _rollback.value?.version ?: return
        if (rollbackJob?.isActive == true) return
        rollbackJob = scope.launch {
            val app = app
            _rollback.value = RollbackState.Downloading(version, 0f)
            val apk = try {
                AppUpdateInstaller.download(app, version) { _rollback.value = RollbackState.Downloading(version, it) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _rollback.value = RollbackState.Failed(version, "Couldn't download it. Check your connection and try again.")
                return@launch
            }
            if (!withContext(Dispatchers.IO) { AppUpdateInstaller.isTrustedUpdate(app, apk) }) {
                AppUpdateInstaller.clearDownloads(app)
                _rollback.value = RollbackState.Failed(version, "This download isn't signed by Ondalune, so it was left alone.")
                return@launch
            }
            val name = "Ondalune-$version.apk"
            _rollback.value = if (withContext(Dispatchers.IO) { AppUpdateInstaller.saveToDownloads(app, apk, name) }) {
                RollbackState.Saved(version, name)
            } else {
                RollbackState.Failed(version, "Couldn't save it to Downloads. Use \"All versions\" to download it from GitHub instead.")
            }
        }
    }

    fun cancelUpdate() {
        updateJob?.cancel()
        _updateInstall.value = UpdateInstallState.Idle
    }

    /** Back from Android's "Install unknown apps" screen: carry on if it was allowed. */
    fun resumeUpdateInstall() {
        val state = _updateInstall.value as? UpdateInstallState.NeedsPermission ?: return
        if (AppUpdateInstaller.canInstall(app)) _updateInstall.value = UpdateInstallState.Ready(state.apk)
    }

    private fun isAppUpdate(app: Application): Boolean = runCatching {
        @Suppress("DEPRECATION")
        app.packageManager.getPackageInfo(app.packageName, 0).let { it.lastUpdateTime > it.firstInstallTime }
    }.getOrDefault(false)

    /** Settings → Updates → Check for updates switched off: the waiting update goes too. */
    fun clearAvailableUpdate() {
        _availableUpdate.value = null
    }
}

private const val PREF_UPDATE_FOLDED = "update_dismissed_version"
private const val PREF_UPDATE_PROMPTED = "update_prompted_version"
