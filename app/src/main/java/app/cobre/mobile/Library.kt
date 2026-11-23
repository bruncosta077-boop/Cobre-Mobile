package app.cobre.mobile

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Track(
    val id: Long, val uri: Uri, val title: String, val artist: String,
    val albumArtist: String, val album: String, val trackNumber: Int,
    val discNumber: Int, val durationMs: Long, val year: Int,
    val sourceUri: Uri? = null,
)
internal fun Track.libraryIdentity(): String {
    fun norm(value: String) = java.text.Normalizer.normalize(value.trim().lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9]+"), "")
    return "${norm(albumArtist.ifBlank { artist })}|${norm(album)}|$trackNumber|${norm(title)}|${norm(artist)}"
}

data class Album(val artist: String, val title: String, val year: Int, val tracks: List<Track>) {
    val key: String get() = "${artist.lowercase()}|${title.lowercase()}"
}
data class Artist(val name: String, val albums: List<Album>, val tracks: List<Track>)

class LocalLibrary(private val context: Context) {
    suspend fun load(folderUri: Uri? = null): List<Track> = withContext(Dispatchers.IO) {
        val items = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.YEAR,
        )
        val relativePath = folderUri?.let {
            runCatching { DocumentsContract.getTreeDocumentId(it).substringAfter(':', "").trim('/') }.getOrNull()
        }.orEmpty()
        val selection = if (relativePath.isBlank()) "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            else "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = if (relativePath.isBlank()) null else arrayOf("$relativePath/%")
        context.contentResolver.query(
            collection, projection, selection, selectionArgs,
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
            fun value(raw: String?, fallback: String) = if (raw.isNullOrBlank() || raw == "<unknown>") fallback else raw.trim()
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val rawTrack = cursor.getInt(trackIndex)
                val rawArtist = cursor.getString(artistIndex)
                val rawAlbumArtist = if (albumArtistIndex >= 0) cursor.getString(albumArtistIndex) else null
                items += Track(
                    id, ContentUris.withAppendedId(collection, id),
                    value(cursor.getString(titleIndex), "Faixa sem título"),
                    value(rawArtist, "Artista desconhecido"),
                    value(rawAlbumArtist, value(rawArtist, "Artista desconhecido")),
                    value(cursor.getString(albumIndex), "Sem álbum"), rawTrack % 1000, rawTrack / 1000,
                    cursor.getLong(durationIndex).coerceAtLeast(0), cursor.getInt(yearIndex).coerceAtLeast(0),
                )
            }
        }
        items
    }

    fun albums(tracks: List<Track>): List<Album> = tracks.groupBy { it.albumArtist.trim().lowercase() to it.album.trim().lowercase() }
        .map { (_, group) ->
            val sorted = group.sortedWith(compareBy<Track>({ it.discNumber }, { it.trackNumber.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }, { it.title.lowercase() }))
            Album(sorted.first().albumArtist, sorted.first().album, sorted.map(Track::year).firstOrNull { it > 0 } ?: 0, sorted)
        }.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))

    fun artists(tracks: List<Track>): List<Artist> = artistsFromAlbums(albums(tracks))

    fun artistsFromAlbums(albums: List<Album>): List<Artist> = albums.groupBy { it.artist.lowercase() }
        .map { (_, albums) -> Artist(albums.first().artist, albums, albums.flatMap(Album::tracks)) }
        .sortedBy { it.name.lowercase() }
}
