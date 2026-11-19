package app.cobre.mobile

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Track(
    val id: Long, val uri: Uri, val title: String, val artist: String,
    val album: String, val no: Int, val durMs: Long, val year: Int,
)

/** Mesmo critério de agrupamento do Desktop 3.1: artista|álbum. (Sem artista do álbum, por ora.) */
data class Album(val artist: String, val title: String, val year: Int, val tracks: List<Track>) {
    val key get() = "$artist|$title"
}

class LocalLibrary(private val ctx: Context) {
    suspend fun load(): List<Track> = withContext(Dispatchers.IO) {
        val out = ArrayList<Track>()
        val coll = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.YEAR,
        )
        ctx.contentResolver.query(coll, proj, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTi = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iAr = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAl = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iNo = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val iDu = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iYe = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val unknown = { s: String? -> s == null || s == "<unknown>" }
                out += Track(
                    id = id, uri = ContentUris.withAppendedId(coll, id),
                    title = c.getString(iTi) ?: "Sem título",
                    artist = c.getString(iAr).let { if (unknown(it)) "Artista desconhecido" else it!! },
                    album = c.getString(iAl).let { if (unknown(it)) "" else it!! },
                    no = c.getInt(iNo) % 1000, // MediaStore pode codificar disco*1000+faixa
                    durMs = c.getLong(iDu), year = c.getInt(iYe),
                )
            }
        }
        out
    }

    fun albums(tracks: List<Track>): List<Album> =
        tracks.groupBy { it.artist to it.album }.map { (k, v) ->
            Album(k.first, k.second.ifEmpty { "Sem álbum" },
                v.minOf { if (it.year > 0) it.year else Int.MAX_VALUE }.let { if (it == Int.MAX_VALUE) 0 else it },
                v.sortedWith(compareBy({ it.no }, { it.title })))
        }.sortedWith(compareBy({ it.artist.lowercase() }, { if (it.year == 0) 9999 else it.year }))
}
