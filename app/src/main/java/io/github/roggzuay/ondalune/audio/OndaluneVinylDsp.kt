package io.github.roggzuay.ondalune.audio

import io.github.roggzuay.ondalune.data.model.VinylMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Vinyl mode: what a record does to a song on its way to your ears.
 *
 * Four things, in the order a record does them:
 *  1. **Bandwidth.** A record cannot hold the deepest bass or the last of the top, so both ends
 *     are rolled off gently.
 *  2. **Wow and flutter.** The platter is never exactly on speed, and the hole is never exactly
 *     in the middle: a slow drift and a fast waver, together a fraction of a semitone.
 *  3. **Surface noise.** The hiss of the groove, shaped so it sits behind the music rather than
 *     over it, with crackle on top of it.
 *  4. **The needle drop.** A record starts with the needle landing: one pop, then the groove's
 *     noise settling in before the music.
 *
 * It is all deterministic from a seed, so the same record sounds the same twice and the tests can
 * check it. Off, [process] returns on its first line.
 */
class OndaluneVinylDsp(seed: Long = 0x5EEDL) {

    @Volatile
    var mode: VinylMode = VinylMode.OFF

    private var active = VinylMode.OFF
    private var rate = 0

    // ---- Bandwidth ----
    private var lpL = 0f
    private var lpR = 0f
    private var hpL = 0f
    private var hpR = 0f
    private var lpCoef = 0f
    private var hpCoef = 0f

    // ---- Wow and flutter ----
    private val delayL = FloatArray(WowLineFrames)
    private val delayR = FloatArray(WowLineFrames)
    private var write = 0
    private var wowPhase = 0f
    private var flutterPhase = 0f
    private var wowStep = 0f
    private var flutterStep = 0f
    private var centreDelay = 0f
    private var swingFrames = 0f

    // ---- Surface noise ----
    private var rng = seed
    private var noiseLp = 0f
    private var noiseHp = 0f
    private var crackle = 0f
    private var crackleDecay = 0f
    private var crackleChance = 0f

    // ---- The needle drop ----
    private var dropFrames = 0
    private var dropLeft = 0

    /**
     * The needle has been asked to land. It is a flag rather than a countdown because both
     * callers ask before the first buffer of a mode is processed — switching vinyl on, and
     * starting a song — and until then there is no sample rate, so there is no length to count.
     * Writing it from another thread is safe; acting on it is the audio thread's business.
     */
    @Volatile private var needleQueued = false

    /**
     * The needle lands: one pop, then the groove settling in. Called when a song starts, and when
     * vinyl is switched on mid-song. Both of those happen before the first buffer of that mode,
     * so this only asks; [process] does it.
     */
    fun dropNeedle() {
        if (mode == VinylMode.OFF) return
        needleQueued = true
    }

    /** Lifts the needle: clears the delay line and the noise so nothing carries into silence. */
    fun reset() {
        active = VinylMode.OFF
        rate = 0
        write = 0
        lpL = 0f; lpR = 0f; hpL = 0f; hpR = 0f
        wowPhase = 0f; flutterPhase = 0f
        noiseLp = 0f; noiseHp = 0f
        crackle = 0f
        dropLeft = 0
        needleQueued = false
        java.util.Arrays.fill(delayL, 0f)
        java.util.Arrays.fill(delayR, 0f)
    }

    /**
     * Puts a record under the music, in place. Interleaved stereo floats, as everything else in
     * this pipeline uses.
     */
    fun process(samples: FloatArray, frameCount: Int, sampleRate: Int) {
        val wanted = mode
        if (wanted == VinylMode.OFF) {
            if (active != VinylMode.OFF) reset()
            return
        }
        val frames = minOf(frameCount, samples.size / 2)
        if (frames <= 0 || sampleRate <= 0) return
        if (wanted != active || sampleRate != rate) retune(wanted, sampleRate)
        if (needleQueued) {
            needleQueued = false
            dropLeft = dropFrames
            crackle = NeedlePop
            rng = rng xor 0x9E3779B97F4A7C15UL.toLong()
        }

        for (i in 0 until frames) {
            var left = samples[i * 2]
            var right = samples[i * 2 + 1]

            // 1. Bandwidth: a record holds neither the deepest bass nor the last of the top.
            lpL += lpCoef * (left - lpL)
            lpR += lpCoef * (right - lpR)
            hpL += hpCoef * (lpL - hpL)
            hpR += hpCoef * (lpR - hpR)
            left = lpL - hpL
            right = lpR - hpR

            // 2. Wow and flutter: the platter drifts, the hole wavers.
            delayL[write] = left
            delayR[write] = right
            wowPhase += wowStep
            if (wowPhase >= TwoPi) wowPhase -= TwoPi
            flutterPhase += flutterStep
            if (flutterPhase >= TwoPi) flutterPhase -= TwoPi
            val swing = sin(wowPhase) * 0.72f + sin(flutterPhase) * 0.28f
            val delay = centreDelay + swing * swingFrames
            left = readDelayed(delayL, delay)
            right = readDelayed(delayR, delay)
            write++
            if (write >= WowLineFrames) write = 0

            // 3. Surface noise, shaped to sit behind the music, with crackle over it.
            val white = nextNoise()
            noiseLp += NoiseLpCoef * (white - noiseLp)
            noiseHp += NoiseHpCoef * (noiseLp - noiseHp)
            var surface = (noiseLp - noiseHp) * active.noiseLevel
            if (nextUnit() < crackleChance) crackle = (nextNoise() * CrackleHeight)
            crackle *= crackleDecay
            surface += crackle * active.crackleLevel

            // 4. The needle drop: the groove is loudest just after the needle lands.
            if (dropLeft > 0) {
                val into = dropLeft.toFloat() / dropFrames.coerceAtLeast(1)
                surface *= 1f + into * NeedleDropLift
                dropLeft--
            }

            samples[i * 2] = (left + surface).coerceIn(-1f, 1f)
            // The other side of the groove hears almost the same noise, not a different one.
            samples[i * 2 + 1] = (right + surface * 0.86f + nextNoise() * active.noiseLevel * 0.14f)
                .coerceIn(-1f, 1f)
        }
    }

    private fun retune(wanted: VinylMode, sampleRate: Int) {
        active = wanted
        rate = sampleRate
        lpCoef = onePole(wanted.topHz, sampleRate)
        hpCoef = onePole(wanted.bottomHz, sampleRate)
        wowStep = (TwoPi * WowHz / sampleRate).toFloat()
        flutterStep = (TwoPi * FlutterHz / sampleRate).toFloat()
        // The delay sits in the middle of the line so it can swing either way.
        swingFrames = (wanted.wowCents / 1200f) * sampleRate * WowSwingSeconds
        centreDelay = (swingFrames + 2f).coerceAtMost(WowLineFrames - 2f)
        crackleChance = wanted.cracklesPerSecond / sampleRate
        // A crackle dies away in about a millisecond and a half, whatever the rate.
        crackleDecay = exp(-1.0 / (sampleRate * CrackleDecaySeconds)).toFloat()
        dropFrames = (sampleRate * NeedleDropSeconds).toInt().coerceAtLeast(1)
        java.util.Arrays.fill(delayL, 0f)
        java.util.Arrays.fill(delayR, 0f)
        write = 0
    }

    /** Reads the line [delay] frames back, between samples, so the pitch bends smoothly. */
    private fun readDelayed(line: FloatArray, delay: Float): Float {
        val d = delay.coerceIn(1f, WowLineFrames - 2f)
        val whole = d.toInt()
        val frac = d - whole
        var a = write - whole
        if (a < 0) a += WowLineFrames
        var b = a - 1
        if (b < 0) b += WowLineFrames
        return line[a] * (1f - frac) + line[b] * frac
    }

    /** A small, fast, repeatable noise source: xorshift, then -1..1. */
    private fun nextNoise(): Float {
        rng = rng xor (rng shl 13)
        rng = rng xor (rng ushr 7)
        rng = rng xor (rng shl 17)
        return ((rng ushr 40).toInt() / 8_388_608f) - 1f
    }

    private fun nextUnit(): Float = abs(nextNoise())

    private fun onePole(hz: Float, sampleRate: Int): Float =
        (1.0 - exp(-2.0 * PI * hz / sampleRate)).toFloat().coerceIn(0f, 1f)

    private companion object {
        const val TwoPi = (2.0 * PI).toFloat()
        const val WowLineFrames = 512
        /** The drift and the waver, in turns per second. */
        const val WowHz = 0.63
        const val FlutterHz = 6.4
        /** How far a cent of pitch swing reaches, in seconds of delay. */
        const val WowSwingSeconds = 0.0012f
        const val NoiseLpCoef = 0.52f
        const val NoiseHpCoef = 0.012f
        const val CrackleHeight = 0.5f
        const val CrackleDecaySeconds = 0.0015
        const val NeedlePop = 0.35f
        const val NeedleDropSeconds = 0.8f
        /** How much louder the groove is in the moment after the needle lands. */
        const val NeedleDropLift = 2.2f
    }
}
