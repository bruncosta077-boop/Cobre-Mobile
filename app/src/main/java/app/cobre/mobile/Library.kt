package app.cobre.mobile

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Track(
    val id: Long,
    val uri: android.net.Uri,
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val trackNumber: Int,
    val discNumber: Int,
    val durationMs: Long,
    val year: Int,
)

data class Album(val artist: String, val title: String, val year: Int, val tracks: List<Track>) {
    val key: String get() = "${artist.lowercase()}|${title.lowercase()}"
}

data class Artist(val name: String, val albums: List<Album>, val tracks: List<Track>)

class LocalLibrary(private val context: Context) {
    suspend fun load(): List<Track> = withContext(Dispatchers.IO) {
        val items = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.YEAR,
        )
        context.contentResolver.query(
            collection, projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
            "${MediaStore.Audio.Media.ARTIST} COLLATE NOCASE ASC, ${MediaStore.Audio.Media.ALBUM} COLLATE NOCASE ASC, ${MediaStore.Audio.Media.TRACK} ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumArtistIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
            val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val trackIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val yearIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            fun metadata(value: String?, fallback: String) =
                if (value.isNullOrBlank() || value == "<unknown>") fallback else value.trim()

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val rawTrack = cursor.getInt(trackIndex)
                val rawArtist = cursor.getString(artistIndex)
                val rawAlbumArtist = if (albumArtistIndex >= 0) cursor.getString(albumArtistIndex) else null
                items += Track(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = metadata(cursor.getString(titleIndex), "Faixa sem título"),
                    artist = metadata(rawArtist, "Artista desconhecido"),
                    albumArtist = metadata(rawAlbumArtist, metadata(rawArtist, "Artista desconhecido")),
                    album = metadata(cursor.getString(albumIndex), "Sem álbum"),
                    trackNumber = rawTrack % 1000,
                    discNumber = rawTrack / 1000,
                    durationMs = cursor.getLong(durationIndex).coerceAtLeast(0),
                    year = cursor.getInt(yearIndex).coerceAtLeast(0),
                )
            }
        }
        items
    }

    fun albums(tracks: List<Track>): List<Album> = tracks
        .groupBy { it.albumArtist.trim().lowercase() to it.album.trim().lowercase() }
        .map { (_, group) ->
            val sorted = group.sortedWith(compareBy<Track>({ it.discNumber }, { it.trackNumber.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }, { it.title.lowercase() }))
            Album(sorted.first().albumArtist, sorted.first().album,
                sorted.map(Track::year).firstOrNull { it > 0 } ?: 0, sorted)
        }
        .sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))

    fun artists(tracks: List<Track>): List<Artist> = albums(tracks)
        .groupBy { it.artist.lowercase() }
        .map { (_, albums) -> Artist(albums.first().artist, albums, albums.flatMap(Album::tracks)) }
        .sortedBy { it.name.lowercase() }
}