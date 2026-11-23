package app.cobre.mobile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CoverSearchDialog(album: Album, repository: CoverArtworkRepository, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var candidates by remember(album.key) { mutableStateOf<List<CoverCandidate>>(emptyList()) }
    var loading by remember(album.key) { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(album.key) {
        loading = true
        candidates = runCatching { repository.search(album.artist, album.title) }.getOrDefault(emptyList())
        loading = false
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Column { Text("Buscar capa", fontWeight = FontWeight.SemiBold); Text("${album.artist} · ${album.title}", color = Mu, style = MaterialTheme.typography.bodySmall, maxLines = 2) } },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 510.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("A capa escolhida fica salva somente no Cobre Mobile e não altera os arquivos nem a biblioteca do Desktop.", color = Mu, style = MaterialTheme.typography.bodySmall)
                if (loading) Row(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    Text("Procurando em fontes de música…", color = Mu, modifier = Modifier.padding(start = 12.dp))
                }
                if (!loading && candidates.isEmpty()) Text("Não encontrei uma capa exata agora. Confira o nome do álbum e tente novamente mais tarde.", color = Mu, modifier = Modifier.padding(vertical = 18.dp))
                candidates.forEach { candidate ->
                    CoverCandidateRow(candidate, enabled = !saving) {
                        saving = true
                        message = ""
                        scope.launch {
                            val saved = runCatching { repository.save(album.tracks.first(), candidate) }.getOrDefault(false)
                            saving = false
                            if (saved) onDismiss() else message = "Não consegui salvar essa imagem. Escolha outra capa."
                        }
                    }
                }
                if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Fechar", color = MaterialTheme.colorScheme.primary) } },
    )
}

@Composable
private fun CoverCandidateRow(candidate: CoverCandidate, enabled: Boolean, onClick: () -> Unit) {
    val bitmap by produceState<Bitmap?>(null, candidate) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(candidate.bytes, 0, candidate.bytes.size) }
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg).clickable(enabled = enabled, onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), "Capa do álbum", Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
        else Box(Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).background(Panel), contentAlignment = Alignment.Center) { Icon(Icons.Default.Album, null, tint = Mu) }
        Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
            Text(candidate.source, color = Tx, fontWeight = FontWeight.Medium)
            Text("Usar esta capa", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        if (enabled) Text("Usar", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        else CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
    }
}
