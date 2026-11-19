package app.cobre.mobile

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import android.util.Size
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Paleta do Cobre Desktop
private val Bg = Color(0xFF121110); private val Panel = Color(0xFF181614); private val Line = Color(0xFF2A2623)
private val Tx = Color(0xFFECE6DF); private val Mu = Color(0xFF8D847B); private val Cu = Color(0xFFC9773F)

class MainActivity : ComponentActivity() {
    private lateinit var player: PlayerController
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = PlayerController(applicationContext)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Panel, primary = Cu, onBackground = Tx, onSurface = Tx)) {
                Surface(Modifier.fillMaxSize(), color = Bg) { Root(player) }
            }
        }
    }
    override fun onDestroy() { player.release(); super.onDestroy() }
}

@Composable
private fun Root(player: PlayerController) {
    val ctx = LocalContext.current
    val lib = remember { LocalLibrary(ctx) }
    var tracks by remember { mutableStateOf<List<Track>?>(null) }
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ctx.checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (!granted) ask.launch(perm)
    }
    LaunchedEffect(granted) { if (granted) tracks = runCatching { lib.load() }.getOrDefault(emptyList()) }

    var open by remember { mutableStateOf<Album?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Box(Modifier.weight(1f)) {
            val t = tracks
            when {
                !granted -> Msg("O Cobre precisa de acesso às músicas do aparelho.")
                t == null -> Msg("Lendo suas músicas…")
                t.isEmpty() -> Msg("Nenhuma música encontrada neste aparelho.")
                open != null -> AlbumScreen(open!!, player) { open = null }
                else -> Albums(lib.albums(t)) { open = it }
            }
        }
        MiniPlayer(player)
    }
}

@Composable private fun Msg(s: String) = Box(Modifier.fillMaxSize(), Alignment.Center) { Text(s, color = Mu, modifier = Modifier.padding(24.dp)) }

@Composable
private fun Albums(list: List<Album>, onOpen: (Album) -> Unit) {
    LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(list, key = { it.key }) { a ->
            Column(Modifier.clickable { onOpen(a) }) {
                Cover(a.tracks.first(), Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)))
                Text(a.title, color = Tx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                Text(listOf(a.artist, a.year.takeIf { it > 0 }?.toString()).filterNotNull().joinToString(" · "),
                    color = Mu, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AlbumScreen(a: Album, player: PlayerController, onBack: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Column(Modifier.padding(16.dp)) {
                TextButton(onClick = onBack) { Text("‹ Álbuns", color = Cu) }
                Cover(a.tracks.first(), Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)))
                Text(a.title, color = Tx, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                Text("${a.artist} · ${a.tracks.size} faixas", color = Mu)
            }
        }
        items(a.tracks.size) { i ->
            val t = a.tracks[i]
            Row(Modifier.fillMaxWidth().clickable { player.play(a.tracks, i) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(if (t.no > 0) "${t.no}" else "${i + 1}", color = Mu, modifier = Modifier.width(32.dp))
                Text(t.title, color = Tx, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val s = t.durMs / 1000
                Text("${s / 60}:${(s % 60).toString().padStart(2, '0')}", color = Mu)
            }
        }
    }
}

@Composable
private fun MiniPlayer(player: PlayerController) {
    val s by player.state.collectAsState()
    if (!s.hasItem) return
    Row(Modifier.fillMaxWidth().background(Panel).navigationBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(s.title, color = Tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.artist, color = Mu, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = player::prev) { Text("⏮", color = Tx) }
        TextButton(onClick = player::toggle) { Text(if (s.playing) "⏸" else "▶", color = Cu) }
        TextButton(onClick = player::next) { Text("⏭", color = Tx) }
    }
}

/** Capa embutida/da pasta via MediaStore (API 29+). Sem capa: bloco neutro. */
@Composable
private fun Cover(t: Track, modifier: Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, t.id) {
        value = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.loadThumbnail(t.uri, Size(480, 480), null) }.getOrNull() }
    }
    Box(modifier.background(Color(0xFF221F1C))) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}
