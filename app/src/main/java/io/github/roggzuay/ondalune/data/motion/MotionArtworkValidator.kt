package io.github.roggzuay.ondalune.data.motion

import java.io.File

/**
 * Tells moving artwork from everything else by its first bytes, whatever its name or the server
 * says: an MP4 or WebM video, an animated WebP, or a GIF with more than one frame. A still WebP or
 * GIF is not motion artwork.
 */
object MotionArtworkValidator {
    /** Enough of a file to tell its format and whether it moves. */
    const val HEADER_BYTES = 4096
    /** Larger files are not artwork. */
    const val MAX_BYTES = 60L * 1024 * 1024

    private val stillBrands = setOf("heic", "heix", "heim", "heis", "hevc", "mif1", "msf1", "avif", "avis")

    fun sniff(head: ByteArray): MotionFormat? = when {
        // HEIF and AVIF pictures share MP4's box format; their brand tells them apart.
        head.size >= 12 && ascii(head, 4, 4) == "ftyp" -> MotionFormat.MP4.takeUnless { ascii(head, 8, 4) in stillBrands }
        head.size >= 4 && head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() && head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte() ->
            MotionFormat.WEBM.takeIf { ascii(head, 0, head.size).contains("webm") }
        head.size >= 21 && ascii(head, 0, 4) == "RIFF" && ascii(head, 8, 4) == "WEBP" ->
            // Only the extended header can say "animated": bit 1 of its flags.
            MotionFormat.ANIMATED_WEBP.takeIf { ascii(head, 12, 4) == "VP8X" && head[20].toInt() and 0x02 != 0 }
        head.size >= 6 && (ascii(head, 0, 6) == "GIF89a" || ascii(head, 0, 6) == "GIF87a") ->
            MotionFormat.GIF.takeIf { gifFrames(head) >= 2 || ascii(head, 0, head.size).contains("NETSCAPE2.0") }
        else -> null
    }

    /** A downloaded copy is kept only when it is whole, not too large, and still the format offered. */
    fun check(file: File, expected: MotionFormat): Boolean {
        if (!file.isFile || file.length() < 32 || file.length() > MAX_BYTES) return false
        val head = ByteArray(HEADER_BYTES)
        val read = file.inputStream().use { it.read(head) }
        return read > 0 && sniff(head.copyOf(read)) == expected
    }

    private fun ascii(b: ByteArray, from: Int, length: Int): String {
        val end = minOf(b.size, from + length)
        return if (from >= end) "" else String(b, from, end - from, Charsets.ISO_8859_1)
    }

    /** Frames in the part of a GIF read so far. */
    private fun gifFrames(b: ByteArray): Int {
        if (b.size < 13) return 0
        var i = 13
        val flags = b[10].toInt() and 0xFF
        if (flags and 0x80 != 0) i += 3 * (1 shl ((flags and 0x07) + 1))
        var frames = 0
        while (i < b.size) {
            when (b[i].toInt() and 0xFF) {
                0x21 -> { // Extension: label, then sub-blocks.
                    i += 2
                    i = skipBlocks(b, i) ?: return frames
                }
                0x2C -> { // Image: descriptor, colour table, LZW size, data sub-blocks.
                    frames++
                    if (i + 10 > b.size) return frames
                    val f = b[i + 9].toInt() and 0xFF
                    i += 10
                    if (f and 0x80 != 0) i += 3 * (1 shl ((f and 0x07) + 1))
                    i += 1
                    i = skipBlocks(b, i) ?: return frames
                }
                else -> return frames
            }
        }
        return frames
    }

    private fun skipBlocks(b: ByteArray, start: Int): Int? {
        var i = start
        while (i < b.size) {
            val len = b[i].toInt() and 0xFF
            i += 1
            if (len == 0) return i
            i += len
        }
        return null
    }
}
