package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.data.model.analyseSamples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class SongFeaturesTest {
    private val rate = 44100

    /** A drum-like click (30 ms noise burst) on every beat, over a soft tone. */
    private fun clickTrack(bpm: Double, seconds: Int, loud: Float = 0.6f): FloatArray {
        val random = Random(1)
        val beat = (rate * 60.0 / bpm).toInt()
        return FloatArray(rate * seconds) { i ->
            val tone = 0.05f * sin(2 * PI * 220 * i / rate).toFloat()
            val inBeat = i % beat
            val click = if (inBeat < rate * 0.03) loud * (random.nextFloat() * 2 - 1) * (1f - inBeat / (rate * 0.03f)) else 0f
            tone + click
        }
    }

    @Test
    fun `finds the tempo of a steady beat`() {
        for (bpm in listOf(90.0, 120.0, 140.0)) {
            val features = analyseSamples(clickTrack(bpm, 30), rate)!!
            assertEquals("tempo of a $bpm BPM beat", bpm, features.bpm.toDouble(), 4.0)
            assertTrue("a click track is danceable", features.danceability > 0.4f)
        }
    }

    @Test
    fun `louder songs have more energy`() {
        val quiet = analyseSamples(clickTrack(120.0, 20, loud = 0.08f), rate)!!
        val loud = analyseSamples(clickTrack(120.0, 20, loud = 0.9f), rate)!!
        assertTrue(loud.energy > quiet.energy)
    }

    @Test
    fun `silence and short excerpts give nothing`() {
        assertNull(analyseSamples(FloatArray(rate * 20), rate))
        assertNull(analyseSamples(clickTrack(120.0, 3), rate))
    }
}
