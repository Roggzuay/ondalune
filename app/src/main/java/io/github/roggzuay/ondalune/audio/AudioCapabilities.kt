package io.github.roggzuay.ondalune.audio

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodecList
import android.os.Build
import io.github.roggzuay.ondalune.data.model.AudioOutput
import io.github.roggzuay.ondalune.data.model.OutputKind

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

    /** The Dolby stream types this phone can decode to PCM, including Atmos streams played as their 5.1 core. */
    val dolbyDecodable: Set<String> by lazy { DolbyMimes.filter { dolbyDecodersFor(it).isNotEmpty() }.toSet() }

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

    /**
     * The output [device] as Android reports it: "Phone speaker", "Bluetooth · Pixel Buds", "USB · DAC
     * name", with the rate Android's mixer now sends to wherever music plays. Android Auto shows as
     * the car while the phone is in car mode.
     */
    fun output(device: AudioDeviceInfo?): AudioOutput? {
        device ?: return null
        val name = device.productName?.toString()?.trim().orEmpty()
        val rate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC).coerceAtLeast(0)
        val car = context.getSystemService(UiModeManager::class.java)?.currentModeType == Configuration.UI_MODE_TYPE_CAR
        if (car) return AudioOutput("Android Auto", OutputKind.CAR, rate)
        val kind = when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> return AudioOutput("Phone speaker", OutputKind.PHONE, rate)
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> OutputKind.WIRED
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> OutputKind.BLUETOOTH
            AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_BROADCAST -> OutputKind.BLUETOOTH_LE
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> OutputKind.USB
            AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC -> OutputKind.HDMI
            AudioDeviceInfo.TYPE_BUS -> OutputKind.CAR
            else -> OutputKind.OTHER
        }
        val label = when (kind) {
            OutputKind.WIRED -> "Wired headphones"
            OutputKind.BLUETOOTH -> "Bluetooth"
            OutputKind.BLUETOOTH_LE -> "Bluetooth LE"
            OutputKind.USB -> "USB"
            OutputKind.HDMI -> "HDMI"
            OutputKind.CAR -> "Car audio"
            else -> "Output"
        }
        return AudioOutput(if (name.isNotBlank() && name != Build.MODEL) "$label · $name" else label, kind, rate)
    }
}
