package app.cobre.mobile

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class SpectrumAnalyzerTest {
    private fun sine(frequency: Double, sampleRate: Int, amplitude: Double = 1.0, size: Int = 1024) =
        FloatArray(size) { (amplitude * sin(2.0 * PI * frequency * it / sampleRate)).toFloat() }

    private fun loudestBand(levels: FloatArray) = levels.indices.maxByOrNull { levels[it] }!!

    @Test fun silenceProducesZeroLevels() {
        val levels = SpectrumAnalyzer().analyze(FloatArray(1024), 44_100)
        assertTrue(levels.all { it == 0f })
    }

    @Test fun oneKilohertzToneLightsTheExpectedBand() {
        val levels = SpectrumAnalyzer().analyze(sine(1_000.0, 44_100), 44_100)
        assertEquals(17, loudestBand(levels))
        assertTrue(levels[17] > 0.95f)
    }

    @Test fun fiveKilohertzToneAtFortyEightKhz() {
        val levels = SpectrumAnalyzer().analyze(sine(5_000.0, 48_000), 48_000)
        assertEquals(25, loudestBand(levels))
    }

    @Test fun tenKilohertzToneLightsTheExpectedBand() {
        val levels = SpectrumAnalyzer().analyze(sine(10_000.0, 44_100), 44_100)
        assertEquals(29, loudestBand(levels))
    }

    @Test fun quieterToneProducesLowerLevel() {
        val analyzer = SpectrumAnalyzer()
        val loud = analyzer.analyze(sine(1_000.0, 44_100), 44_100)[17]
        val quiet = analyzer.analyze(sine(1_000.0, 44_100, amplitude = 0.25), 44_100)[17]
        assertTrue(quiet < loud)
        assertTrue(quiet > 0.5f)
    }

    @Test fun levelsStayBetweenZeroAndOne() {
        val levels = SpectrumAnalyzer().analyze(sine(2_000.0, 44_100, amplitude = 8.0), 44_100)
        assertTrue(levels.all { it in 0f..1f })
    }

    @Test fun tooFewSamplesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { SpectrumAnalyzer().analyze(FloatArray(100), 44_100) }
    }
}
