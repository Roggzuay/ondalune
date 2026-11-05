package io.github.roggzuay.ondalune.data.model

/**
 * The player's quality line and its details. Built only from what is known: the song's own details
 * from its source, what Ondalune actually opened and decoded, the way it goes out, and the
 * processing switched on. Nothing is filled in when a source doesn't say.
 */
data class AudioQuality(
    /** The compact line under the cover: "FLAC · 24-bit / 96 kHz", "MP3 · 320 kbps". */
    val label: String,
    /** "Lossless", "Lossless source", "Converted" or null. */
    val tag: String?,
    val source: List<Pair<String, String>>,
    val stream: List<Pair<String, String>>,
    val output: List<Pair<String, String>>,
    /** What changes the sound right now; empty when nothing does. */
    val processing: List<String>
)

/** What the engine opened, in plain values (see the engine's DecodedStream). */
data class OpenedStream(
    val mime: String,
    val sampleRate: Int,
    val channels: Int,
    val bitrate: Int,
    val bitsPerSample: Int,
    /** Ondalune's own 16-bit stereo pipeline (true) or Android's MediaPlayer (false). */
    val ownPipeline: Boolean,
    val outputSampleRate: Int
)

private val losslessSuffixes = setOf("FLAC", "ALAC", "WAV", "AIFF", "AIF", "APE", "WV", "DSF", "DFF")

/** The decoder types a file with this extension can hold; null when the extension says too little. */
private fun mimesFor(suffix: String): Set<String>? = when (suffix) {
    "FLAC" -> setOf("audio/flac")
    "MP3" -> setOf("audio/mpeg")
    "M4A", "AAC", "MP4" -> setOf("audio/mp4a-latm", "audio/alac")
    "ALAC" -> setOf("audio/alac")
    "OGG", "OGA" -> setOf("audio/vorbis", "audio/opus", "audio/flac")
    "OPUS" -> setOf("audio/opus")
    "WAV" -> setOf("audio/raw", "audio/wav", "audio/x-wav")
    else -> null
}

/** "audio/mp4a-latm" → "AAC"; unknown types keep their own name. */
fun codecName(mime: String): String = when (mime.lowercase()) {
    "audio/flac" -> "FLAC"
    "audio/alac" -> "ALAC"
    "audio/mpeg" -> "MP3"
    "audio/mp4a-latm" -> "AAC"
    "audio/opus" -> "Opus"
    "audio/vorbis" -> "Vorbis"
    "audio/raw", "audio/wav", "audio/x-wav" -> "PCM"
    "audio/ac3" -> "Dolby Digital"
    "audio/eac3" -> "Dolby Digital Plus"
    "audio/eac3-joc" -> "Dolby Digital Plus with Atmos"
    "audio/ac4" -> "Dolby AC-4"
    else -> mime.substringAfter('/').uppercase()
}

private fun khz(rate: Int) = if (rate % 1000 == 0) "${rate / 1000} kHz" else "%.1f kHz".format(rate / 1000.0)

private fun channelName(n: Int) = when (n) {
    1 -> "mono"
    2 -> "stereo"
    6 -> "5.1"
    8 -> "7.1"
    else -> "$n channels"
}

/**
 * Builds the quality line for [track] as it plays. [opened] is what the engine decoded (null before
 * it has opened the song), [convertedBy] the server's conversion format when it streams a converted
 * copy (null when it sends the original or the song plays from a file), [processing] what is on.
 */
fun audioQualityOf(
    track: SubsonicTrack,
    opened: OpenedStream?,
    convertedBy: String?,
    processing: List<String>,
    outputDevice: String?
): AudioQuality? {
    val suffix = track.suffix.uppercase()
    val sourceLossless = suffix in losslessSuffixes || (suffix == "M4A" && track.bitDepth > 0)
    val streamCodec = opened?.mime?.takeIf { it.isNotBlank() }?.let(::codecName)
    // The decoder's word beats the server's: a codec the file can't hold means a converted copy.
    val expected = mimesFor(suffix)
    val converted = convertedBy != null || (opened != null && expected != null && opened.mime.isNotBlank() && opened.mime.lowercase() !in expected)

    fun sourceLine(): String? {
        if (suffix.isBlank()) return null
        val detail = when {
            sourceLossless && track.bitDepth > 0 && track.samplingRate > 0 -> "${track.bitDepth}-bit / ${khz(track.samplingRate)}"
            sourceLossless && track.samplingRate > 0 -> khz(track.samplingRate)
            track.bitRate > 0 -> "${track.bitRate} kbps"
            else -> null
        }
        return listOfNotNull(suffix, detail).joinToString(" · ")
    }

    val label = if (converted) {
        listOfNotNull(streamCodec ?: convertedBy, opened?.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} kbps" }).joinToString(" · ")
    } else sourceLine() ?: streamCodec?.let { codec ->
        listOfNotNull(codec, opened.sampleRate.takeIf { it > 0 }?.let(::khz)).joinToString(" · ")
    } ?: return null

    val outputBits = if (opened?.ownPipeline == true) 16 else 0
    val reduced = sourceLossless && track.bitDepth > outputBits && outputBits > 0
    val tag = when {
        converted -> "Converted"
        sourceLossless && processing.isEmpty() && !reduced -> "Lossless"
        sourceLossless -> "Lossless source"
        else -> null
    }

    val source = buildList {
        add("Format" to suffix.ifBlank { "Not reported" })
        if (track.bitDepth > 0) add("Bit depth" to "${track.bitDepth}-bit")
        if (track.samplingRate > 0) add("Sample rate" to khz(track.samplingRate))
        if (track.bitRate > 0) add("Bitrate" to "${track.bitRate} kbps")
        add("Kind" to if (sourceLossless) "Lossless" else if (suffix.isBlank()) "Not reported" else "Lossy")
    }
    val stream = if (opened == null) listOf("Playing" to "Not opened yet") else buildList {
        add("Codec" to (streamCodec ?: "Not reported"))
        if (opened.sampleRate > 0) add("Sample rate" to khz(opened.sampleRate))
        if (opened.channels > 0) add("Channels" to channelName(opened.channels))
        if (opened.bitsPerSample > 0) add("Bit depth" to "${opened.bitsPerSample}-bit")
        if (opened.bitrate > 0) add("Bitrate" to "${opened.bitrate / 1000} kbps")
        if (converted) add("Conversion" to "The server sends a converted copy" + (convertedBy?.let { " ($it)" } ?: ""))
    }
    val output = buildList {
        if (opened != null) {
            if (opened.ownPipeline) {
                add("Path" to "Ondalune's audio engine")
                add("Format" to listOfNotNull("16-bit PCM", opened.outputSampleRate.takeIf { it > 0 }?.let(::khz), "stereo").joinToString(" · "))
                if (reduced) add("Note" to "This ${track.bitDepth}-bit song plays as 16-bit")
                if (opened.channels > 2) add("Note" to "${channelName(opened.channels)} audio plays as stereo")
            } else add("Path" to "Android's media player")
        }
        outputDevice?.let { add("Device" to it) }
    }
    return AudioQuality(label, tag, source, stream, output, processing)
}

/** The short badge on song rows: "FLAC 24/96k", "MP3 320k", or just the format when that's all that's known. */
fun SubsonicTrack.compactQuality(): String {
    val suffix = suffix.uppercase()
    return when {
        bitDepth > 0 && samplingRate > 0 -> "$suffix $bitDepth/${samplingRate / 1000}k"
        bitRate > 0 -> "$suffix ${bitRate}k"
        else -> suffix
    }
}
