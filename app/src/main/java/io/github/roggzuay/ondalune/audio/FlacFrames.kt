package io.github.roggzuay.ondalune.audio

import android.content.Context
import android.media.MediaFormat
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

/**
 * A FLAC file's compressed frames, one at a time, for Android's FLAC decoder. Android's extractor
 * decodes FLAC itself and always hands over 16-bit, so a 24-bit FLAC is read here instead and the
 * decoder asked for float. Frames are found by their sync code, a header whose CRC-8 checks out and
 * a sample number that follows on from the frame before, the way the format intends.
 */
internal class FlacFrames private constructor(
    private val src: ByteSource,
    private val info: StreamInfo,
    private val firstFrame: Long,
    /** SEEKTABLE points: sample number and byte offset from the first frame. */
    private val seekSamples: LongArray,
    private val seekOffsets: LongArray
) : EncodedInput {
    class StreamInfo(
        val minBlockSize: Int,
        val maxBlockSize: Int,
        val maxFrameSize: Int,
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val totalSamples: Long,
        /** The 34 bytes as they are in the file, for the decoder's codec-specific data. */
        val raw: ByteArray
    )

    /** A frame at [pos] starting at song sample [sample]; [sample] -1 marks the end of the data at [pos]. */
    private class Header(val pos: Long, val sample: Long, val blockSize: Int, val length: Int)

    private var variableBlocks = false
    private var cur: Header? = null
    private var next: Header? = null

    // Bytes read but not yet passed on. The frame being read ([anchor] on) stays in it.
    private var window = ByteArray(1 shl 20)
    private var winStart = 0L
    private var winLen = 0
    private var ended = false
    private var anchor = 0L

    /** How far past a frame's start its successor may be before a frame that doesn't follow on is accepted. */
    private val maxScan = if (info.maxFrameSize > 0) info.maxFrameSize + 64 else 1 shl 20

    /** The format Android's FLAC decoder needs: the stream's details and its STREAMINFO block. */
    fun format(): MediaFormat = MediaFormat.createAudioFormat("audio/flac", info.sampleRate, info.channels).apply {
        // "fLaC", then STREAMINFO as the last metadata block (type 0, 34 bytes).
        val csd = ByteBuffer.allocate(4 + 4 + 34)
        csd.put("fLaC".toByteArray(Charsets.US_ASCII)).put(byteArrayOf(0x80.toByte(), 0, 0, 34)).put(info.raw).flip()
        setByteBuffer("csd-0", csd)
        setInteger("bits-per-sample", info.bitsPerSample)
        val frameBytes = if (info.maxFrameSize > 0) info.maxFrameSize else info.maxBlockSize * info.channels * 4
        setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, frameBytes + 1024)
        if (info.totalSamples > 0) {
            val durationUs = info.totalSamples * 1_000_000L / info.sampleRate
            setLong(MediaFormat.KEY_DURATION, durationUs)
            if (src.size > 0 && durationUs > 0) setInteger(MediaFormat.KEY_BIT_RATE, ((src.size - firstFrame) * 8_000_000L / durationUs).toInt())
        }
    }

    override val sampleTime: Long get() = cur?.let { it.sample * 1_000_000L / info.sampleRate } ?: -1L

    override fun readSampleData(buffer: ByteBuffer, offset: Int): Int {
        val h = cur ?: return -1
        val end = (next ?: after(h, strict = false).also { next = it })!!.pos
        val length = (end - h.pos).toInt()
        if (!ensure(h.pos, length) || length > buffer.capacity() - offset) throw IOException("FLAC frame of $length bytes")
        buffer.clear()
        buffer.position(offset)
        buffer.put(window, (h.pos - winStart).toInt(), length)
        return length
    }

    override fun advance(): Boolean {
        val h = cur ?: return false
        val n = next ?: after(h, strict = false)!!
        next = null
        cur = n.takeIf { it.sample >= 0 }
        cur?.let { anchor = it.pos }
        return cur != null
    }

    /** Moves to the frame that holds [timeUs]: bisects the file (narrowed by the seek table) on frame headers. */
    override fun seekTo(timeUs: Long) {
        val target = timeUs.coerceAtLeast(0L) * info.sampleRate / 1_000_000L
        var lo = firstFrame
        var hi = src.size
        for (i in seekSamples.indices) {
            if (seekSamples[i] <= target) lo = max(lo, firstFrame + seekOffsets[i])
            else if (hi < 0 || firstFrame + seekOffsets[i] < hi) hi = firstFrame + seekOffsets[i]
        }
        if (hi > 0) {
            while (hi - lo > 1 shl 16) {
                val mid = lo + (hi - lo) / 2
                val h = confirmedAtOrAfter(mid, hi)
                if (h == null || h.sample > target) hi = mid else lo = h.pos
            }
        }
        next = null
        var h = confirmedAtOrAfter(lo, -1L)
        cur = h
        while (h != null) {
            anchor = h.pos
            val n = after(h, strict = false)!!
            if (n.sample < 0 || n.sample > target) {
                next = n
                break
            }
            h = n
            cur = n
        }
        cur?.let { anchor = it.pos }
    }

    override fun release() {
        runCatching { src.close() }
    }

    /** The frame after [h], or the end of the data. [strict]: null when no frame follows on within reach. */
    private fun after(h: Header, strict: Boolean): Header? {
        val expected = h.sample + h.blockSize
        val limit = h.pos + maxScan
        var p = h.pos + h.length + 1
        while (true) {
            val b = byteAt(p)
            if (b < 0) return Header(p, -1L, 0, 0)
            if (b == 0xFF) {
                val n = headerAt(p)
                if (n != null && (n.sample == expected || (!strict && p > limit))) return n
            }
            if (strict && p > limit) return null
            p++
        }
    }

    /** The first frame at or after [from] (and before [limit], when that is set) that the next frame confirms. */
    private fun confirmedAtOrAfter(from: Long, limit: Long): Header? {
        anchor = from
        var p = from
        while (limit < 0 || p < limit) {
            val b = byteAt(p)
            if (b < 0) return null
            if (b == 0xFF) {
                val h = headerAt(p)
                if (h != null && after(h, strict = true) != null) return h
            }
            p++
        }
        return null
    }

    private fun headerAt(p: Long): Header? {
        val b1 = byteAt(p + 1)
        if (byteAt(p) != 0xFF || b1 < 0 || b1 and 0xFE != 0xF8 || (b1 and 1 == 1) != variableBlocks) return null
        val b2 = byteAt(p + 2)
        val b3 = byteAt(p + 3)
        if (b2 < 0 || b3 < 0) return null
        val sizeCode = b2 shr 4
        val rateCode = b2 and 0xF
        val assignment = b3 shr 4
        val depthCode = (b3 shr 1) and 7
        if (sizeCode == 0 || rateCode == 15 || assignment > 10 || depthCode == 3 || b3 and 1 != 0) return null
        if ((if (assignment < 8) assignment + 1 else 2) != info.channels) return null
        var q = p + 4
        // The frame or sample number, UTF-8 style: the first byte says how many follow.
        val first = byteAt(q++)
        val more = when {
            first < 0 -> return null
            first and 0x80 == 0 -> 0
            first and 0xE0 == 0xC0 -> 1
            first and 0xF0 == 0xE0 -> 2
            first and 0xF8 == 0xF0 -> 3
            first and 0xFC == 0xF8 -> 4
            first and 0xFE == 0xFC -> 5
            first == 0xFE -> 6
            else -> return null
        }
        var number = (first and (if (more == 0) 0x7F else 0x7F shr (more + 1))).toLong()
        repeat(more) {
            val c = byteAt(q++)
            if (c < 0 || c and 0xC0 != 0x80) return null
            number = (number shl 6) or (c and 0x3F).toLong()
        }
        val blockSize = when (sizeCode) {
            1 -> 192
            in 2..5 -> 576 shl (sizeCode - 2)
            6 -> byteAt(q++).takeIf { it >= 0 }?.plus(1) ?: return null
            7 -> {
                val hi = byteAt(q++)
                val lo = byteAt(q++)
                if (hi < 0 || lo < 0) return null
                ((hi shl 8) or lo) + 1
            }
            else -> 256 shl (sizeCode - 8)
        }
        when (rateCode) {
            12 -> q += 1
            13, 14 -> q += 2
        }
        val crc = byteAt(q)
        if (crc < 0) return null
        var c = 0
        for (i in p until q) c = CRC8[c xor byteAt(i)]
        if (c != crc) return null
        val sample = if (variableBlocks) number else number * info.maxBlockSize
        return Header(p, sample, blockSize, (q + 1 - p).toInt())
    }

    private fun byteAt(p: Long): Int {
        val i = p - winStart
        if (i >= 0 && i < winLen) return window[i.toInt()].toInt() and 0xFF
        return if (ensure(p, 1)) window[(p - winStart).toInt()].toInt() and 0xFF else -1
    }

    /** Makes the window hold [length] bytes from [pos]; false when the data ends first. */
    private fun ensure(pos: Long, length: Int): Boolean {
        if (pos >= winStart && pos + length <= winStart + winLen) return true
        if (pos < winStart || pos > winStart + winLen) {
            winStart = pos
            winLen = 0
            ended = false
        } else {
            // Reading on: drop what comes before the frame in hand and keep reading forward.
            val drop = (min(anchor, pos) - winStart).coerceIn(0L, winLen.toLong()).toInt()
            if (drop > 0) {
                System.arraycopy(window, drop, window, 0, winLen - drop)
                winStart += drop
                winLen -= drop
            }
        }
        val need = (pos + length - winStart).toInt()
        if (need > window.size) window = window.copyOf(need + (1 shl 16))
        while (winLen < need && !ended) {
            val n = src.read(winStart + winLen, window, winLen, min(window.size - winLen, max(need - winLen, 1 shl 16)))
            if (n < 0) ended = true else winLen += n
        }
        return winLen >= need
    }

    companion object {
        private val CRC8 = IntArray(256) { i ->
            var c = i
            repeat(8) { c = if (c and 0x80 != 0) ((c shl 1) xor 0x07) and 0xFF else (c shl 1) and 0xFF }
            c
        }

        /**
         * Opens [path] as FLAC frames when it is a FLAC file deeper than 16-bit; null otherwise (and
         * for anything it can't read), so the song takes Android's usual path, which is bit-exact for
         * 16-bit. Blocking: IO thread.
         */
        fun open(context: Context, path: String): FlacFrames? {
            val src = runCatching { ByteSource.open(context, path) }.getOrNull() ?: return null
            return try {
                parse(src).also { if (it == null) src.close() }
            } catch (_: Exception) {
                runCatching { src.close() }
                null
            }
        }

        private fun parse(src: ByteSource): FlacFrames? {
            var pos = 0L
            val head = src.readFully(0, 10) ?: return null
            if (String(head, 0, 3, Charsets.US_ASCII) == "ID3") {
                // An ID3v2 tag some taggers put in front: a syncsafe size, plus a footer when flagged.
                val size = (head[6].toInt() and 0x7F shl 21) or (head[7].toInt() and 0x7F shl 14) or
                    (head[8].toInt() and 0x7F shl 7) or (head[9].toInt() and 0x7F)
                pos = 10L + size + if (head[5].toInt() and 0x10 != 0) 10 else 0
            }
            if (src.readFully(pos, 4)?.toString(Charsets.US_ASCII) != "fLaC") return null
            pos += 4
            var info: StreamInfo? = null
            val samples = ArrayList<Long>()
            val offsets = ArrayList<Long>()
            while (true) {
                val block = src.readFully(pos, 4) ?: return null
                val type = block[0].toInt() and 0x7F
                val length = ((block[1].toInt() and 0xFF) shl 16) or ((block[2].toInt() and 0xFF) shl 8) or (block[3].toInt() and 0xFF)
                pos += 4
                if (type == 0) {
                    val b = src.readFully(pos, 34) ?: return null
                    fun u(i: Int) = b[i].toInt() and 0xFF
                    info = StreamInfo(
                        minBlockSize = (u(0) shl 8) or u(1),
                        maxBlockSize = (u(2) shl 8) or u(3),
                        maxFrameSize = (u(7) shl 16) or (u(8) shl 8) or u(9),
                        sampleRate = (u(10) shl 12) or (u(11) shl 4) or (u(12) shr 4),
                        channels = ((u(12) shr 1) and 7) + 1,
                        bitsPerSample = (((u(12) and 1) shl 4) or (u(13) shr 4)) + 1,
                        totalSamples = ((u(13) and 0xF).toLong() shl 32) or (((u(14) shl 24) or (u(15) shl 16) or (u(16) shl 8) or u(17)).toLong() and 0xFFFFFFFFL),
                        raw = b
                    )
                    // 16-bit and below: Android's extractor already plays these exactly.
                    if (info.bitsPerSample <= 16 || info.sampleRate <= 0 || info.maxBlockSize <= 0) return null
                } else if (type == 3) {
                    val t = ByteBuffer.wrap(src.readFully(pos, length) ?: return null)
                    while (t.remaining() >= 18) {
                        val sample = t.long
                        val offset = t.long
                        t.short
                        if (sample != -1L) {
                            samples += sample
                            offsets += offset
                        }
                    }
                }
                pos += length
                if (block[0].toInt() and 0x80 != 0) break
            }
            val streamInfo = info ?: return null
            val frames = FlacFrames(src, streamInfo, pos, samples.toLongArray(), offsets.toLongArray())
            // The first frame says whether block sizes vary, then must check out like any other.
            frames.variableBlocks = (src.readFully(pos, 2) ?: return null)[1].toInt() and 1 == 1
            frames.anchor = pos
            frames.cur = frames.headerAt(pos) ?: return null
            return frames
        }
    }
}

/** Where a decoder's input comes from: Android's extractor, or [FlacFrames]. */
internal interface EncodedInput {
    /** Copies the next frame into [buffer] from [offset]; its size, or -1 at the end. */
    fun readSampleData(buffer: ByteBuffer, offset: Int): Int
    /** The song time of that frame in µs. */
    val sampleTime: Long
    fun advance(): Boolean
    /** Moves to the frame at or before [timeUs]. */
    fun seekTo(timeUs: Long)
    fun release()
}

internal class ExtractorInput(private val extractor: android.media.MediaExtractor) : EncodedInput {
    override fun readSampleData(buffer: ByteBuffer, offset: Int) = extractor.readSampleData(buffer, offset)
    override val sampleTime: Long get() = extractor.sampleTime
    override fun advance() = extractor.advance()
    override fun seekTo(timeUs: Long) = extractor.seekTo(timeUs, android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC)
    override fun release() {
        runCatching { extractor.release() }
    }
}

/** A song's bytes by position: a file, a `content://` document or an HTTP stream. */
internal interface ByteSource : Closeable {
    /** Bytes in all, or -1 when not known. */
    val size: Long
    /** Reads up to [length] bytes at [position]; -1 at the end. */
    fun read(position: Long, dst: ByteArray, offset: Int, length: Int): Int

    fun readFully(position: Long, length: Int): ByteArray? {
        val out = ByteArray(length)
        var got = 0
        while (got < length) {
            val n = read(position + got, out, got, length - got)
            if (n < 0) return null
            got += n
        }
        return out
    }

    companion object {
        fun open(context: Context, path: String): ByteSource = when {
            path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true) -> HttpByteSource(path)
            isDocumentPath(path) -> {
                val fd = context.contentResolver.openFileDescriptor(Uri.parse(path), "r") ?: throw IOException("No document")
                ChannelByteSource(FileInputStream(fd.fileDescriptor).channel, fd)
            }
            else -> ChannelByteSource(RandomAccessFile(path, "r").channel, null)
        }
    }
}

private class ChannelByteSource(private val channel: FileChannel, private val fd: ParcelFileDescriptor?) : ByteSource {
    override val size: Long = channel.size()
    override fun read(position: Long, dst: ByteArray, offset: Int, length: Int): Int =
        if (length == 0) 0 else channel.read(ByteBuffer.wrap(dst, offset, length), position)

    override fun close() {
        runCatching { channel.close() }
        runCatching { fd?.close() }
    }
}

/**
 * An HTTP stream read ahead on its own thread, a few MB at most, so a slow moment on the network
 * doesn't reach the speaker. Reading elsewhere than straight on (a seek) starts a new request there,
 * with a Range header; a server that ignores it is read from the start and skipped forward.
 */
private class HttpByteSource(private val url: String) : ByteSource {
    private val lock = Object()
    private val ring = ByteArray(4 shl 20)
    /** Stream position of the first byte held, where it sits in [ring], and how many are held. */
    private var start = 0L
    private var head = 0
    private var held = 0
    private var atEnd = false
    private var failure: IOException? = null
    private var generation = 0
    private var closed = false
    @Volatile
    override var size = -1L
        private set
    private var connection: HttpURLConnection? = null

    init {
        Thread(::fetch, "Ondalune HTTP read-ahead").apply { isDaemon = true }.start()
    }

    override fun read(position: Long, dst: ByteArray, offset: Int, length: Int): Int {
        synchronized(lock) {
            if (position < start || position > start + held) {
                // Not straight on: fetch from there instead.
                start = position
                head = 0
                held = 0
                atEnd = false
                failure = null
                generation++
                runCatching { connection?.disconnect() }
                lock.notifyAll()
            }
            val skip = (position - start).toInt()
            head = (head + skip) % ring.size
            held -= skip
            start = position
            val deadline = System.nanoTime() + 30_000_000_000L
            while (held == 0) {
                if (atEnd) return -1
                failure?.let { throw it }
                if (closed) throw IOException("Closed")
                val left = (deadline - System.nanoTime()) / 1_000_000L
                if (left <= 0) throw IOException("Stream stalled")
                lock.wait(left)
            }
            val n = min(length, min(held, ring.size - head))
            System.arraycopy(ring, head, dst, offset, n)
            head = (head + n) % ring.size
            held -= n
            start += n
            lock.notifyAll()
            return n
        }
    }

    private fun fetch() {
        val chunk = ByteArray(64 shl 10)
        var tries = 0
        while (true) {
            val (gen, from) = synchronized(lock) {
                while (!closed && (atEnd || failure != null)) lock.wait()
                if (closed) return
                generation to (start + held)
            }
            var input: InputStream? = null
            try {
                val c = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    if (from > 0) setRequestProperty("Range", "bytes=$from-")
                }
                synchronized(lock) { connection = c }
                val code = c.responseCode
                if (code !in 200..299) throw IOException("HTTP $code")
                input = c.inputStream
                val total = c.getHeaderField("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                    ?: c.contentLengthLong.takeIf { it >= 0 && code == 200 }
                if (total != null) size = total
                if (from > 0 && code == 200) {
                    // The server sent the whole file: skip to where reading stands.
                    var left = from
                    while (left > 0) {
                        val s = input.skip(left)
                        if (s <= 0) { if (input.read() < 0) throw IOException("Short stream") else left-- } else left -= s
                    }
                }
                while (true) {
                    val room = synchronized(lock) {
                        while (!closed && gen == generation && held == ring.size) lock.wait()
                        if (closed || gen != generation) -1 else ring.size - held
                    }
                    if (room < 0) break
                    val n = input.read(chunk, 0, min(room, chunk.size))
                    synchronized(lock) {
                        if (closed || gen != generation) return@synchronized
                        if (n < 0) atEnd = true else {
                            var w = (head + held) % ring.size
                            var left = n
                            var from2 = 0
                            while (left > 0) {
                                val k = min(left, ring.size - w)
                                System.arraycopy(chunk, from2, ring, w, k)
                                w = (w + k) % ring.size
                                from2 += k
                                left -= k
                            }
                            held += n
                        }
                        lock.notifyAll()
                    }
                    if (n < 0) break
                    tries = 0
                }
            } catch (e: IOException) {
                synchronized(lock) {
                    if (!closed && gen == generation && ++tries >= 3) {
                        failure = e
                        lock.notifyAll()
                    }
                }
                if (tries < 3) runCatching { Thread.sleep(300L * tries) }
            } finally {
                runCatching { input?.close() }
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            lock.notifyAll()
            runCatching { connection?.disconnect() }
        }
    }
}
