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
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(
    val title: String = "", val artist: String = "", val playing: Boolean = false,
    val hasItem: Boolean = false, val queue: List<Track> = emptyList(), val currentIndex: Int = 0,
    val positionMs: Long = 0, val durationMs: Long = 0,
    val shuffleEnabled: Boolean = false, val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val formatLabel: String = "",
)

class PlayerController(context: Context) {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = mutableState.asStateFlow()
    private var controller: MediaController? = null
    private var queue: List<Track> = emptyList()
    private var pendingPlay: Pair<List<Track>, Int>? = null
    private val playbackPrefs = appContext.getSharedPreferences("cobre-playback-state", Context.MODE_PRIVATE)
    private var restoring = false
    private var lastSavedPositionMs = -SAVE_POSITION_INTERVAL_MS
    private var lastSavedQueueFingerprint = ""
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
                if (mediaController.mediaItemCount == 0) restoreQueue(mediaController)
                else sync(mediaController)
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    private fun mediaItem(track: Track) = MediaItem.Builder().setMediaId(track.id.toString()).setUri(track.uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).setAlbumTitle(track.album).build()).build()

    private fun applyQueue(tracks: List<Track>, index: Int) {
        if (tracks.isEmpty()) return
        val player = controller ?: return
        queue = tracks.toList()
        restoring = true
        player.setMediaItems(queue.map(::mediaItem), index.coerceIn(0, queue.lastIndex), 0)
        player.prepare(); player.play()
        restoring = false
        sync(player, forceSave = true)
    }

    private fun sync(player: Player, forceSave: Boolean = false) {
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
        val audioFormat = player.currentTracks.groups.asSequence()
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { group -> (0 until group.length).asSequence().filter(group::isTrackSelected).map(group::getTrackFormat) }
            .firstOrNull()
            ?.let { format ->
                val codec = when {
                    format.sampleMimeType == "audio/raw" -> when (format.pcmEncoding) {
                        C.ENCODING_PCM_16BIT -> "PCM 16-bit"
                        C.ENCODING_PCM_FLOAT -> "PCM 32-bit"
                        else -> "PCM"
                    }
                    format.sampleMimeType?.contains("flac", true) == true || format.codecs?.contains("flac", true) == true -> "FLAC"
                    format.sampleMimeType?.contains("mpeg", true) == true -> "MP3"
                    else -> format.codecs?.substringBefore('.')?.uppercase()
                        ?: format.sampleMimeType?.substringAfter('/')?.uppercase()
                        ?: "ÁUDIO"
                }
                buildList {
                    add(codec)
                    if (format.sampleRate > 0) {
                        val whole = format.sampleRate / 1_000
                        val decimal = (format.sampleRate % 1_000) / 100
                        add(if (decimal == 0) "$whole kHz" else "$whole.$decimal kHz")
                    }
                    val bits = when (format.pcmEncoding) {
                        C.ENCODING_PCM_16BIT -> 16
                        C.ENCODING_PCM_24BIT -> 24
                        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 32
                        else -> 0
                    }
                    if (bits > 0) add("$bits-bit")
                    if (format.bitrate > 0) add("${format.bitrate / 1_000} kb/s")
                }.joinToString(" · ")
            }.orEmpty()
        mutableState.value = NowPlaying(
            title = current?.title ?: metadata.title?.toString().orEmpty(), artist = current?.artist ?: metadata.artist?.toString().orEmpty(),
            playing = player.isPlaying, hasItem = player.currentMediaItem != null, queue = queue, currentIndex = index,
            positionMs = player.currentPosition.coerceAtLeast(0), durationMs = duration,
            shuffleEnabled = player.shuffleModeEnabled, repeatMode = player.repeatMode, formatLabel = audioFormat,
        )
        persistQueue(player, forceSave)
    }

    private fun restoreQueue(player: MediaController) {
        val saved = readSavedQueue() ?: return
        if (saved.tracks.isEmpty()) return
        restoring = true
        queue = saved.tracks
        player.shuffleModeEnabled = saved.shuffleEnabled
        player.repeatMode = saved.repeatMode
        player.setMediaItems(queue.map(::mediaItem), saved.currentIndex.coerceIn(0, queue.lastIndex), saved.positionMs)
        // Keep the restored queue paused. Playback starts only after the user presses Play.
        player.pause()
        restoring = false
        sync(player, forceSave = true)
    }

    private fun readSavedQueue(): SavedPlayback? = runCatching {
        val json = playbackPrefs.getString(KEY_QUEUE, null)?.let(::JSONObject) ?: return null
        val items = json.optJSONArray("tracks") ?: return null
        if (items.length() > MAX_PERSISTED_QUEUE) return null
        val tracks = buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val uri = Uri.parse(item.optString("uri"))
                if (uri.scheme !in setOf("content", "file", "http", "https")) continue
                add(Track(
                    id = item.optLong("id", uri.toString().hashCode().toLong()), uri = uri,
                    title = item.optString("title", "Faixa"), artist = item.optString("artist", "Artista desconhecido"),
                    albumArtist = item.optString("albumArtist", item.optString("artist", "Artista desconhecido")),
                    album = item.optString("album", "Sem álbum"), trackNumber = item.optInt("trackNumber"),
                    discNumber = item.optInt("discNumber"), durationMs = item.optLong("durationMs"), year = item.optInt("year"),
                ))
            }
        }
        if (tracks.isEmpty()) return null
        SavedPlayback(
            tracks = tracks,
            currentIndex = json.optInt("currentIndex").coerceIn(0, tracks.lastIndex),
            positionMs = json.optLong("positionMs").coerceAtLeast(0),
            shuffleEnabled = json.optBoolean("shuffleEnabled"),
            repeatMode = json.optInt("repeatMode").takeIf { it in Player.REPEAT_MODE_OFF..Player.REPEAT_MODE_ALL } ?: Player.REPEAT_MODE_OFF,
        )
    }.getOrNull()

    private fun persistQueue(player: Player, force: Boolean) {
        if (restoring) return
        val position = player.currentPosition.coerceAtLeast(0)
        val fingerprint = buildString {
            queue.forEach { append(it.uri).append('|') }
            append(':').append(player.currentMediaItemIndex).append(':').append(player.shuffleModeEnabled).append(':').append(player.repeatMode)
        }
        val queueChanged = fingerprint != lastSavedQueueFingerprint
        if (!force && !queueChanged && kotlin.math.abs(position - lastSavedPositionMs) < SAVE_POSITION_INTERVAL_MS) return
        val tracks = JSONArray().apply {
            queue.take(MAX_PERSISTED_QUEUE).forEach { track ->
                put(JSONObject().apply {
                    put("id", track.id); put("uri", track.uri.toString()); put("title", track.title)
                    put("artist", track.artist); put("albumArtist", track.albumArtist); put("album", track.album)
                    put("trackNumber", track.trackNumber); put("discNumber", track.discNumber)
                    put("durationMs", track.durationMs); put("year", track.year)
                })
            }
        }
        val json = JSONObject().apply {
            put("tracks", tracks); put("currentIndex", player.currentMediaItemIndex.coerceAtLeast(0))
            put("positionMs", position); put("shuffleEnabled", player.shuffleModeEnabled); put("repeatMode", player.repeatMode)
        }
        playbackPrefs.edit().putString(KEY_QUEUE, json.toString()).apply()
        lastSavedQueueFingerprint = fingerprint
        lastSavedPositionMs = position
    }

    fun play(tracks: List<Track>, index: Int = 0) {
        if (tracks.isEmpty()) return
        if (controller == null) pendingPlay = tracks.toList() to index else applyQueue(tracks, index)
    }
    fun playAt(index: Int) { controller?.let { if (index in queue.indices) { it.seekToDefaultPosition(index); it.play() } } }
    fun addToQueue(track: Track) {
        val p = controller ?: return
        if (p.mediaItemCount == 0) applyQueue(listOf(track), 0)
        else { queue = queue + track; p.addMediaItem(mediaItem(track)); sync(p, forceSave = true) }
    }
    fun removeFromQueue(index: Int) {
        val p = controller ?: return
        if (index in queue.indices) { queue = queue.toMutableList().also { it.removeAt(index) }; p.removeMediaItem(index); sync(p, forceSave = true) }
    }
    fun moveInQueue(from: Int, to: Int) {
        val p = controller ?: return
        if (from !in queue.indices || to !in queue.indices || from == to) return
        queue = queue.toMutableList().also { it.add(to, it.removeAt(from)) }; p.moveMediaItem(from, to); sync(p, forceSave = true)
    }
    fun clearQueue() { queue = emptyList(); controller?.clearMediaItems(); controller?.let { sync(it, forceSave = true) } }
    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else { if (it.playbackState == Player.STATE_IDLE && it.mediaItemCount > 0) it.prepare(); it.play() } } }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.let { if (it.currentPosition > 3_000L) it.seekTo(0) else it.seekToPreviousMediaItem() } }
    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }
    fun refreshPosition() { controller?.let(::sync) }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled; sync(it, forceSave = true) } }
    fun cycleRepeat() { controller?.let { it.repeatMode = when (it.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }; sync(it, forceSave = true) } }
    fun release() { controller?.removeListener(listener); controller?.release(); controller = null }

    private data class SavedPlayback(
        val tracks: List<Track>, val currentIndex: Int, val positionMs: Long,
        val shuffleEnabled: Boolean, val repeatMode: Int,
    )

    private companion object {
        const val KEY_QUEUE = "saved_queue"
        const val MAX_PERSISTED_QUEUE = 2_000
        const val SAVE_POSITION_INTERVAL_MS = 10_000L
    }
}
