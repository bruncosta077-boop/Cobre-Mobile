package app.cobre.mobile

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AlbumInfoDialog(album: Album, repository: AlbumInfoRepository, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var editions by remember(album.key) { mutableStateOf<List<AlbumEdition>>(emptyList()) }
    var info by remember(album.key) { mutableStateOf<AlbumInfo?>(null) }
    var selectedImage by remember(album.key) { mutableStateOf<AlbumBookletImage?>(null) }
    var loading by remember(album.key) { mutableStateOf(true) }
    var error by remember(album.key) { mutableStateOf("") }
    LaunchedEffect(album.key) {
        loading = true
        runCatching { repository.findEditions(album) }
            .onSuccess { editions = it; if (it.isEmpty()) error = "Não encontrei uma edição com artista e álbum correspondentes." }
            .onFailure { error = "Não foi possível consultar os créditos agora. Tente novamente mais tarde." }
        loading = false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (info != null || selectedImage != null) IconButton(onClick = { if (selectedImage != null) selectedImage = null else info = null }) { Icon(Icons.Default.ArrowBack, "Voltar", tint = MaterialTheme.colorScheme.primary) }
                Column(Modifier.weight(1f)) {
                    Text(if (selectedImage != null) selectedImage?.type.orEmpty() else if (info != null) "Encarte e créditos" else "Informações do álbum", fontWeight = FontWeight.SemiBold)
                    Text("${album.artist} · ${album.title}", color = Mu, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    loading -> Row(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text("Conferindo edições no MusicBrainz…", color = Mu, modifier = Modifier.padding(start = 12.dp))
                    }
                    selectedImage != null -> BookletFullImage(selectedImage!!, repository)
                    info == null && editions.isNotEmpty() -> {
                        Text("Escolha a edição correta para ver os créditos e imagens disponíveis. Isso evita misturar informações de relançamentos diferentes.", color = Mu, style = MaterialTheme.typography.bodySmall)
                        editions.forEach { edition ->
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg).clickable(enabled = !loading) {
                                loading = true; error = ""
                                scope.launch {
                                    runCatching { repository.load(edition) }
                                        .onSuccess { info = it }
                                        .onFailure { error = "Não consegui carregar esta edição. Escolha outra ou tente novamente." }
                                    loading = false
                                }
                            }.padding(12.dp)) {
                                Text(edition.title, color = Tx, fontWeight = FontWeight.Medium)
                                val metadata = listOf(edition.date, edition.country, edition.format, edition.trackCount.takeIf { it > 0 }?.let { "$it faixas" }).filterNotNull().filter(String::isNotBlank)
                                Text(metadata.joinToString(" · ").ifBlank { "Edição sem detalhes adicionais" }, color = Mu, style = MaterialTheme.typography.bodySmall)
                                Text(edition.creditedArtist, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    info != null -> {
                        val current = info!!
                        Text("Edição: ${listOf(current.edition.date, current.edition.country, current.edition.format).filter(String::isNotBlank).joinToString(" · ").ifBlank { "sem detalhes" }}", color = Mu, style = MaterialTheme.typography.bodySmall)
                        if (current.labels.isNotEmpty()) InfoLine("Gravadora", current.labels.joinToString(" · "))
                        InfoLine("Crédito do álbum", current.edition.creditedArtist)
                        HorizontalDivider(color = Line)
                        Text("Créditos das faixas", color = Tx, fontWeight = FontWeight.SemiBold)
                        if (current.tracks.isEmpty()) Text("Esta edição não tem créditos de faixa cadastrados.", color = Mu, style = MaterialTheme.typography.bodySmall)
                        current.tracks.forEach { track ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                Text(track.position, color = Mu, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(34.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, color = Tx, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                    Text(track.artists, color = Mu, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        HorizontalDivider(color = Line)
                        Text("Encarte e imagens", color = Tx, fontWeight = FontWeight.SemiBold)
                        if (current.images.isEmpty()) Text("A edição não tem imagens de encarte cadastradas no arquivo comunitário.", color = Mu, style = MaterialTheme.typography.bodySmall)
                        else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            current.images.forEach { image -> BookletThumbnail(image, repository) { selectedImage = image } }
                        }
                        Text("Créditos e imagens: MusicBrainz / Cover Art Archive, conforme os dados enviados pela comunidade.", color = Mu, style = MaterialTheme.typography.labelSmall)
                    }
                    error.isNotBlank() -> Text(error, color = Mu, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = MaterialTheme.colorScheme.primary) } },
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Mu, style = MaterialTheme.typography.labelSmall)
        Text(value, color = Tx, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BookletThumbnail(image: AlbumBookletImage, repository: AlbumInfoRepository, onClick: () -> Unit) {
    val bitmap by produceState<Bitmap?>(null, image.thumbnail) { value = withContext(Dispatchers.IO) { repository.loadImage(image.thumbnail) } }
    Column(Modifier.width(118.dp).clip(RoundedCornerShape(10.dp)).background(CardBg).clickable(onClick = onClick).padding(6.dp)) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), image.type, Modifier.fillMaxWidth().height(118.dp).clip(RoundedCornerShape(7.dp)), contentScale = ContentScale.Crop)
        else Box(Modifier.fillMaxWidth().height(118.dp).clip(RoundedCornerShape(7.dp)).background(Panel), contentAlignment = Alignment.Center) { Icon(Icons.Default.BrokenImage, null, tint = Mu) }
        Text(image.type.ifBlank { "Imagem" }, color = Tx, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        if (image.comment.isNotBlank()) Text(image.comment, color = Mu, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BookletFullImage(image: AlbumBookletImage, repository: AlbumInfoRepository) {
    val bitmap by produceState<Bitmap?>(null, image.url) { value = withContext(Dispatchers.IO) { repository.loadImage(image.url) } }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), image.type, Modifier.fillMaxWidth().heightIn(max = 470.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Fit)
    else Box(Modifier.fillMaxWidth().height(260.dp).background(CardBg), contentAlignment = Alignment.Center) { Icon(Icons.Default.AutoStories, null, tint = Mu, modifier = Modifier.size(36.dp)) }
}
