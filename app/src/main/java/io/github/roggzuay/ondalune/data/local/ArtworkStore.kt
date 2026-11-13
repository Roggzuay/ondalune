package io.github.roggzuay.ondalune.data.local

import android.content.Context
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.ui.theme.artworkCacheKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Keeps every cover and artist photo of the library on the phone, so browsing never waits for one
 * and downloaded songs keep their covers offline. They live in the image loader's disk cache, which
 * sits under the app's files (see OndaluneApp), so clearing the cache leaves them. Only the ones
 * not there yet are fetched, a few at a time, and none while [streaming] says a song is coming
 * from the server: on a slow server the covers would take bandwidth the song needs.
 */
object ArtworkStore {
    private val running = Mutex()

    suspend fun saveAll(context: Context, tracks: List<SubsonicTrack>, streaming: () -> Boolean = { false }) {
        // One pass at a time; a later call finds the earlier one's covers already there.
        if (!running.tryLock()) return
        try {
            withContext(Dispatchers.IO) { fetchMissing(context, tracks, streaming) }
        } finally {
            running.unlock()
        }
    }

    private suspend fun fetchMissing(context: Context, tracks: List<SubsonicTrack>, streaming: () -> Boolean) {
        val loader = context.imageLoader
        val disk = loader.diskCache ?: return
        val missing = tracks.asSequence()
            .flatMap { sequenceOf(it.coverArtUrl, it.artistImageUrl) }
            .filter { it.startsWith("http") }
            .distinctBy(::artworkCacheKey)
            .filter { url -> disk.openSnapshot(artworkCacheKey(url))?.also { it.close() } == null }
            .toList()
        val gate = Semaphore(3)
        coroutineScope {
            missing.forEach { url ->
                launch {
                    gate.withPermit {
                        while (streaming()) delay(2_000L)
                        val key = artworkCacheKey(url)
                        loader.execute(
                            ImageRequest.Builder(context)
                                .data(url)
                                .diskCacheKey(key)
                                .memoryCachePolicy(CachePolicy.DISABLED)
                                // Only the file is wanted; a tiny decode keeps it cheap.
                                .size(8)
                                .build()
                        )
                    }
                }
            }
        }
    }
}
