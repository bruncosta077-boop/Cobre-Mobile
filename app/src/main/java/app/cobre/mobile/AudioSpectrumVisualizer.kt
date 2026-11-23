package app.cobre.mobile

import android.Manifest
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** Optional local playback spectrum. Permission is requested only after the user chooses to enable it. */
@Composable
internal fun AudioSpectrumVisualizer(audioSessionId: Int, playing: Boolean) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var enabled by remember { mutableStateOf(false) }
    var explainPermission by remember { mutableStateOf(false) }
    var levels by remember { mutableStateOf(List(32) { 0f }) }
    var status by remember { mutableStateOf("") }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) { enabled = true; status = "" }
        else status = "Sem essa permissão, o Android não libera o espectro de áudio."
    }

    DisposableEffect(audioSessionId, enabled, hasPermission) {
        if (!enabled || !hasPermission || !playing) return@DisposableEffect onDispose { }
        if (audioSessionId <= 0) {
            status = "Inicie uma faixa no Cobre para ativar o visualizador."
            return@DisposableEffect onDispose { }
        }
        var visualizer: Visualizer? = null
        runCatching {
            val effect = Visualizer(audioSessionId)
            visualizer = effect
            run {
                val sizeRange = Visualizer.getCaptureSizeRange()
                check(effect.setCaptureSize(sizeRange[1]) == Visualizer.SUCCESS)
                effect.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit
                    override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        val data = fft ?: return
                        val bins = (data.size / 2).coerceAtLeast(2)
                        val maxLog = ln(bins.toDouble())
                        val next = List(32) { bar ->
                            val start = exp(maxLog * bar / 32.0).toInt().coerceIn(1, bins - 1)
                            val end = exp(maxLog * (bar + 1) / 32.0).toInt().coerceIn(start + 1, bins)
                            var peak = 0.0
                            for (bin in start until end) {
                                val real = data[bin * 2].toInt()
                                val imaginary = data[bin * 2 + 1].toInt()
                                peak = maxOf(peak, sqrt((real * real + imaginary * imaginary).toDouble()))
                            }
                            (sqrt(peak / 110.0) * (1.0 + bar * .018)).toFloat().coerceIn(.04f, 1f)
                        }
                        mainHandler.post { levels = next }
                    }
                }, (Visualizer.getMaxCaptureRate() / 2).coerceAtLeast(1), false, true)
                check(effect.setEnabled(true) == Visualizer.SUCCESS)
            }
        }.onSuccess { status = "" }.onFailure {
            visualizer?.release()
            visualizer = null
            status = "Este aparelho ou a saída atual não disponibilizou o visualizador."
        }
        onDispose {
            runCatching { visualizer?.enabled = false }
            runCatching { visualizer?.release() }
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 5.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Equalizer, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text("Espectro de áudio", color = Tx, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
            TextButton(onClick = { if (enabled) { enabled = false; status = "" } else if (hasPermission) enabled = true else explainPermission = true }) {
                Text(if (enabled) "Parar" else "Ativar", color = MaterialTheme.colorScheme.primary)
            }
        }
        if (enabled && hasPermission && audioSessionId > 0 && playing) {
            Canvas(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).background(CardBg).padding(horizontal = 10.dp, vertical = 7.dp)) {
                val gap = 3.dp.toPx()
                val barWidth = ((size.width - gap * (levels.size - 1)) / levels.size).coerceAtLeast(1f)
                levels.forEachIndexed { index, level ->
                    val barHeight = (size.height * level).coerceIn(3.dp.toPx(), size.height)
                    drawRoundRect(
                        color = accent.copy(alpha = .58f + .42f * level),
                        topLeft = Offset(index * (barWidth + gap), (size.height - barHeight) / 2),
                        size = Size(barWidth, barHeight), cornerRadius = CornerRadius(barWidth / 2),
                    )
                }
            }
        }
        Text(when { status.isNotBlank() -> status; enabled && !playing -> "Pausado · o espectro acompanha a reprodução"; else -> "Opcional · processado localmente · não grava nem envia áudio" }, color = Mu, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 3.dp))
    }

    if (explainPermission) AlertDialog(
        onDismissRequest = { explainPermission = false },
        title = { Text("Ativar o espectro de áudio?") },
        text = { Text("O Android exige a permissão de áudio para ler o sinal da sessão de reprodução do Cobre. O visualizador só mostra barras enquanto estiver ativado; o Cobre não grava, salva nem envia o áudio.", color = Mu) },
        confirmButton = { TextButton(onClick = { explainPermission = false; requestPermission.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Continuar", color = MaterialTheme.colorScheme.primary) } },
        dismissButton = { TextButton(onClick = { explainPermission = false }) { Text("Agora não", color = Mu) } },
    )
}
