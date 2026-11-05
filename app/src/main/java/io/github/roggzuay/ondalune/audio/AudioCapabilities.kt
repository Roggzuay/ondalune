package io.github.roggzuay.ondalune.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaCodecList
import android.os.Build

/**
 * What this phone can do with audio, as Android reports it: its Dolby decoders, whether spatial
 * audio would apply to Ondalune's output, and a name for the output device. Only reported facts:
 * a phone sold with Dolby branding still shows nothing here unless its decoders are registered.
 */
class AudioCapabilities(private val context: Context) {
    /** The Dolby formats this phone can decode, by name; empty when it has no Dolby decoders. */
    val dolbyDecoders: List<String> by lazy {
        val types = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filterNot { it.isEncoder }
            .flatMap { it.supportedTypes.asList() }
            .map { it.lowercase() }
            .toSet()
        listOf(
            "audio/ac3" to "Dolby Digital (AC-3)",
            "audio/eac3" to "Dolby Digital Plus (E-AC-3)",
            "audio/eac3-joc" to "Dolby Digital Plus with Dolby Atmos (E-AC-3 JOC)",
            "audio/ac4" to "Dolby AC-4"
        ).filter { it.first in types }.map { it.second }
    }

    /**
     * Whether Android's spatial audio would process Ondalune's stereo output right now: true or
     * false when the phone has it, null when it doesn't (or Android is older than 12L).
     */
    fun spatialAudioActive(): Boolean? {
        if (Build.VERSION.SDK_INT < 32) return null
        val spatializer = context.getSystemService(AudioManager::class.java)?.spatializer ?: return null
        if (spatializer.immersiveAudioLevel == android.media.Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE) return null
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setSampleRate(48000).build()
        return spatializer.isAvailable && spatializer.isEnabled && spatializer.canBeSpatialized(attributes, format)
    }

    /** "Phone speaker", "Bluetooth · Pixel Buds", "USB · DAC name". */
    fun deviceName(device: AudioDeviceInfo?): String? {
        device ?: return null
        val name = device.productName?.toString()?.trim().orEmpty()
        val kind = when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> return "Phone speaker"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headphones"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
            AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_BROADCAST -> "Bluetooth LE"
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB"
            AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI"
            else -> "Output"
        }
        return if (name.isNotBlank() && name != Build.MODEL) "$kind · $name" else kind
    }
}
