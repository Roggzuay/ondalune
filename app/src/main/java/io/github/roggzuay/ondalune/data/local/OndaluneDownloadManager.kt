package io.github.roggzuay.ondalune.data.local

import android.content.Context
import io.github.roggzuay.ondalune.data.model.DownloadSpeedLimit
import io.github.roggzuay.ondalune.data.model.OfflineDownloadState
import io.github.roggzuay.ondalune.data.model.OfflineStorageSummary
import io.github.roggzuay.ondalune.data.model.SubsonicDownloadEntity
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.network.OpenSubsonicClient
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Semaphore

/**
 * Persistent Offline Download & Storage Manager for Ondalune.
 *
 * Responsibilities:
 * - Downloads individual tracks or complete albums to `filesDir/ondalune_offline_downloads/server_<id>/`.
 * - Tracks download state (`QUEUED`, `DOWNLOADING`, `COMPLETED`, `FAILED`, `CANCELLED`) and progress
 *   (`0..100%`) in Room (`subsonic_downloads`).
 * - Applies user-selected [DownloadSpeedLimit] bandwidth throttling.
 * - Supports cancellation, retry, single-track removal, and bulk cleanup.
 * - Reports storage usage separating persistent offline downloads from temporary playback cache.
 */
class OndaluneDownloadManager(
    private val context: Context,
    private val dao: OndaluneDao,
    private val client: OpenSubsonicClient,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val activeJobs = ConcurrentHashMap<String, Job>()

    // Two transfers at a time: an album download fills the queue instead of opening a stream per track.
    private val transferSlots = Semaphore(2)

    // Current batch for the aggregate notification: key -> (title, percent).
    private val batch = ConcurrentHashMap<String, Pair<String, Int>>()
    @Volatile private var batchCompleted = 0
    @Volatile private var batchFailed = 0
    @Volatile private var lastNotifyMs = 0L
    private val notifier by lazy { DownloadNotifier(context) }

    private fun notifyBatch(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotifyMs < 500) return
        lastNotifyMs = now
        if (batch.isEmpty()) {
            if (batchCompleted + batchFailed > 0) notifier.showFinished(batchCompleted, batchFailed) else notifier.cancel()
            batchCompleted = 0
            batchFailed = 0
            return
        }
        val total = batchCompleted + batchFailed + batch.size
        val overall = (batchCompleted * 100 + batch.values.sumOf { it.second }) / total.coerceAtLeast(1)
        val current = batch.values.maxByOrNull { it.second }?.first.orEmpty()
        notifier.showProgress(batchCompleted + batchFailed, total, overall, current)
    }
    private val _storageSummary = MutableStateFlow(OfflineStorageSummary())
    val storageSummary: StateFlow<OfflineStorageSummary> = _storageSummary.asStateFlow()

    fun downloadKeyFor(serverId: Long, trackId: String): String = "${serverId}_${trackId}"

    fun observeDownloadsForServer(serverId: Long): Flow<List<SubsonicDownloadEntity>> =
        dao.observeDownloadsForServer(serverId)

    fun observeDownloadsMap(serverId: Long): Flow<Map<String, SubsonicDownloadEntity>> =
        dao.observeDownloadsForServer(serverId).map { list ->
            list.associateBy { it.trackId }
        }

    suspend fun refreshStorageSummary(serverId: Long): OfflineStorageSummary {
        val summary = computeStorageSummary(serverId)
        _storageSummary.value = summary
        return summary
    }

    fun getServerOfflineDir(serverId: Long): File {
        return File(context.filesDir, "ondalune_offline_downloads/server_$serverId").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Returns the persistent offline audio [File] if the track is marked `COMPLETED` and exists on disk.
     */
    suspend fun getOfflineAudioFile(serverId: Long, trackId: String): File? = withContext(Dispatchers.IO) {
        val key = downloadKeyFor(serverId, trackId)
        val record = dao.getDownloadByKey(key) ?: return@withContext null
        if (!record.isCompleted) return@withContext null
        val file = File(record.localFilePath)
        if (file.exists() && file.length() > 0L) file else null
    }

    /**
     * Synchronous check used by the audio engine on an IO/background thread when starting playback.
     */
    fun findExistingOfflineFileSync(serverId: Long, trackId: String): File? {
        val dir = getServerOfflineDir(serverId)
        if (!dir.exists()) return null
        val candidates = dir.listFiles { f ->
            f.isFile && !f.name.endsWith(".part") && f.nameWithoutExtension == sanitizeFileName(trackId)
        }
        return candidates?.firstOrNull { it.length() > 44L }
    }

    fun enqueueTrackDownload(
        server: SubsonicServerConfig,
        track: SubsonicTrack,
        speedLimit: DownloadSpeedLimit = DownloadSpeedLimit.UNLIMITED
    ): Job {
        val key = downloadKeyFor(server.id, track.id)
        activeJobs[key]?.cancel()
        batch[key] = track.title to 0
        notifyBatch(force = true)

        val job = scope.launch {
            // Visible as "queued" right away, so every row of an album shows its state immediately.
            dao.upsertDownload(newEntity(server, track).copy(status = OfflineDownloadState.QUEUED.name))
            transferSlots.withPermit { downloadTrackSuspending(server, track, speedLimit) }
            refreshStorageSummary(server.id)
        }
        activeJobs[key] = job
        job.invokeOnCompletion {
            activeJobs.remove(key, job)
        }
        return job
    }

    fun enqueueAlbumDownload(
        server: SubsonicServerConfig,
        albumTracks: List<SubsonicTrack>,
        speedLimit: DownloadSpeedLimit = DownloadSpeedLimit.UNLIMITED
    ): List<Job> {
        return albumTracks.map { track ->
            enqueueTrackDownload(server, track, speedLimit)
        }
    }

    suspend fun downloadTrackNow(
        server: SubsonicServerConfig,
        track: SubsonicTrack,
        speedLimit: DownloadSpeedLimit = DownloadSpeedLimit.UNLIMITED
    ): SubsonicDownloadEntity = downloadTrackSuspending(server, track, speedLimit)

    /**
     * Executes a track download synchronously within the caller's coroutine context (ideal for
     * deterministic testing as well as background job execution).
     */
    suspend fun downloadTrackSuspending(
        server: SubsonicServerConfig,
        track: SubsonicTrack,
        speedLimit: DownloadSpeedLimit = DownloadSpeedLimit.UNLIMITED
    ): SubsonicDownloadEntity = withContext(Dispatchers.IO) {
        val key = downloadKeyFor(server.id, track.id)
        val initialEntity = newEntity(server, track)
        val targetFile = File(initialEntity.localFilePath)
        dao.upsertDownload(initialEntity)

        // Progress is written at most every 250ms and only when the percent moves; writing every
        // chunk made each row (and the whole list) recompose hundreds of times a second.
        var lastPct = -1
        var lastWriteMs = 0L
        suspend fun reportProgress(written: Long, total: Long) {
            val pct = ((written * 100L) / total.coerceAtLeast(1L)).toInt().coerceIn(0, 99)
            val now = System.currentTimeMillis()
            if (pct == lastPct || now - lastWriteMs < 250) return
            lastPct = pct
            lastWriteMs = now
            batch.computeIfPresent(key) { _, v -> v.first to pct }
            notifyBatch()
            dao.upsertDownload(
                initialEntity.copy(
                    bytesDownloaded = written,
                    totalBytes = total,
                    progressPercent = pct,
                    status = OfflineDownloadState.DOWNLOADING.name,
                    updatedAtMs = now
                )
            )
        }

        try {
            val resultFile = if (server.isDemoServer) {
                synthesizeOfflineMasterWav(track, targetFile, speedLimit) { written, total -> reportProgress(written, total) }
                Result.success(targetFile)
            } else {
                client.downloadAudioStreamToFile(
                    server = server,
                    track = track,
                    targetFile = targetFile,
                    speedLimit = speedLimit
                ) { written, total -> reportProgress(written, total) }
            }

            resultFile.fold(
                onSuccess = { file ->
                    val finalBytes = file.length()
                    val completed = initialEntity.copy(
                        localFilePath = file.absolutePath,
                        bytesDownloaded = finalBytes,
                        totalBytes = finalBytes,
                        progressPercent = 100,
                        status = OfflineDownloadState.COMPLETED.name,
                        errorMessage = "",
                        updatedAtMs = System.currentTimeMillis()
                    )
                    dao.upsertDownload(completed)
                    if (batch.remove(key) != null) batchCompleted++
                    notifyBatch(force = true)
                    refreshStorageSummary(server.id)
                    completed
                },
                onFailure = { err ->
                    targetFile.delete()
                    val failed = initialEntity.copy(
                        progressPercent = 0,
                        status = OfflineDownloadState.FAILED.name,
                        errorMessage = err.localizedMessage ?: "Download failed",
                        updatedAtMs = System.currentTimeMillis()
                    )
                    dao.upsertDownload(failed)
                    if (batch.remove(key) != null) batchFailed++
                    notifyBatch(force = true)
                    refreshStorageSummary(server.id)
                    failed
                }
            )
        } catch (ce: CancellationException) {
            targetFile.delete()
            File(targetFile.parentFile, "${targetFile.name}.part").delete()
            val cancelled = initialEntity.copy(
                progressPercent = 0,
                status = OfflineDownloadState.CANCELLED.name,
                errorMessage = "Cancelled by user",
                updatedAtMs = System.currentTimeMillis()
            )
            dao.upsertDownload(cancelled)
            batch.remove(key)
            notifyBatch(force = true)
            refreshStorageSummary(server.id)
            throw ce
        }
    }

    private fun newEntity(server: SubsonicServerConfig, track: SubsonicTrack): SubsonicDownloadEntity {
        val ext = if (server.isDemoServer) "wav" else track.suffix.lowercase().ifBlank { "flac" }
        val targetFile = File(getServerOfflineDir(server.id), "${sanitizeFileName(track.id)}.$ext")
        return SubsonicDownloadEntity(
            downloadKey = downloadKeyFor(server.id, track.id),
            trackId = track.id,
            serverId = server.id,
            albumId = track.albumId,
            title = track.title,
            artist = track.artist,
            album = track.album,
            suffix = track.suffix,
            localFilePath = targetFile.absolutePath,
            bytesDownloaded = 0L,
            totalBytes = 0L,
            progressPercent = 0,
            status = OfflineDownloadState.DOWNLOADING.name,
            errorMessage = "",
            updatedAtMs = System.currentTimeMillis()
        )
    }

    suspend fun cancelDownload(serverId: Long, trackId: String) = withContext(Dispatchers.IO) {
        val key = downloadKeyFor(serverId, trackId)
        activeJobs.remove(key)?.cancel()
        batch.remove(key)
        notifyBatch(force = true)
        val existing = dao.getDownloadByKey(key)
        if (existing != null) {
            File(existing.localFilePath).let { f ->
                if (!existing.isCompleted) f.delete()
                File(f.parentFile, "${f.name}.part").delete()
            }
            dao.upsertDownload(
                existing.copy(
                    status = OfflineDownloadState.CANCELLED.name,
                    progressPercent = 0,
                    errorMessage = "Cancelled by user",
                    updatedAtMs = System.currentTimeMillis()
                )
            )
        }
        refreshStorageSummary(serverId)
    }

    suspend fun removeDownloadedTrack(serverId: Long, trackId: String) = removeDownload(serverId, trackId)

    suspend fun removeDownload(serverId: Long, trackId: String) = withContext(Dispatchers.IO) {
        val key = downloadKeyFor(serverId, trackId)
        activeJobs.remove(key)?.cancel()
        val existing = dao.getDownloadByKey(key)
        if (existing != null) {
            File(existing.localFilePath).delete()
            File("${existing.localFilePath}.part").delete()
        }
        findExistingOfflineFileSync(serverId, trackId)?.delete()
        dao.deleteDownloadByKey(key)
        refreshStorageSummary(serverId)
    }

    suspend fun clearAllDownloadsForServer(serverId: Long) = withContext(Dispatchers.IO) {
        val prefix = "${serverId}_"
        activeJobs.keys.filter { it.startsWith(prefix) }.forEach { key ->
            activeJobs.remove(key)?.cancel()
        }
        val dir = getServerOfflineDir(serverId)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        dao.deleteAllDownloadsForServer(serverId)
        refreshStorageSummary(serverId)
    }

    suspend fun clearTemporaryCache(serverId: Long = 1L): Long = withContext(Dispatchers.IO) {
        var freedBytes = 0L
        context.cacheDir.listFiles()?.forEach { file ->
            val len = directoryOrFileSize(file)
            if (file.deleteRecursively()) {
                freedBytes += len
            }
        }
        refreshStorageSummary(serverId)
        freedBytes
    }

    suspend fun computeStorageSummary(serverId: Long): OfflineStorageSummary = withContext(Dispatchers.IO) {
        val records = dao.getDownloadsForServerOnce(serverId)
        val completedCount = records.count { it.isCompleted && File(it.localFilePath).exists() }
        val activeCount = records.count {
            it.stateEnum == OfflineDownloadState.DOWNLOADING || it.stateEnum == OfflineDownloadState.QUEUED
        }
        val persistentRoot = File(context.filesDir, "ondalune_offline_downloads")
        val persistentBytes = directoryOrFileSize(persistentRoot)
        val tempCacheBytes = directoryOrFileSize(context.cacheDir)
        OfflineStorageSummary(
            downloadedTrackCount = completedCount,
            activeDownloadCount = activeCount,
            persistentAudioBytes = persistentBytes,
            temporaryCacheBytes = tempCacheBytes
        )
    }

    private fun directoryOrFileSize(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun sanitizeFileName(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "track" }
    }

    /**
     * Synthesizes a clean dry stereo 16-bit PCM WAV file for Demo/Sandbox offline downloads so
     * real-time reverb, EQ, speed, and pitch can process the dry signal live during offline playback.
     */
    private suspend fun synthesizeOfflineMasterWav(
        track: SubsonicTrack,
        targetFile: File,
        speedLimit: DownloadSpeedLimit,
        onProgress: suspend (written: Long, total: Long) -> Unit
    ) {
        val sampleRate = 22050
        val channels = 2
        val durationSec = track.durationSec.coerceIn(10, 40)
        val totalFrames = sampleRate * durationSec
        val dataSize = totalFrames * channels * 2
        val totalBytes = 44L + dataSize.toLong()

        val tempFile = File(targetFile.parentFile, "${targetFile.name}.part")
        targetFile.parentFile?.mkdirs()

        val rootFreq = when (track.artStyleIndex % 5) {
            0 -> 220.0
            1 -> 261.63
            2 -> 196.00
            3 -> 246.94
            else -> 293.66
        }

        val maxBytesPerSec = if (speedLimit.mbpsValue > 0) {
            speedLimit.mbpsValue.toLong() * 1024L * 1024L
        } else {
            0L
        }

        FileOutputStream(tempFile).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(36 + dataSize)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)
            header.putShort(1) // PCM
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(sampleRate * channels * 2)
            header.putShort((channels * 2).toShort())
            header.putShort(16)
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataSize)
            fos.write(header.array())

            val chunkFrames = 4096
            val chunkBytes = ByteArray(chunkFrames * channels * 2)
            var frameCursor = 0
            var writtenBytes = 44L
            var windowStartMs = System.currentTimeMillis()
            var windowBytes = 0L

            while (frameCursor < totalFrames) {
                currentCoroutineContext().ensureActive()
                val framesInBatch = minOf(chunkFrames, totalFrames - frameCursor)
                val bb = ByteBuffer.wrap(chunkBytes, 0, framesInBatch * channels * 2)
                    .order(ByteOrder.LITTLE_ENDIAN)

                for (i in 0 until framesInBatch) {
                    val globalFrame = frameCursor + i
                    val t = globalFrame.toDouble() / sampleRate.toDouble()
                    val padL = sin(2.0 * PI * rootFreq * t) * 0.25 +
                        sin(2.0 * PI * (rootFreq * 1.25) * t) * 0.16
                    val padR = sin(2.0 * PI * rootFreq * t + 0.18) * 0.25 +
                        sin(2.0 * PI * (rootFreq * 1.5) * t) * 0.16
                    val pcmL = (padL.coerceIn(-0.95, 0.95) * Short.MAX_VALUE).toInt().toShort()
                    val pcmR = (padR.coerceIn(-0.95, 0.95) * Short.MAX_VALUE).toInt().toShort()
                    bb.putShort(pcmL)
                    bb.putShort(pcmR)
                }

                val bytesToWrite = framesInBatch * channels * 2
                fos.write(chunkBytes, 0, bytesToWrite)
                frameCursor += framesInBatch
                writtenBytes += bytesToWrite
                windowBytes += bytesToWrite

                if (frameCursor % (chunkFrames * 4) == 0 || frameCursor >= totalFrames) {
                    onProgress(writtenBytes, totalBytes)
                }

                if (maxBytesPerSec > 0L && windowBytes >= maxBytesPerSec / 8L) {
                    val now = System.currentTimeMillis()
                    val elapsedMs = (now - windowStartMs).coerceAtLeast(1L)
                    val expectedMs = (windowBytes * 1000L) / maxBytesPerSec
                    if (expectedMs > elapsedMs) {
                        delay(expectedMs - elapsedMs)
                    }
                    windowStartMs = System.currentTimeMillis()
                    windowBytes = 0L
                }
            }
            fos.flush()
        }

        if (targetFile.exists()) targetFile.delete()
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }
        onProgress(totalBytes, totalBytes)
    }
}
