package app.cobre.mobile

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(
    val title: String = "", val artist: String = "", val playing: Boolean = false,
    val hasItem: Boolean = false, val queue: List<Track> = emptyList(), val currentIndex: Int = 0,
    val positionMs: Long = 0, val durationMs: Long = 0,
    val shuffleEnabled: Boolean = false, val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

class PlayerController(context: Context) {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = mutableState.asStateFlow()
    private var controller: MediaController? = null
    private var queue: List<Track> = emptyList()
    private var pendingPlay: Pair<List<Track>, Int>? = null
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
    }

    init {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.onSuccess { mediaController ->
                controller = mediaController
                mediaController.addListener(listener)
                pendingPlay?.let { (tracks, index) -> applyQueue(tracks, index) }
                pendingPlay = null
                sync(mediaController)
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    private fun mediaItem(track: Track) = MediaItem.Builder().setMediaId(track.id.toString()).setUri(track.uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).setAlbumTitle(track.album).build()).build()

    private fun applyQueue(tracks: List<Track>, index: Int) {
        if (tracks.isEmpty()) return
        val player = controller ?: return
        queue = tracks.toList()
        player.setMediaItems(queue.map(::mediaItem), index.coerceIn(0, queue.lastIndex), 0)
        player.prepare(); player.play(); sync(player)
    }

    private fun sync(player: Player) {
        if (queue.size != player.mediaItemCount) {
            queue = (0 until player.mediaItemCount).map { i ->
                val item = player.getMediaItemAt(i); val m = item.mediaMetadata; val uri = item.localConfiguration?.uri ?: Uri.EMPTY
                Track(item.mediaId.toLongOrNull() ?: uri.toString().hashCode().toLong(), uri,
                    m.title?.toString() ?: "Faixa", m.artist?.toString() ?: "Artista desconhecido",
                    m.artist?.toString() ?: "Artista desconhecido", m.albumTitle?.toString() ?: "Sem álbum", 0, 0, 0, 0)
            }
        }
        val index = player.currentMediaItemIndex.takeIf { it >= 0 } ?: 0
        val current = queue.getOrNull(index); val metadata = player.mediaMetadata
        val duration = player.duration.let { if (it == C.TIME_UNSET || it < 0) 0 else it }
        mutableState.value = NowPlaying(
            title = current?.title ?: metadata.title?.toString().orEmpty(), artist = current?.artist ?: metadata.artist?.toString().orEmpty(),
            playing = player.isPlaying, hasItem = player.currentMediaItem != null, queue = queue, currentIndex = index,
            positionMs = player.currentPosition.coerceAtLeast(0), durationMs = duration,
            shuffleEnabled = player.shuffleModeEnabled, repeatMode = player.repeatMode,
        )
    }

    fun play(tracks: List<Track>, index: Int = 0) {
        if (tracks.isEmpty()) return
        if (controller == null) pendingPlay = tracks.toList() to index else applyQueue(tracks, index)
    }
    fun playAt(index: Int) { controller?.let { if (index in queue.indices) { it.seekToDefaultPosition(index); it.play() } } }
    fun addToQueue(track: Track) {
        val p = controller ?: return
        if (p.mediaItemCount == 0) applyQueue(listOf(track), 0)
        else { queue = queue + track; p.addMediaItem(mediaItem(track)); sync(p) }
    }
    fun removeFromQueue(index: Int) {
        val p = controller ?: return
        if (index in queue.indices) { queue = queue.toMutableList().also { it.removeAt(index) }; p.removeMediaItem(index); sync(p) }
    }
    fun moveInQueue(from: Int, to: Int) {
        val p = controller ?: return
        if (from !in queue.indices || to !in queue.indices || from == to) return
        queue = queue.toMutableList().also { it.add(to, it.removeAt(from)) }; p.moveMediaItem(from, to); sync(p)
    }
    fun clearQueue() { queue = emptyList(); controller?.clearMediaItems(); controller?.let(::sync) }
    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.let { if (it.currentPosition > 3_000L) it.seekTo(0) else it.seekToPreviousMediaItem() } }
    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }
    fun refreshPosition() { controller?.let(::sync) }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled; sync(it) } }
    fun cycleRepeat() { controller?.let { it.repeatMode = when (it.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }; sync(it) } }
    fun release() { controller?.removeListener(listener); controller?.release(); controller = null }
}
