package io.github.roggzuay.ondalune.data.local

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import io.github.roggzuay.ondalune.audio.isDocumentPath
import io.github.roggzuay.ondalune.audio.setDataSourceOf
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Process
import io.github.roggzuay.ondalune.data.model.SongFeatures
import io.github.roggzuay.ondalune.data.model.SubsonicDownloadEntity
import io.github.roggzuay.ondalune.data.model.analyseSamples
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.ByteOrder

/**
 * Optional on-device song analysis (off by default). It reads up to 45 seconds from the middle of
 * songs stored on the phone (downloads and the phone's own music) and estimates tempo, energy, brightness and how steady the
 * beat is ([analyseSamples]). Nothing is streamed for it and nothing leaves the phone: results are
 * kept in one file in the app's private storage, removed by [clear] when the feature is switched off.
 */
/** One song to analyse: its result key ("serverId_trackId"), its place in line, and where its file is. */
class AnalysisInput(val key: String, val order: Long, val path: suspend () -> String?)

/** Where a song's analysis stands while it hasn't produced a result (results are in [SongAnalysis.features]). */
enum class AnalysisState { QUEUED, ANALYSING, FAILED }

/** Analysis inputs for completed downloads. */
fun Collection<SubsonicDownloadEntity>.analysisInputs(): List<AnalysisInput> =
    filter { it.isCompleted }.map { d -> AnalysisInput(d.downloadKey, d.updatedAtMs) { d.localFilePath } }

class SongAnalysis(private val context: Context) {
    private val file = File(context.filesDir, "song_analysis_v1.json")
    private val _features = MutableStateFlow<Map<String, SongFeatures>>(emptyMap())
    /** Features by download key ("serverId_trackId"). */
    val features: StateFlow<Map<String, SongFeatures>> = _features.asStateFlow()

    private val _states = MutableStateFlow<Map<String, AnalysisState>>(emptyMap())
    /** Songs waiting, being analysed, or that couldn't be; a song with a result has no entry. */
    val states: StateFlow<Map<String, AnalysisState>> = _states.asStateFlow()

    /**
     * The file version ("size:modified") each result or failure belongs to: a replaced file (a new
     * download, an edited song) is analysed again; one that failed isn't retried until it changes.
     */
    private val fingerprints = HashMap<String, String>()
    private val failed = HashSet<String>()

    // One song at a time, so analysis never competes with playback for more than one core.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val worker = Dispatchers.IO.limitedParallelism(1)

    suspend fun load() = withContext(worker) {
        if (!file.exists()) return@withContext
        val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return@withContext
        _features.value = json.keys().asSequence().mapNotNull { key ->
            val o = json.optJSONObject(key) ?: return@mapNotNull null
            o.optString("fp").takeIf { it.isNotBlank() }?.let { fingerprints[key] = it }
            if (o.optBoolean("failed")) {
                failed += key
                return@mapNotNull null
            }
            key to SongFeatures(
                o.optDouble("bpm").toFloat(), o.optDouble("energy").toFloat(),
                o.optDouble("brightness").toFloat(), o.optDouble("dance").toFloat()
            )
        }.toMap()
    }

    /**
     * Analyses the songs with no result for their current file, in [AnalysisInput.order]; returns
     * how many it added. Songs wait as [AnalysisState.QUEUED]; cancelling (a new list, the feature
     * turned off) leaves the unfinished ones unanalysed rather than failed.
     */
    suspend fun analysePending(inputs: Collection<AnalysisInput>): Int = withContext(worker) {
        val pending = inputs.sortedBy { it.order }.mapNotNull { input ->
            val path = input.path() ?: return@mapNotNull null
            val print = fingerprint(path) ?: return@mapNotNull null
            val known = fingerprints[input.key]
            val upToDate = known == null || known == print // results from before fingerprints count
            when {
                input.key in _features.value && upToDate -> null
                input.key in failed && known == print -> null
                else -> Triple(input.key, path, print)
            }
        }
        _states.value = failed.associateWith { AnalysisState.FAILED } + pending.associate { it.first to AnalysisState.QUEUED }
        var added = 0
        var processed = 0
        val previousPriority = Process.getThreadPriority(Process.myTid())
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        try {
            for ((key, path, print) in pending) {
                currentCoroutineContext().ensureActive()
                _states.value = _states.value + (key to AnalysisState.ANALYSING)
                val result = runCatching { decodeExcerpt(path) }.getOrNull()?.let { (samples, rate) -> analyseSamples(samples, rate) }
                currentCoroutineContext().ensureActive()
                fingerprints[key] = print
                if (result == null) {
                    failed += key
                    _features.value = _features.value - key
                    _states.value = _states.value + (key to AnalysisState.FAILED)
                } else {
                    failed -= key
                    _features.value = _features.value + (key to result)
                    _states.value = _states.value - key
                    added++
                }
                if (++processed % 10 == 0) save()
            }
        } finally {
            Process.setThreadPriority(previousPriority)
            _states.value = _states.value.filterValues { it == AnalysisState.FAILED }
        }
        if (processed > 0) save()
        added
    }

    /** "size:modified" of a song file or picked-folder document; null when it can't be read. */
    private fun fingerprint(path: String): String? = if (isDocumentPath(path)) {
        runCatching {
            context.contentResolver.query(
                Uri.parse(path), arrayOf(OpenableColumns.SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null
            )?.use { c -> if (c.moveToFirst()) "${c.getLong(0)}:${c.getLong(1)}" else null }
        }.getOrNull()
    } else File(path).takeIf { it.exists() }?.let { "${it.length()}:${it.lastModified()}" }

    /** Forgets every result and deletes the file. */
    suspend fun clear() = withContext(worker) {
        _features.value = emptyMap()
        _states.value = emptyMap()
        fingerprints.clear()
        failed.clear()
        file.delete()
    }

    private fun save() {
        val json = JSONObject()
        _features.value.forEach { (key, f) ->
            json.put(key, JSONObject().put("bpm", f.bpm.toDouble()).put("energy", f.energy.toDouble())
                .put("brightness", f.brightness.toDouble()).put("dance", f.danceability.toDouble())
                .put("fp", fingerprints[key].orEmpty()))
        }
        failed.forEach { key -> json.put(key, JSONObject().put("failed", true).put("fp", fingerprints[key].orEmpty())) }
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.toString())
        tmp.renameTo(file)
    }

    /** Decodes up to [seconds] from the middle of [audio] to mono floats; returns them with the sample rate. */
    private suspend fun decodeExcerpt(path: String, seconds: Int = 45): Pair<FloatArray, Int>? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSourceOf(context, path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            extractor.seekTo((durationUs / 2 - seconds * 500_000L).coerceAtLeast(0L), MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()

            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false
            val wanted = rate * seconds
            val out = FloatArray(wanted)
            var filled = 0
            var inputDone = false
            val info = MediaCodec.BufferInfo()
            while (filled < wanted) {
                currentCoroutineContext().ensureActive()
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val size = extractor.readSampleData(codec.getInputBuffer(inIndex)!!, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    outIndex >= 0 -> {
                        val buffer = codec.getOutputBuffer(outIndex)!!.order(ByteOrder.nativeOrder())
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        if (floatPcm) {
                            val fb = buffer.asFloatBuffer()
                            while (fb.remaining() >= channels && filled < out.size) {
                                var s = 0f
                                repeat(channels) { s += fb.get() }
                                out[filled++] = s / channels
                            }
                        } else {
                            val sb = buffer.asShortBuffer()
                            while (sb.remaining() >= channels && filled < out.size) {
                                var s = 0f
                                repeat(channels) { s += sb.get() / 32768f }
                                out[filled++] = s / channels
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            return out.copyOf(filled) to rate
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }
}
