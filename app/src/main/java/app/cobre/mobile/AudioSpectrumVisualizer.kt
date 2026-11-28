package app.cobre.mobile

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
import androidx.compose.ui.unit.dp

/**
 * Espectro calculado a partir do áudio que o próprio player já decodifica ([SpectrumAudioProcessor]).
 * Não usa microfone nem a permissão RECORD_AUDIO.
 */
@Composable
internal fun AudioSpectrumVisualizer(playing: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    var enabled by remember { mutableStateOf(false) }
    val levels by SpectrumBus.levels.collectAsState()

    DisposableEffect(enabled) {
        SpectrumBus.enabled = enabled
        if (!enabled) SpectrumBus.clear()
        onDispose {
            SpectrumBus.enabled = false
            SpectrumBus.clear()
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 5.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Equalizer, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text("Espectro de áudio", color = Tx, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
            TextButton(onClick = { enabled = !enabled }) {
                Text(if (enabled) "Parar" else "Ativar", color = MaterialTheme.colorScheme.primary)
            }
        }
        if (enabled && playing) {
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
        Text(
            if (enabled && !playing) "Pausado · o espectro acompanha a reprodução" else "Opcional · processado localmente · sem microfone · não grava nem envia áudio",
            color = Mu, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 3.dp),
        )
    }
}
