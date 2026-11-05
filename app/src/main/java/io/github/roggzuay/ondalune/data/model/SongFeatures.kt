package io.github.roggzuay.ondalune.data.model

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.sqrt

/**
 * What on-device song analysis estimates from a short excerpt of a song stored on the phone. Rough by
 * design: enough to sort songs into calm and lively, and to keep autoplay near the same tempo.
 * All values but [bpm] run 0..1.
 */
data class SongFeatures(
    val bpm: Float,
    /** Loudness of the excerpt (RMS): 0 very quiet, 1 loud and dense. */
    val energy: Float,
    /** How much high-frequency content there is (zero-crossing rate): 0 dark, 1 bright. */
    val brightness: Float,
    /** How strong and regular the beat is: 0 free-flowing, 1 steady pulse. */
    val danceability: Float
)

private const val ANALYSIS_RATE = 11025
private const val HOP = 256
private const val FRAME = 512

/**
 * Estimates [SongFeatures] from mono samples in -1..1 at [sampleRate]. Returns null for an excerpt
 * too short or too quiet to say anything.
 *
 * Tempo: the onset envelope (rises in log energy, frame by frame) is autocorrelated over the lags
 * of 60–200 BPM, with a gentle preference for tempos near 120 so that half and double tempo lose
 * to the felt beat. Danceability is how far that peak stands above the rest.
 */
fun analyseSamples(mono: FloatArray, sampleRate: Int): SongFeatures? {
    if (sampleRate <= 0 || mono.size < sampleRate * 8) return null

    var sumSquares = 0.0
    var crossings = 0
    for (i in mono.indices) {
        val x = mono[i]
        sumSquares += x * x
        if (i > 0 && (x >= 0f) != (mono[i - 1] >= 0f)) crossings++
    }
    val rms = sqrt(sumSquares / mono.size)
    if (rms < 1e-4) return null
    val dbfs = 20 * log10(rms)
    val energy = ((dbfs + 35) / 27).toFloat().coerceIn(0f, 1f) // -35 dBFS → 0, -8 dBFS → 1
    val brightness = (crossings.toDouble() / mono.size / 0.15).toFloat().coerceIn(0f, 1f)

    // Decimate to ~11 kHz by block averaging: tempo lives in the envelope, not the highs.
    val step = (sampleRate / ANALYSIS_RATE).coerceAtLeast(1)
    val rate = sampleRate.toDouble() / step
    val low = FloatArray(mono.size / step) { i ->
        var s = 0f
        for (k in 0 until step) s += mono[i * step + k]
        s / step
    }
    val frames = (low.size - FRAME) / HOP
    if (frames < 64) return null
    val logEnergy = DoubleArray(frames) { f ->
        var e = 0.0
        val start = f * HOP
        for (k in start until start + FRAME) e += low[k] * low[k]
        ln(1e-9 + e / FRAME)
    }
    val onset = DoubleArray(frames)
    for (f in 1 until frames) onset[f] = (logEnergy[f] - logEnergy[f - 1]).coerceAtLeast(0.0)
    val mean = onset.average()
    for (f in onset.indices) onset[f] -= mean

    val hopSec = HOP / rate
    val minLag = (60.0 / (200 * hopSec)).toInt().coerceAtLeast(1)
    val maxLag = (60.0 / (60 * hopSec)).toInt().coerceAtMost(frames / 2)
    val zero = onset.sumOf { it * it }
    if (zero <= 0.0 || maxLag <= minLag + 2) return null
    val lags = minOf(2 * maxLag + 2, frames - 1)
    val correlation = DoubleArray(lags + 1) { lag ->
        var s = 0.0
        for (f in 0 until frames - lag) s += onset[f] * onset[f + lag]
        s / zero
    }
    // A beat rarely falls on a whole number of frames: take the best of each lag's neighbours.
    fun peak(lag: Int) = if (lag + 1 > lags) 0.0 else maxOf(correlation[lag - 1], correlation[lag], correlation[lag + 1])
    var bestLag = minLag
    var bestScore = Double.NEGATIVE_INFINITY
    for (lag in minLag..maxLag) {
        val bpm = 60.0 / (lag * hopSec)
        val prior = exp(-0.5 * (log2(bpm / 120.0) / 0.9).let { it * it })
        // The true beat also repeats at twice its lag; half tempo would need four times.
        val score = (peak(lag) + 0.5 * peak(2 * lag)) * prior
        if (score > bestScore) { bestScore = score; bestLag = lag }
    }
    // Centre on the strongest of the neighbours before refining.
    bestLag = (bestLag - 1..bestLag + 1).filter { it in 1 until lags }.maxBy { correlation[it] }
    // Parabolic interpolation around the peak for a finer tempo than one frame allows.
    val a = correlation[bestLag - 1]
    val b = correlation[bestLag]
    val c = correlation[bestLag + 1]
    val denominator = a - 2 * b + c
    val shift = if (abs(denominator) > 1e-12) (0.5 * (a - c) / denominator).coerceIn(-0.5, 0.5) else 0.0
    val bpm = (60.0 / ((bestLag + shift) * hopSec)).toFloat()

    val others = (minLag..maxLag).map { correlation[it] }
    val spread = (others.max() - others.average()).coerceAtLeast(1e-9)
    val danceability = ((b - others.average()) / spread * b.coerceAtLeast(0.0) * 2.5).toFloat().coerceIn(0f, 1f)

    return SongFeatures(bpm, energy, brightness, danceability)
}
