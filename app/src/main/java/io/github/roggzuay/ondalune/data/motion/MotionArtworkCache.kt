package io.github.roggzuay.ondalune.data.motion

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * What Ondalune knows about each album's moving artwork, kept on disk in [dir]: the saved copy and
 * where it came from, or that none was found. One entry per release, so every song of an album
 * shares it. Saved copies count against a size limit and the least recently shown go first.
 * Artwork found in the user's own music folders is pointed to, never copied.
 */
class MotionArtworkCache(private val dir: File, private val clock: () -> Long = System::currentTimeMillis) {
    enum class Status { FOUND, NONE }

    data class Entry(
        val key: String,
        val status: Status,
        val checkedAt: Long,
        val lastUsedAt: Long,
        val providerId: String = "",
        val providerName: String = "",
        val attribution: String? = null,
        val location: String = "",
        /** True when [location] is a copy in this cache, counted against the limit. */
        val copied: Boolean = false,
        val format: MotionFormat? = null,
        val album: String = "",
        val artist: String = "",
        val confidence: Float = 0f,
        val bytes: Long = 0L
    ) {
        fun asset() = format?.takeIf { status == Status.FOUND }?.let {
            MotionAsset(key, providerId, providerName, attribution, location, it, album, artist, confidence, bytes)
        }
    }

    companion object {
        /**
         * How long "nothing found" holds before the providers are asked again. Short: checking a
         * server cover reads only its first few kilobytes, and a cover changed on the server
         * should start moving by the next day.
         */
        val NONE_TTL_MS = TimeUnit.DAYS.toMillis(1)
        /** How long found artwork is trusted before it is looked up again (it keeps showing meanwhile). */
        val FOUND_TTL_MS = TimeUnit.DAYS.toMillis(90)
        private const val INDEX = "index.json"
    }

    private val index = File(dir, INDEX)
    private var entries: MutableMap<String, Entry>? = null

    @Synchronized
    fun get(key: String): Entry? {
        val all = load()
        val e = all[key] ?: return null
        val now = clock()
        val gone = when (e.status) {
            Status.NONE -> now - e.checkedAt > NONE_TTL_MS
            // A copy that went missing or broke is no entry at all.
            Status.FOUND -> e.copied && !File(e.location).isFile
        }
        if (gone) {
            remove(all, key)
            save()
            return null
        }
        return e
    }

    fun isStale(e: Entry) = e.status == Status.FOUND && clock() - e.checkedAt > FOUND_TTL_MS

    @Synchronized
    fun touch(key: String) {
        val all = load()
        all[key]?.let { all[key] = it.copy(lastUsedAt = clock()); save() }
    }

    @Synchronized
    fun putNone(key: String) {
        val all = load()
        remove(all, key)
        val now = clock()
        all[key] = Entry(key, Status.NONE, now, now)
        save()
    }

    /** Records found artwork; a [copy] made in [partFile] is moved into the cache. */
    @Synchronized
    fun putFound(asset: MotionAsset, copy: File?): MotionAsset {
        val all = load()
        val now = clock()
        var location = asset.location
        var bytes = asset.bytes
        if (copy != null) {
            val target = File(dir, fileName(asset.releaseKey, asset.format))
            if (target != copy) {
                target.delete()
                if (!copy.renameTo(target)) {
                    copy.copyTo(target, overwrite = true)
                    copy.delete()
                }
            }
            location = target.absolutePath
            bytes = target.length()
        }
        all[asset.releaseKey]?.takeIf { it.copied && it.location != location }?.let { File(it.location).delete() }
        val entry = Entry(
            key = asset.releaseKey, status = Status.FOUND, checkedAt = now, lastUsedAt = now,
            providerId = asset.providerId, providerName = asset.providerName, attribution = asset.attribution,
            location = location, copied = copy != null, format = asset.format,
            album = asset.album, artist = asset.artist, confidence = asset.confidence, bytes = bytes
        )
        all[asset.releaseKey] = entry
        save()
        return entry.asset()!!
    }

    /** Forgets every "nothing found", so those albums are looked at again. */
    @Synchronized
    fun forgetMisses() {
        val all = load()
        all.values.filter { it.status == Status.NONE }.map { it.key }.forEach { remove(all, it) }
        save()
    }

    @Synchronized
    fun forget(key: String) {
        remove(load(), key)
        save()
    }

    /** Where a download for [key] goes while it comes in; what is already there is resumed. */
    fun partFile(key: String): File = File(dir.apply { mkdirs() }, safe(key) + ".part")

    /** Saved copies, in bytes (artwork in music folders isn't counted: it isn't Ondalune's). */
    @Synchronized
    fun sizeBytes(): Long = load().values.filter { it.copied }.sumOf { it.bytes } +
        (dir.listFiles { f -> f.name.endsWith(".part") }?.sumOf { it.length() } ?: 0L)

    /** Space left on the storage the cache lives on. */
    fun freeBytes(): Long = dir.usableSpace

    /** Drops the least recently shown copies until the rest fit in [limitBytes]; returns what went. */
    @Synchronized
    fun evictTo(limitBytes: Long): List<String> {
        val all = load()
        var total = all.values.filter { it.copied }.sumOf { it.bytes }
        val dropped = ArrayList<String>()
        for (e in all.values.filter { it.copied }.sortedBy { it.lastUsedAt }) {
            if (total <= limitBytes) break
            remove(all, e.key)
            total -= e.bytes
            dropped += e.key
        }
        if (dropped.isNotEmpty()) save()
        return dropped
    }

    /** Everything goes: copies, half-finished downloads, and what was looked up. */
    @Synchronized
    fun clear() {
        dir.listFiles()?.forEach { it.deleteRecursively() }
        entries = mutableMapOf()
    }

    /** Files no entry points to, left by a crash or an older version. */
    @Synchronized
    fun removeOrphans() {
        val kept = load().values.filter { it.copied }.map { File(it.location).name }.toSet() + INDEX
        dir.listFiles()?.filter { it.isFile && it.name !in kept && !it.name.endsWith(".part") }?.forEach { it.delete() }
    }

    private fun remove(all: MutableMap<String, Entry>, key: String) {
        all.remove(key)?.takeIf { it.copied }?.let { File(it.location).delete() }
        partFile(key).delete()
    }

    private fun fileName(key: String, format: MotionFormat) = safe(key) + "." + format.extension

    private fun safe(key: String) = key.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun load(): MutableMap<String, Entry> = entries ?: run {
        // An unreadable index starts over: it only costs a new lookup.
        val read = runCatching {
            val arr = JSONArray(index.readText())
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }.associateBy { it.key }.toMutableMap()
        }.getOrNull() ?: mutableMapOf()
        entries = read
        read
    }

    private fun save() {
        val all = entries ?: return
        dir.mkdirs()
        val tmp = File(dir, "$INDEX.tmp")
        tmp.writeText(JSONArray(all.values.map(::toJson)).toString())
        if (!tmp.renameTo(index)) {
            tmp.copyTo(index, overwrite = true)
            tmp.delete()
        }
    }

    private fun toJson(e: Entry) = JSONObject()
        .put("key", e.key).put("status", e.status.name).put("checkedAt", e.checkedAt).put("lastUsedAt", e.lastUsedAt)
        .put("providerId", e.providerId).put("providerName", e.providerName).put("attribution", e.attribution ?: JSONObject.NULL)
        .put("location", e.location).put("copied", e.copied).put("format", e.format?.name ?: JSONObject.NULL)
        .put("album", e.album).put("artist", e.artist).put("confidence", e.confidence.toDouble()).put("bytes", e.bytes)

    private fun fromJson(o: JSONObject) = Entry(
        key = o.getString("key"),
        status = Status.valueOf(o.getString("status")),
        checkedAt = o.getLong("checkedAt"),
        lastUsedAt = o.optLong("lastUsedAt", o.getLong("checkedAt")),
        providerId = o.optString("providerId"),
        providerName = o.optString("providerName"),
        attribution = if (o.isNull("attribution")) null else o.optString("attribution"),
        location = o.optString("location"),
        copied = o.optBoolean("copied"),
        format = if (o.isNull("format")) null else runCatching { MotionFormat.valueOf(o.getString("format")) }.getOrNull(),
        album = o.optString("album"),
        artist = o.optString("artist"),
        confidence = o.optDouble("confidence", 0.0).toFloat(),
        bytes = o.optLong("bytes")
    )
}
