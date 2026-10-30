package io.github.roggzuay.ondalune

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class OndaluneApp : Application(), ImageLoaderFactory {
    /**
     * Artwork-friendly Coil setup: a large disk cache that ignores the no-cache headers many
     * Subsonic servers send for getCoverArt, so covers load once and then come from disk.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("artwork"))
                .maxSizeBytes(512L * 1024 * 1024)
                .build()
        }
        .respectCacheHeaders(false)
        .build()
}
