package app.cobre.mobile

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Converte uma janela de amostras mono (-1..1) em níveis 0..1 por banda de frequência (escala logarítmica).
 * Código puro Kotlin, sem Android: roda em testes de unidade e não exige permissão de microfone.
 */
internal class SpectrumAnalyzer(
    val fftSize: Int = 1024,
    val bands: Int = 32,
    private val minHz: Double = 40.0,
    private val maxHz: Double = 16_000.0,
) {
    init {
        require(fftSize >= 64 && fftSize and (fftSize - 1) == 0) { "fftSize precisa ser potência de 2" }
        require(bands >= 1) { "bands precisa ser positivo" }
    }

    private val hann = DoubleArray(fftSize) { 0.5 - 0.5 * cos(2.0 * PI * it / (fftSize - 1)) }
    private val re = DoubleArray(fftSize)
    private val im = DoubleArray(fftSize)
    private val cosTable = DoubleArray(fftSize / 2) { cos(2.0 * PI * it / fftSize) }
    private val sinTable = DoubleArray(fftSize / 2) { -sin(2.0 * PI * it / fftSize) }
    private val reversed = IntArray(fftSize).also { table ->
        val bits = Integer.numberOfTrailingZeros(fftSize)
        for (i in 0 until fftSize) table[i] = Integer.reverse(i) ushr (32 - bits)
    }

    /** Lê as primeiras [fftSize] amostras de [samples] e escreve [bands] níveis em [out]. */
    fun analyze(samples: FloatArray, sampleRate: Int, out: FloatArray = FloatArray(bands)): FloatArray {
        require(samples.size >= fftSize) { "são necessárias ao menos $fftSize amostras" }
        require(out.size == bands) { "out precisa ter $bands posições" }
        java.util.Arrays.fill(out, 0f)
        if (sampleRate <= 0) return out
        for (i in 0 until fftSize) {
            re[reversed[i]] = samples[i] * hann[i]
            im[reversed[i]] = 0.0
        }
        fft()
        val binHz = sampleRate.toDouble() / fftSize
        val top = min(maxHz, sampleRate / 2.0 * 0.98)
        if (top <= minHz) return out
        val ratio = top / minHz
        val lastBin = fftSize / 2
        for (band in 0 until bands) {
            val low = minHz * ratio.pow(band.toDouble() / bands)
            val high = minHz * ratio.pow((band + 1.0) / bands)
            var start = ceil(low / binHz).toInt().coerceAtLeast(1)
            var end = ceil(high / binHz).toInt()
            if (end <= start) end = start + 1
            end = end.coerceAtMost(lastBin)
            if (start >= end) start = end - 1
            var peak = 0.0
            for (k in start until end) {
                val magnitude = sqrt(re[k] * re[k] + im[k] * im[k]) / (fftSize / 4.0)
                if (magnitude > peak) peak = magnitude
            }
            val db = 20.0 * log10(peak + 1e-9)
            out[band] = ((db - FLOOR_DB) / -FLOOR_DB).coerceIn(0.0, 1.0).toFloat()
        }
        return out
    }

    /** FFT radix-2 in-place; a entrada já foi colocada em ordem de bits invertidos. */
    private fun fft() {
        var size = 2
        while (size <= fftSize) {
            val half = size / 2
            val step = fftSize / size
            var start = 0
            while (start < fftSize) {
                var k = 0
                for (j in start until start + half) {
                    val wr = cosTable[k]
                    val wi = sinTable[k]
                    val tr = wr * re[j + half] - wi * im[j + half]
                    val ti = wr * im[j + half] + wi * re[j + half]
                    re[j + half] = re[j] - tr
                    im[j + half] = im[j] - ti
                    re[j] += tr
                    im[j] += ti
                    k += step
                }
                start += size
            }
            size *= 2
        }
    }

    companion object {
        const val FLOOR_DB = -70.0
    }
}
