package io.github.roggzuay.ondalune.data.motion

import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * Finds, checks and keeps moving artwork, one album at a time. A song's album is looked up once:
 * songs of the same album wait on the same lookup and share its result, "nothing found" is
 * remembered for a while, and the player only ever reads [found]. Nothing here touches playback;
 * every step runs off the main thread and any failure leaves the still cover in place.
 */
class MotionArtworkRepository(
    private val cache: MotionArtworkCache,
    private val providers: List<MotionArtworkProvider>,
    private val settings: () -> MotionArtworkSettings,
    /** Whether the network may be used now, given the Wi-Fi-only setting. */
    private val networkAllowed: (wifiOnly: Boolean) -> Boolean,
    /** A last check on the device that a file plays (it has a picture, the phone can decode it). */
    private val playable: suspend (MotionFormat, String) -> Boolean = { _, _ -> true },
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val retryDelayMs: Long = 2_000L,
    /** Downloads wait while the phone has less than this free, whatever the size limit says. */
    private val keepFreeBytes: Long = 1L shl 30
) {
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val inFlight = HashMap<String, Deferred<MotionAsset?>>()
    private val downloads = Semaphore(2)

    private val _found = MutableStateFlow<Map<String, MotionAsset>>(emptyMap())
    /** Artwork ready to show, by [MotionIdentity.releaseKey]. */
    val found: StateFlow<Map<String, MotionAsset>> = _found.asStateFlow()

    private val _cacheBytes = MutableStateFlow(-1L)
    /** Space the saved copies take; -1 until first measured. */
    val cacheBytes: StateFlow<Long> = _cacheBytes.asStateFlow()

    /** How far the whole-library run has got: albums looked at so far, out of all of them. */
    data class LibraryRun(val done: Int, val total: Int)

    private val _libraryRun = MutableStateFlow<LibraryRun?>(null)
    /** The whole-library run in progress, or null when none is running. */
    val libraryRun: StateFlow<LibraryRun?> = _libraryRun.asStateFlow()

    fun libraryRun(run: LibraryRun?) {
        if (run?.done == 0) outOfRoom = false
        _libraryRun.value = run
    }

    private fun limitBytes() = settings().cacheLimitMb * 1024L * 1024L

    /** True once the saved copies have reached the size limit, or the next one didn't fit. */
    fun full() = outOfRoom || cache.sizeBytes() >= limitBytes()

    /**
     * The moving artwork for [track]'s album: from the cache, or looked up now. Songs of an album
     * already being looked up wait for that lookup instead of starting another. With [fillOnly]
     * (the whole-library run) a download that doesn't fit under the limit is skipped instead of
     * pushing out artwork already saved.
     */
    suspend fun resolve(track: SubsonicTrack, server: SubsonicServerConfig?, fillOnly: Boolean = false): MotionAsset? {
        if (settings().mode == MotionArtworkMode.DISABLED) return null
        val identity = MotionIdentity.of(track)
        val key = identity.releaseKey
        val job = synchronized(inFlight) {
            inFlight[key]?.takeIf { it.isActive } ?: scope.async { lookUp(identity, track, server, fillOnly) }.also { inFlight[key] = it }
        }
        return try {
            job.await()
        } finally {
            synchronized(inFlight) { if (inFlight[key] === job && job.isCompleted) inFlight.remove(key) }
        }
    }

    private suspend fun lookUp(identity: MotionIdentity, track: SubsonicTrack, server: SubsonicServerConfig?, fillOnly: Boolean): MotionAsset? {
        val key = identity.releaseKey
        val prefs = settings()
        val known = cache.get(key)
        known?.asset()?.let { asset ->
            publish(asset)
            cache.touch(key)
            // Kept artwork shows straight away; an old entry is checked again only when lookups are on.
            if (!cache.isStale(known) || !prefs.autoRetrieve) return asset
        }
        // "Nothing found" holds for the network sources; music folders are cheap to look at again.
        val skipNetwork = known?.status == MotionArtworkCache.Status.NONE || !prefs.autoRetrieve
        var incomplete = false
        val candidates = ArrayList<MotionCandidate>()
        for (provider in providers) {
            if (!provider.handles(track, server)) continue
            if (provider.usesNetwork) {
                if (skipNetwork) continue
                if (!networkAllowed(prefs.wifiOnly)) { incomplete = true; continue }
            }
            try {
                candidates += provider.find(identity, track, server)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Unreachable or failing: try again next time rather than remember "none".
                incomplete = true
            }
        }
        for (match in MotionArtworkMatcher.pick(identity, candidates)) {
            val provider = providers.firstOrNull { it.id == match.candidate.providerId } ?: continue
            val asset = try {
                fetch(provider, match, identity, fillOnly)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                incomplete = true
                null
            } ?: continue
            publish(asset)
            enforceLimit()
            return asset
        }
        if (known?.asset() != null) return known.asset()
        if (!incomplete && !skipNetwork) cache.putNone(key)
        return null
    }

    private suspend fun fetch(provider: MotionArtworkProvider, match: MotionMatch, identity: MotionIdentity, fillOnly: Boolean): MotionAsset? {
        val c = match.candidate
        fun asset(location: String, bytes: Long) = MotionAsset(
            releaseKey = identity.releaseKey,
            providerId = provider.id,
            providerName = provider.name,
            attribution = provider.attribution,
            location = location,
            format = c.format,
            album = identity.album,
            artist = identity.artist,
            confidence = match.score,
            bytes = bytes
        )
        if (provider.showsInPlace) {
            if (!playable(c.format, c.source)) return null
            return cache.putFound(asset(c.source, c.sizeBytes.coerceAtLeast(0)), copy = null)
        }
        if (c.sizeBytes > MotionArtworkValidator.MAX_BYTES) return null
        // Not written off: tried again once there is room.
        if (cache.freeBytes() - c.sizeBytes.coerceAtLeast(0) < keepFreeBytes) throw IOException("Phone storage is low")
        if (fillOnly && cache.sizeBytes() + c.sizeBytes.coerceAtLeast(0) > limitBytes()) throw OutOfRoom()
        val part = cache.partFile(identity.releaseKey)
        val file = downloads.withPermit { download(provider, c, part) } ?: return null
        if (!MotionArtworkValidator.check(file, c.format) || !playable(c.format, file.absolutePath)) {
            file.delete()
            return null
        }
        // The size wasn't known up front and it came out too big to fit.
        if (fillOnly && cache.sizeBytes() > limitBytes()) {
            file.delete()
            throw OutOfRoom()
        }
        return cache.putFound(asset(file.absolutePath, file.length()), copy = file)
    }

    /**
     * Downloads [candidate] into [part], picking up where an interrupted download stopped when the
     * source allows it, and trying three times in all. Returns null for a file too large to be
     * artwork; throws when it never completes, so the album is tried again another time.
     */
    internal suspend fun download(provider: MotionArtworkProvider, candidate: MotionCandidate, part: File): File? {
        repeat(3) { attempt ->
            try {
                val have = if (part.isFile) part.length() else 0L
                provider.open(candidate, have).use { s ->
                    val append = have > 0 && s.startsAt == have
                    if (s.totalBytes > MotionArtworkValidator.MAX_BYTES) return null
                    withContext(io) {
                        FileOutputStream(part, append).use { out ->
                            val buf = ByteArray(64 * 1024)
                            var total = if (append) have else 0L
                            var measuredAt = total
                            while (true) {
                                coroutineContext.ensureActive()
                                val n = s.stream.read(buf)
                                if (n < 0) break
                                total += n
                                if (total > MotionArtworkValidator.MAX_BYTES) throw TooLarge()
                                out.write(buf, 0, n)
                                // The space taken counts up as it downloads, not only when done.
                                if (total - measuredAt >= 512 * 1024) {
                                    out.flush()
                                    measuredAt = total
                                    measure()
                                }
                            }
                            if (s.totalBytes > 0 && total != s.totalBytes) throw IOException("Short download")
                        }
                    }
                }
                return part
            } catch (e: CancellationException) {
                throw e
            } catch (_: TooLarge) {
                part.delete()
                return null
            } catch (_: IOException) {
                if (attempt < 2) delay(retryDelayMs * (attempt + 1))
            }
        }
        throw IOException("The download didn't finish")
    }

    private class TooLarge : IOException()

    /** Not written off: the album is looked at again once there is room. */
    private inner class OutOfRoom : IOException("Over the size limit") {
        init { outOfRoom = true }
    }

    @Volatile
    private var outOfRoom = false

    /** Artwork that turned out not to play on this phone is dropped and not offered again for a while. */
    fun reportUnplayable(asset: MotionAsset) {
        _found.update { it - asset.releaseKey }
        scope.launch { cache.putNone(asset.releaseKey); measure() }
    }

    /** The saved copies over the size limit go, least recently shown first. */
    fun enforceLimit() {
        val dropped = cache.evictTo(limitBytes())
        if (dropped.isNotEmpty()) _found.update { it - dropped.toSet() }
        measure()
    }

    /** Albums that had no moving artwork are looked at again the next time they come up. */
    suspend fun lookAgain() = withContext(io) { cache.forgetMisses() }

    suspend fun clear() = withContext(io) {
        synchronized(inFlight) { inFlight.values.forEach { it.cancel() }; inFlight.clear() }
        cache.clear()
        _found.value = emptyMap()
        measure()
    }

    fun measure() {
        _cacheBytes.value = cache.sizeBytes()
    }

    private fun publish(asset: MotionAsset) = _found.update { it + (asset.releaseKey to asset) }
}
