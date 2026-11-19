package io.github.roggzuay.ondalune.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import io.github.roggzuay.ondalune.data.model.LocalFolder
import io.github.roggzuay.ondalune.data.repository.OndaluneRepository
import io.github.roggzuay.ondalune.data.source.FolderPermissions
import io.github.roggzuay.ondalune.data.source.FolderReader
import io.github.roggzuay.ondalune.data.source.folderLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Music folders on this phone: picking them, keeping access, and scanning them into the library.
 * The ViewModel owns it; scans run on [scope] one after another.
 */
class FolderController(
    private val app: Application,
    private val repository: OndaluneRepository,
    private val scope: CoroutineScope,
    private val showBanner: (String) -> Unit,
) {
    /** Folders picked in Settings → Music sources; only their music joins the library. */
    val localFolders: StateFlow<List<LocalFolder>> = repository.observeLocalFolders()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** A folder scan under way: which folder, how many songs found and read. */
    data class FolderScan(val folderName: String, val found: Int, val read: Int)

    private val _folderScan = MutableStateFlow<FolderScan?>(null)
    val folderScan: StateFlow<FolderScan?> = _folderScan.asStateFlow()
    private var folderScanJob: Job? = null
    private val folderReader by lazy { FolderReader(app) }
    private val folderPermissions by lazy { FolderPermissions(app) }

    /** Adds a folder picked in Android's folder picker, keeps access to it, and reads its music. */
    fun addMusicFolder(treeUri: Uri) {
        scope.launch {
            if (runCatching { folderPermissions.take(treeUri) }.isFailure) {
                showBanner("Ondalune couldn't keep access to that folder")
                return@launch
            }
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val name = docId.substringAfter(':').substringAfterLast('/').ifBlank { folderLocation(docId).substringAfterLast(" › ") }
            val alreadyPicked = localFolders.value.any { it.treeUri == treeUri.toString() }
            when (repository.addLocalFolder(treeUri.toString(), docId, name)) {
                OndaluneRepository.FolderAdd.ALREADY_INCLUDED -> {
                    if (!alreadyPicked) folderPermissions.release(treeUri.toString())
                    showBanner("\"$name\" is already in your music folders")
                }
                OndaluneRepository.FolderAdd.REPLACED_INNER -> {
                    showBanner("\"$name\" replaces the folders inside it")
                    scanFolders(repository.localFolders().filter { it.treeUri == treeUri.toString() })
                }
                OndaluneRepository.FolderAdd.ADDED ->
                    scanFolders(repository.localFolders().filter { it.treeUri == treeUri.toString() })
            }
        }
    }

    /** Forgets a folder and its songs; the files stay on the phone. */
    fun removeMusicFolder(folder: LocalFolder) {
        scope.launch {
            if (_folderScan.value?.folderName == folder.name) folderScanJob?.cancel()
            repository.removeLocalFolder(folder)
        }
    }

    fun rescanMusicFolder(folder: LocalFolder) = scanFolders(listOf(folder))

    fun rescanAllMusicFolders() = scanFolders(localFolders.value)

    fun cancelFolderScan() {
        folderScanJob?.cancel()
    }

    /** Scans [folders] one after another, after any scan already running; never on the main thread. */
    fun scanFolders(folders: List<LocalFolder>) {
        if (folders.isEmpty()) return
        val before = folderScanJob
        folderScanJob = scope.launch(Dispatchers.IO) {
            before?.join()
            try {
                for (folder in folders) {
                    _folderScan.value = FolderScan(folder.name, 0, 0)
                    val result = repository.scanLocalFolder(folder, folderReader) { found, read ->
                        _folderScan.value = FolderScan(folder.name, found, read)
                    }
                    if (!result.accessible) {
                        showBanner("Can't open \"${folder.name}\". Its songs stay; allow it again in Settings → Music sources")
                    }
                }
            } finally {
                _folderScan.value = null
            }
        }
    }
}
