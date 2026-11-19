package app.cobre.mobile

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(val title: String = "", val artist: String = "", val playing: Boolean = false, val hasItem: Boolean = false)

class PlayerController(ctx: Context) {
    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()
    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
    }

    init {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val f = MediaController.Builder(ctx, token).buildAsync()
        f.addListener({
            controller = f.get().also { it.addListener(listener); sync(it) }
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun sync(p: Player) {
        val m = p.mediaMetadata
        _state.value = NowPlaying(m.title?.toString() ?: "", m.artist?.toString() ?: "", p.isPlaying, p.currentMediaItem != null)
    }

    fun play(tracks: List<Track>, index: Int) {
        val c = controller ?: return
        c.setMediaItems(tracks.map(::toItem), index, 0)
        c.prepare(); c.play()
    }

    private fun toItem(t: Track) = MediaItem.Builder()
        .setMediaId(t.id.toString()).setUri(t.uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album).build())
        .build()

    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNextMediaItem() }
    fun prev() { controller?.seekToPreviousMediaItem() }
    fun release() { controller?.release(); controller = null }
}
