package io.github.roggzuay.ondalune.data.local

import android.content.Context
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
 * songs already downloaded to the phone and estimates tempo, energy, brightness and how steady the
 * beat is ([analyseSamples]). Nothing is streamed for it and nothing leaves the phone: results are
 * kept in one file in the app's private storage, removed by [clear] when the feature is switched off.
 */
class SongAnalysis(private val context: Context) {
    private val file = File(context.filesDir, "song_analysis_v1.json")
    private val _features = MutableStateFlow<Map<String, SongFeatures>>(emptyMap())
    /** Features by download key ("serverId_trackId"). */
    val features: StateFlow<Map<String, SongFeatures>> = _features.asStateFlow()

    // One song at a time, so analysis never competes with playback for more than one core.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val worker = Dispatchers.IO.limitedParallelism(1)

    suspend fun load() = withContext(worker) {
        if (!file.exists()) return@withContext
        val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return@withContext
        _features.value = json.keys().asSequence().mapNotNull { key ->
            val o = json.optJSONObject(key) ?: return@mapNotNull null
            key to SongFeatures(
                o.optDouble("bpm").toFloat(), o.optDouble("energy").toFloat(),
                o.optDouble("brightness").toFloat(), o.optDouble("dance").toFloat()
            )
        }.toMap()
    }

    /** Analyses the completed downloads that have no result yet, oldest first; returns how many it added. */
    suspend fun analysePending(downloads: Collection<SubsonicDownloadEntity>): Int = withContext(worker) {
        val pending = downloads.filter { it.isCompleted && it.downloadKey !in _features.value }.sortedBy { it.updatedAtMs }
        var added = 0
        val previousPriority = Process.getThreadPriority(Process.myTid())
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        try {
            for (download in pending) {
                currentCoroutineContext().ensureActive()
                val audio = File(download.localFilePath).takeIf { it.exists() } ?: continue
                val excerpt = runCatching { decodeExcerpt(audio) }.getOrNull() ?: continue
                val result = analyseSamples(excerpt.first, excerpt.second) ?: continue
                _features.value = _features.value + (download.downloadKey to result)
                added++
                if (added % 10 == 0) save()
            }
        } finally {
            Process.setThreadPriority(previousPriority)
        }
        if (added > 0) save()
        added
    }

    /** Forgets every result and deletes the file. */
    suspend fun clear() = withContext(worker) {
        _features.value = emptyMap()
        file.delete()
    }

    private fun save() {
        val json = JSONObject()
        _features.value.forEach { (key, f) ->
            json.put(key, JSONObject().put("bpm", f.bpm.toDouble()).put("energy", f.energy.toDouble())
                .put("brightness", f.brightness.toDouble()).put("dance", f.danceability.toDouble()))
        }
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.toString())
        tmp.renameTo(file)
    }

    /** Decodes up to [seconds] from the middle of [audio] to mono floats; returns them with the sample rate. */
    private suspend fun decodeExcerpt(audio: File, seconds: Int = 45): Pair<FloatArray, Int>? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(audio.path)
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
