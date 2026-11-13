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
    /** Ondalune's own 32-bit float stereo pipeline (true) or Android's MediaPlayer (false). */
    val ownPipeline: Boolean,
    val outputSampleRate: Int,
    /** The untouched Dolby stream goes to the phone's own Dolby engine; nothing of Ondalune's applies. */
    val dolbyEngine: Boolean = false,
    /** Bits per sample the decoder gives the engine (32 for float); 0 when not known. */
    val decodedBits: Int = 0
)

private val losslessSuffixes = setOf("FLAC", "ALAC", "WAV", "AIFF", "AIF", "APE", "WV", "DSF", "DFF")

/** The Dolby stream types, by the names Android uses for them. */
private val dolbyMimes = setOf("audio/ac3", "audio/eac3", "audio/eac3-joc", "audio/ac4")

fun isDolby(mime: String) = mime.lowercase() in dolbyMimes

/**
 * The Dolby stream type of this song as far as the server says: from its codec when the server
 * names one, else from a file extension that can only hold Dolby. Null for anything else, including
 * an M4A that may or may not hold Dolby (only opening it tells).
 */
fun SubsonicTrack.dolbyMime(): String? = when (codec.lowercase()) {
    "eac3-joc", "ec3-joc", "eac3_joc" -> "audio/eac3-joc"
    "eac3", "ec3", "ec-3", "e-ac-3" -> "audio/eac3"
    "ac3", "ac-3" -> "audio/ac3"
    "ac4", "ac-4" -> "audio/ac4"
    else -> when (suffix.uppercase()) {
        "EC3", "EAC3" -> "audio/eac3"
        "AC3" -> "audio/ac3"
        "AC4" -> "audio/ac4"
        else -> null
    }
}

/**
 * Whether this song may hold Dolby audio, so it should stream as the original file: a known Dolby
 * stream, or a multichannel MP4/Matroska file, which is where Dolby usually travels. Converting
 * such a file to Opus or MP3 would throw the Dolby away.
 */
fun SubsonicTrack.mayHoldDolby(): Boolean = dolbyMime() != null ||
    (codec.isBlank() && channelCount > 2 && suffix.uppercase() in setOf("M4A", "MP4", "MKA", "MKV"))

/**
 * The codec named by a server's content type when it is a Dolby one ("audio/eac3" → "eac3"), for
 * servers that report no codec of their own; blank otherwise.
 */
fun dolbyCodecOfContentType(contentType: String): String = when (contentType.lowercase().substringBefore(';').trim()) {
    "audio/eac3-joc" -> "eac3-joc"
    "audio/eac3", "audio/e-ac-3", "audio/x-eac3", "audio/ec3" -> "eac3"
    "audio/ac3", "audio/x-ac3", "audio/vnd.dolby.dd-raw" -> "ac3"
    "audio/ac4", "audio/x-ac4" -> "ac4"
    else -> ""
}

/**
 * How a Dolby song ([dolbyMime]) plays on this phone, in a few words; null for any other song, or
 * when it isn't known yet. [phoneDecodes] are the Dolby types this phone decodes, null if unknown.
 */
fun dolbyPlaybackNote(dolbyMime: String?, opened: OpenedStream?, phoneDecodes: Set<String>?): String? = when {
    dolbyMime == null -> null
    opened?.dolbyEngine == true -> "Played by this phone's Dolby engine: EQ and effects don't apply"
    opened != null && opened.ownPipeline -> "Decoded by this phone's Dolby decoder, mixed down to stereo"
    opened == null && phoneDecodes != null && dolbyMime !in phoneDecodes -> "This phone has no ${codecName(dolbyMime)} decoder"
    else -> null
}

/** The decoder types a file with this extension can hold; null when the extension says too little. */
private fun mimesFor(suffix: String): Set<String>? = when (suffix) {
    // Android's FLAC extractor decodes as it reads, so a FLAC file opens as raw PCM.
    "FLAC" -> setOf("audio/flac", "audio/raw")
    "MP3" -> setOf("audio/mpeg")
    // MP4 also carries Dolby Digital Plus (with or without Atmos) and AC-4.
    "M4A", "AAC", "MP4" -> setOf("audio/mp4a-latm", "audio/alac") + dolbyMimes
    "ALAC" -> setOf("audio/alac")
    "OGG", "OGA" -> setOf("audio/vorbis", "audio/opus", "audio/flac")
    "OPUS" -> setOf("audio/opus")
    "WAV" -> setOf("audio/raw", "audio/wav", "audio/x-wav")
    "AC3" -> setOf("audio/ac3")
    "EC3", "EAC3" -> setOf("audio/eac3", "audio/eac3-joc")
    "AC4" -> setOf("audio/ac4")
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
 * [phoneDecodes] are the Dolby types this phone has decoders for (null when not known), so a Dolby
 * song that hasn't opened can say why it won't.
 */
fun audioQualityOf(
    track: SubsonicTrack,
    opened: OpenedStream?,
    convertedBy: String?,
    processing: List<String>,
    outputDevice: String?,
    phoneDecodes: Set<String>? = null
): AudioQuality? {
    val suffix = track.suffix.uppercase()
    val sourceLossless = suffix in losslessSuffixes || (suffix == "M4A" && track.bitDepth > 0)
    val openedMime = opened?.mime?.lowercase()?.takeIf { it.isNotBlank() }
    // The Dolby type in play: what the decoder saw, else what the server said.
    val dolbyMime = openedMime?.takeIf(::isDolby) ?: track.dolbyMime()?.takeIf { openedMime == null }
    val streamCodec = openedMime?.let { mime ->
        // Raw PCM straight from a lossless file is that file, decoded while it was read.
        if (mime == "audio/raw" && sourceLossless && suffix.isNotBlank()) suffix else codecName(mime)
    }
    // The decoder's word beats the server's: a codec the file can't hold means a converted copy.
    val expected = mimesFor(suffix)
    val converted = convertedBy != null || (openedMime != null && expected != null && openedMime !in expected)
    val channels = opened?.channels?.takeIf { it > 0 } ?: track.channelCount.takeIf { it > 0 }

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

    val label = when {
        converted -> listOfNotNull(streamCodec ?: convertedBy, opened?.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} kbps" }).joinToString(" · ")
        // A Dolby song is named by its stream, not its container: "Dolby Digital Plus · 5.1 · 640 kbps".
        dolbyMime != null -> listOfNotNull(
            codecName(dolbyMime),
            channels?.let(::channelName),
            (opened?.bitrate?.takeIf { it > 0 }?.div(1000) ?: track.bitRate.takeIf { it > 0 })?.let { "$it kbps" }
        ).joinToString(" · ")
        else -> sourceLine() ?: streamCodec?.let { codec ->
            listOfNotNull(codec, opened.sampleRate.takeIf { it > 0 }?.let(::khz)).joinToString(" · ")
        } ?: return null
    }

    // The engine carries float all the way, so only a decoder that gives fewer bits can reduce a song.
    val decodedBits = if (opened?.ownPipeline == true && !converted) opened.decodedBits else 0
    val reduced = sourceLossless && decodedBits > 0 && track.bitDepth > decodedBits
    val tag = when {
        converted -> "Converted"
        sourceLossless && processing.isEmpty() && !reduced -> "Lossless"
        sourceLossless -> "Lossless source"
        else -> null
    }

    val source = buildList {
        add("Format" to suffix.ifBlank { "Not reported" })
        track.dolbyMime()?.let { add("Codec" to codecName(it)) }
        if (track.bitDepth > 0) add("Bit depth" to "${track.bitDepth}-bit")
        if (track.samplingRate > 0) add("Sample rate" to khz(track.samplingRate))
        if (track.channelCount > 0) add("Channels" to channelName(track.channelCount))
        if (track.bitRate > 0) add("Bitrate" to "${track.bitRate} kbps")
        add("Kind" to if (sourceLossless) "Lossless" else if (suffix.isBlank()) "Not reported" else "Lossy")
    }
    val dolbyNote = dolbyPlaybackNote(dolbyMime, opened, phoneDecodes)
    val stream = if (opened == null) {
        listOf("Playing" to (dolbyNote ?: "Not opened yet"))
    } else if (!opened.ownPipeline && !opened.dolbyEngine && opened.mime.isBlank()) {
        listOf("Playing" to "Android's media player decodes this song; it doesn't report the format")
    } else buildList {
        add("Codec" to (streamCodec ?: "Not reported"))
        if (opened.sampleRate > 0) add("Sample rate" to khz(opened.sampleRate))
        if (opened.channels > 0) add("Channels" to channelName(opened.channels))
        if (opened.bitsPerSample > 0) add("Bit depth" to "${opened.bitsPerSample}-bit")
        if (opened.bitrate > 0) add("Bitrate" to "${opened.bitrate / 1000} kbps")
        if (converted) add("Conversion" to "The server sends a converted copy" + (convertedBy?.let { " ($it)" } ?: ""))
        dolbyNote?.let { add("How it plays" to it) }
    }
    val output = buildList {
        if (opened != null) {
            when {
                opened.dolbyEngine -> {
                    add("Path" to "This phone's Dolby engine")
                    add("Format" to listOfNotNull(streamCodec, opened.channels.takeIf { it > 0 }?.let(::channelName)).joinToString(" · ") + ", untouched")
                    add("Note" to "EQ, reverb, speed, pitch, levelling, gapless and crossfade don't apply: the phone's Dolby engine plays this song")
                }
                opened.ownPipeline -> {
                    add("Path" to "Ondalune's audio engine")
                    add("Format" to listOfNotNull("32-bit float", opened.outputSampleRate.takeIf { it > 0 }?.let(::khz), "stereo").joinToString(" · "))
                    if (reduced) add("Note" to "This phone's decoder gives $decodedBits-bit for this ${track.bitDepth}-bit song")
                    if (converted && track.bitDepth > 0) add("Note" to "A converted copy plays, so the file's ${track.bitDepth}-bit depth doesn't reach the phone")
                    if (opened.channels > 2) add("Note" to "${channelName(opened.channels)} audio is mixed down to stereo (centre and surrounds folded in, LFE left out)")
                    // Past the app: Android mixes at the output's own rate, and Bluetooth re-encodes.
                    if (outputDevice?.startsWith("Bluetooth") == true) {
                        add("Note" to "Bluetooth re-encodes the audio for the headphones, which lowers its resolution")
                    } else if (opened.outputSampleRate > 48000) {
                        add("Note" to "Android may resample it to the output's own rate (often 48 kHz)")
                    }
                }
                else -> add("Path" to "Android's media player")
            }
        }
        outputDevice?.let { add("Device" to it) }
    }
    val processingShown = if (opened?.dolbyEngine == true) {
        listOf("Nothing from Ondalune: the phone's Dolby engine plays this song, so EQ, reverb, speed, pitch and levelling don't apply to it")
    } else processing
    return AudioQuality(label, tag, source, stream, output, processingShown)
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
