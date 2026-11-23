package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.data.motion.FolderMotionProvider
import io.github.roggzuay.ondalune.data.motion.MotionArtworkValidator
import io.github.roggzuay.ondalune.data.motion.MotionFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MotionArtworkValidatorTest {
    @get:Rule val tmp = TemporaryFolder()

    companion object {
        fun bytes(vararg parts: Any): ByteArray = parts.flatMap { p ->
            when (p) {
                is String -> p.toByteArray(Charsets.ISO_8859_1).toList()
                is Int -> listOf(p.toByte())
                is ByteArray -> p.toList()
                else -> error(p)
            }
        }.toByteArray()

        fun mp4() = bytes(0, 0, 0, 0x20, "ftypisom", 0, 0, 2, 0, "isomiso2avc1mp41", ByteArray(64))
        fun webm() = bytes(0x1A, 0x45, 0xDF, 0xA3, 0x9F, 0x42, 0x86, 0x81, 0x01, 0x42, 0x82, 0x84, "webm", ByteArray(32))

        /** A WebP with the extended header; [animated] sets the animation flag. */
        fun webp(animated: Boolean) = bytes("RIFF", 0, 0, 0, 0, "WEBP", "VP8X", 10, 0, 0, 0, if (animated) 0x12 else 0x10, 0, 0, 0, ByteArray(40))

        /** A GIF without a colour table and [frames] one-pixel frames. */
        fun gif(frames: Int, loop: Boolean = false): ByteArray {
            val header = bytes("GIF89a", 1, 0, 1, 0, 0, 0, 0)
            val netscape = if (loop) bytes(0x21, 0xFF, 11, "NETSCAPE2.0", 3, 1, 0, 0, 0) else ByteArray(0)
            val frame = bytes(0x21, 0xF9, 4, 0, 10, 0, 0, 0, 0x2C, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 0x44, 0x01, 0)
            return header + netscape + (0 until frames).fold(ByteArray(0)) { acc, _ -> acc + frame } + bytes(0x3B)
        }
    }

    @Test
    fun `videos are told apart by their headers`() {
        assertEquals(MotionFormat.MP4, MotionArtworkValidator.sniff(mp4()))
        assertEquals(MotionFormat.WEBM, MotionArtworkValidator.sniff(webm()))
    }

    @Test
    fun `HEIF and AVIF pictures are not mistaken for MP4 video`() {
        assertNull(MotionArtworkValidator.sniff(bytes(0, 0, 0, 0x18, "ftypheic", ByteArray(32))))
        assertNull(MotionArtworkValidator.sniff(bytes(0, 0, 0, 0x18, "ftypavif", ByteArray(32))))
    }

    @Test
    fun `only an animated WebP counts`() {
        assertEquals(MotionFormat.ANIMATED_WEBP, MotionArtworkValidator.sniff(webp(animated = true)))
        assertNull(MotionArtworkValidator.sniff(webp(animated = false)))
        assertNull(MotionArtworkValidator.sniff(bytes("RIFF", 0, 0, 0, 0, "WEBP", "VP8 ", ByteArray(40))))
    }

    @Test
    fun `only a GIF with several frames or a loop counts`() {
        assertEquals(MotionFormat.GIF, MotionArtworkValidator.sniff(gif(3)))
        assertEquals(MotionFormat.GIF, MotionArtworkValidator.sniff(gif(1, loop = true)))
        assertNull(MotionArtworkValidator.sniff(gif(1)))
    }

    @Test
    fun `still pictures and junk are not motion artwork`() {
        assertNull(MotionArtworkValidator.sniff(bytes(0xFF, 0xD8, 0xFF, 0xE0, ByteArray(40))))
        assertNull(MotionArtworkValidator.sniff(bytes(0x89, "PNG", ByteArray(40))))
        assertNull(MotionArtworkValidator.sniff(ByteArray(0)))
        assertNull(MotionArtworkValidator.sniff(bytes(0x1A, 0x45, 0xDF, 0xA3, "matroska", ByteArray(8))))
    }

    @Test
    fun `a downloaded file is kept only when whole and still the format offered`() {
        val good = tmp.newFile("a.mp4").apply { writeBytes(mp4()) }
        assertTrue(MotionArtworkValidator.check(good, MotionFormat.MP4))
        assertFalse(MotionArtworkValidator.check(good, MotionFormat.GIF))
        val cut = tmp.newFile("b.mp4").apply { writeBytes(mp4().copyOf(10)) }
        assertFalse(MotionArtworkValidator.check(cut, MotionFormat.MP4))
        assertFalse(MotionArtworkValidator.check(tmp.root.resolve("missing.mp4"), MotionFormat.MP4))
    }

    @Test
    fun `music folders offer motion-named files before plain covers, videos first`() {
        val names = listOf("cover.gif", "folder.jpg", "motion.mp4", "cover.mp4", "track01.flac", "animated.webp", "back.mp4")
        val ranked = names.filter { FolderMotionProvider.rank(it) != null }.sortedBy { FolderMotionProvider.rank(it) }
        assertEquals(listOf("motion.mp4", "animated.webp", "cover.mp4", "cover.gif"), ranked)
        assertEquals(MotionFormat.WEBM, FolderMotionProvider.formatOf("Cover.WEBM"))
        assertNull(FolderMotionProvider.formatOf("cover.png"))
    }
}
