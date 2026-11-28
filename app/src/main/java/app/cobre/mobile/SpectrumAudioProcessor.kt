package app.cobre.mobile

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Ponte entre o processador de áudio (thread de reprodução) e a interface. Não guarda nem envia áudio. */
internal object SpectrumBus {
    const val BANDS = 32

    /** A análise só roda enquanto a interface do espectro estiver ligada. */
    @Volatile var enabled: Boolean = false

    private val state = MutableStateFlow(FloatArray(BANDS))
    val levels: StateFlow<FloatArray> = state

    fun publish(values: FloatArray) { state.value = values }
    fun clear() { state.value = FloatArray(BANDS) }
}

/**
 * Lê o PCM que já passa pelo ExoPlayer, sem alterá-lo, e calcula o espectro localmente.
 * Substitui o `android.media.audiofx.Visualizer`, que exigia a permissão RECORD_AUDIO.
 */
@UnstableApi
internal class SpectrumAudioProcessor : BaseAudioProcessor() {
    private val analyzer = SpectrumAnalyzer(bands = SpectrumBus.BANDS)
    private val ring = FloatArray(analyzer.fftSize)
    private val ordered = FloatArray(analyzer.fftSize)
    private val bandLevels = FloatArray(analyzer.bands)
    private val smoothed = FloatArray(analyzer.bands)
    private var writePosition = 0
    private var filled = 0
    private var sinceLast = 0
    private var hop = 1470
    private var sampleRate = 44_100
    private var channels = 2
    private var isFloat = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val supported = (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) &&
            inputAudioFormat.channelCount in 1..8 && inputAudioFormat.sampleRate > 0
        // Formato não suportado (ex.: PCM 24/32 bits): o processador fica inativo e o áudio segue intacto.
        if (!supported) return AudioProcessor.AudioFormat.NOT_SET
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        hop = (sampleRate / 30).coerceAtLeast(analyzer.fftSize / 8)
        resetWindow()
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        if (SpectrumBus.enabled) {
            runCatching { analyze(inputBuffer.duplicate().order(ByteOrder.nativeOrder())) }
        }
        val output = replaceOutputBuffer(size)
        output.put(inputBuffer)
        output.flip()
    }

    override fun onReset() = resetWindow()

    private fun resetWindow() {
        writePosition = 0; filled = 0; sinceLast = 0
        java.util.Arrays.fill(ring, 0f)
        java.util.Arrays.fill(smoothed, 0f)
    }

    private fun analyze(buffer: ByteBuffer) {
        val bytesPerSample = if (isFloat) 4 else 2
        val frameBytes = channels * bytesPerSample
        var position = buffer.position()
        while (position + frameBytes <= buffer.limit()) {
            var sum = 0f
            for (channel in 0 until channels) {
                val at = position + channel * bytesPerSample
                sum += if (isFloat) buffer.getFloat(at) else buffer.getShort(at) / 32768f
            }
            position += frameBytes
            ring[writePosition] = sum / channels
            writePosition = (writePosition + 1) % ring.size
            if (filled < ring.size) filled++
            sinceLast++
            if (filled == ring.size && sinceLast >= hop) {
                sinceLast = 0
                publish()
            }
        }
    }

    private fun publish() {
        for (i in ordered.indices) ordered[i] = ring[(writePosition + i) % ring.size]
        analyzer.analyze(ordered, sampleRate, bandLevels)
        for (band in bandLevels.indices) {
            val tilted = (bandLevels[band] * (1f + band * 0.012f)).coerceAtMost(1f)
            // Sobe rápido, desce devagar: barras mais agradáveis de olhar.
            smoothed[band] = if (tilted >= smoothed[band]) tilted else smoothed[band] - (smoothed[band] - tilted) * 0.25f
        }
        SpectrumBus.publish(smoothed.copyOf())
    }
}

/** Mesmo comportamento do DefaultRenderersFactory, com o processador de espectro na cadeia de áudio. */
@UnstableApi
internal class SpectrumRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
        DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf<AudioProcessor>(SpectrumAudioProcessor()))
            .build()
}
