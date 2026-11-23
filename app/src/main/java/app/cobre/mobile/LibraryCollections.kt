package app.cobre.mobile

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SavedPlaylist(val name: String, val tracks: List<Track>)

/** Coleções locais guardadas em SQLite, sem depender da conta ou do servidor. */
class LibraryCollections(context: Context) {
    private val database = CollectionsDatabase(context.applicationContext)
    var favorites by mutableStateOf(readTracks("favorites", "saved_at DESC"))
        private set
    var history by mutableStateOf(readTracks("history", "played_at DESC", 200))
        private set
    var playlists by mutableStateOf(readPlaylists())
        private set
    private var albumRatings by mutableStateOf(readAlbumRatings())
    fun rating(album: Album): Int = albumRatings[albumIdentity(album)] ?: 0

    suspend fun rebindRemoteTracks(available:List<Track>){
        val snapshot=withContext(Dispatchers.IO){
            val currentByIdentity=available.asSequence().filter{it.uri.scheme=="http"}.associateBy{it.libraryIdentity()}
            if(currentByIdentity.isEmpty())return@withContext null
            val db=database.writableDatabase
            db.beginTransaction()
            try{
                listOf("favorites" to "saved_at","history" to "played_at").forEach{(table,timeColumn)->
                    val rows=db.query(table,TRACK_COLUMNS+timeColumn,null,null,null,null,"$timeColumn DESC").use{cursor->buildList(cursor.count){while(cursor.moveToNext())add(cursor.toTrack() to cursor.getLong(cursor.getColumnIndexOrThrow(timeColumn)))}}
                    rows.forEach rowLoop@{(stored,timestamp)->
                        if(stored.uri.scheme!="http")return@rowLoop
                        val replacement=currentByIdentity[stored.libraryIdentity()]?:return@rowLoop
                        if(safeStoredTrackUri(stored.uri)==safeStoredTrackUri(replacement.uri))return@rowLoop
                        val replacementUri=safeStoredTrackUri(replacement.uri)
                        val existingTime=db.query(table,arrayOf(timeColumn),"uri = ?",arrayOf(replacementUri),null,null,null).use{if(it.moveToFirst())it.getLong(0)else 0L}
                        db.delete(table,"uri = ?",arrayOf(stored.uri.toString()))
                        db.insertWithOnConflict(table,null,trackValues(replacement).apply{put(timeColumn,maxOf(timestamp,existingTime))},SQLiteDatabase.CONFLICT_REPLACE)
                    }
                }
                val playlistRows=db.query("playlist_tracks",arrayOf("playlist_id","position")+TRACK_COLUMNS,null,null,null,null,null).use{cursor->buildList(cursor.count){while(cursor.moveToNext())add(Triple(cursor.getLong(0),cursor.getInt(1),cursor.toTrack()))}}
                playlistRows.forEach playlistLoop@{(playlistId,position,stored)->
                    if(stored.uri.scheme!="http")return@playlistLoop
                    val replacement=currentByIdentity[stored.libraryIdentity()]?:return@playlistLoop
                    if(safeStoredTrackUri(stored.uri)==safeStoredTrackUri(replacement.uri))return@playlistLoop
                    db.update("playlist_tracks",trackValues(replacement),"playlist_id = ? AND position = ?",arrayOf(playlistId.toString(),position.toString()))
                }
                db.setTransactionSuccessful()
            }finally{db.endTransaction()}
            CollectionSnapshot(readTracks("favorites","saved_at DESC"),readTracks("history","played_at DESC",200),readPlaylists())
        }?:return
        favorites=snapshot.favorites
        history=snapshot.history
        playlists=snapshot.playlists
    }

    fun setRating(album: Album, rating: Int) {
        val key = albumIdentity(album)
        val db = database.writableDatabase
        if (rating <= 0) db.delete("album_ratings", "album_key = ?", arrayOf(key))
        else db.insertWithOnConflict("album_ratings", null, ContentValues().apply {
            put("album_key", key); put("rating", rating.coerceIn(1, 5)); put("updated_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
        albumRatings = readAlbumRatings()
    }

    private fun readAlbumRatings(): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        database.readableDatabase.query("album_ratings", arrayOf("album_key", "rating"), null, null, null, null, null).use { c ->
            while (c.moveToNext()) result[c.getString(0)] = c.getInt(1)
        }
        return result
    }

    private fun albumIdentity(album: Album): String {
        fun norm(value: String) = java.text.Normalizer.normalize(value.trim().lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9]+"), "")
        return "${norm(album.artist)}|${norm(album.title)}"
    }

    fun isFavorite(track: Track) = favorites.any { safeStoredTrackUri(it.uri) == safeStoredTrackUri(track.uri) }

    fun toggleFavorite(track: Track) {
        val db = database.writableDatabase
        if (isFavorite(track)) db.delete("favorites", "uri = ?", arrayOf(track.uri.toString()))
        else db.insertWithOnConflict("favorites", null, trackValues(track).apply { put("saved_at", System.currentTimeMillis()) }, SQLiteDatabase.CONFLICT_REPLACE)
        favorites = readTracks("favorites", "saved_at DESC")
    }

    fun savePlaylist(name: String, tracks: List<Track>): Boolean {
        val cleanName = name.trim().take(60)
        if (cleanName.isBlank() || tracks.isEmpty() || playlists.any { it.name.equals(cleanName, ignoreCase = true) }) return false
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val id = db.insertOrThrow("playlists", null, ContentValues().apply {
                put("name", cleanName); put("created_at", System.currentTimeMillis())
            })
            tracks.forEachIndexed { index, track ->
                db.insertOrThrow("playlist_tracks", null, trackValues(track).apply { put("playlist_id", id); put("position", index) })
            }
            db.setTransactionSuccessful()
        } catch (_: Exception) {
            return false
        } finally {
            db.endTransaction()
        }
        playlists = readPlaylists()
        return true
    }

    fun addTrackToPlaylist(name: String, track: Track): Boolean {
        val db = database.writableDatabase
        val playlistId = db.query("playlists", arrayOf("_id"), "name = ? COLLATE NOCASE", arrayOf(name), null, null, null)
            .use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else return false }
        val alreadyAdded = db.query("playlist_tracks", arrayOf("position"), "playlist_id = ? AND uri = ?", arrayOf(playlistId.toString(), track.uri.toString()), null, null, null)
            .use { it.moveToFirst() }
        if (alreadyAdded) return false
        val nextPosition = db.query("playlist_tracks", arrayOf("COALESCE(MAX(position), -1) + 1"), "playlist_id = ?", arrayOf(playlistId.toString()), null, null, null)
            .use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
        val inserted = db.insert("playlist_tracks", null, trackValues(track).apply {
            put("playlist_id", playlistId); put("position", nextPosition)
        }) != -1L
        if (inserted) playlists = readPlaylists()
        return inserted
    }

    fun deletePlaylist(name: String) {
        database.writableDatabase.delete("playlists", "name = ?", arrayOf(name))
        playlists = readPlaylists()
    }

    fun recordPlayed(track: Track) {
        val db = database.writableDatabase
        db.insertWithOnConflict("history", null, trackValues(track).apply { put("played_at", System.currentTimeMillis()) }, SQLiteDatabase.CONFLICT_REPLACE)
        db.execSQL("DELETE FROM history WHERE uri NOT IN (SELECT uri FROM history ORDER BY played_at DESC LIMIT 200)")
        history = readTracks("history", "played_at DESC", 200)
    }

    private fun readTracks(table: String, order: String, limit: Int? = null): List<Track> {
        val cursor = database.readableDatabase.query(table, TRACK_COLUMNS, null, null, null, null, order, limit?.toString())
        return cursor.use { c -> buildList(c.count) { while (c.moveToNext()) add(c.toTrack()) } }
    }

    private fun readPlaylists(): List<SavedPlaylist> {
        val db = database.readableDatabase
        val result = mutableListOf<SavedPlaylist>()
        db.query("playlists", arrayOf("_id", "name"), null, null, null, null, "created_at ASC").use { playlistsCursor ->
            while (playlistsCursor.moveToNext()) {
                val id = playlistsCursor.getLong(0)
                val name = playlistsCursor.getString(1)
                val cursor = db.query("playlist_tracks", TRACK_COLUMNS, "playlist_id = ?", arrayOf(id.toString()), null, null, "position ASC")
                val tracks = cursor.use { c -> buildList(c.count) { while (c.moveToNext()) add(c.toTrack()) } }
                result += SavedPlaylist(name, tracks)
            }
        }
        return result
    }

    private data class CollectionSnapshot(val favorites:List<Track>,val history:List<Track>,val playlists:List<SavedPlaylist>)

    private fun trackValues(track: Track) = ContentValues().apply {
        put("uri", safeStoredTrackUri(track.uri)); put("media_id", track.id); put("title", track.title)
        put("artist", track.artist); put("album_artist", track.albumArtist); put("album", track.album)
        put("track_number", track.trackNumber); put("disc_number", track.discNumber)
        put("duration_ms", track.durationMs); put("year", track.year)
    }

    private fun android.database.Cursor.toTrack() = Track(
        id = getLong(getColumnIndexOrThrow("media_id")), uri = Uri.parse(getString(getColumnIndexOrThrow("uri"))),
        title = getString(getColumnIndexOrThrow("title")), artist = getString(getColumnIndexOrThrow("artist")),
        albumArtist = getString(getColumnIndexOrThrow("album_artist")), album = getString(getColumnIndexOrThrow("album")),
        trackNumber = getInt(getColumnIndexOrThrow("track_number")), discNumber = getInt(getColumnIndexOrThrow("disc_number")),
        durationMs = getLong(getColumnIndexOrThrow("duration_ms")), year = getInt(getColumnIndexOrThrow("year")),
    )

    private class CollectionsDatabase(context: Context) : SQLiteOpenHelper(context, "cobre-mobile-library.db", null, 3) {
        override fun onCreate(db: SQLiteDatabase) {
            val trackFields = "uri TEXT NOT NULL, media_id INTEGER NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, album_artist TEXT NOT NULL, album TEXT NOT NULL, track_number INTEGER NOT NULL, disc_number INTEGER NOT NULL, duration_ms INTEGER NOT NULL, year INTEGER NOT NULL"
            db.execSQL("CREATE TABLE favorites ($trackFields, saved_at INTEGER NOT NULL, PRIMARY KEY(uri))")
            db.execSQL("CREATE TABLE history ($trackFields, played_at INTEGER NOT NULL, PRIMARY KEY(uri))")
            db.execSQL("CREATE TABLE playlists (_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL COLLATE NOCASE UNIQUE, created_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE playlist_tracks (playlist_id INTEGER NOT NULL REFERENCES playlists(_id) ON DELETE CASCADE, position INTEGER NOT NULL, $trackFields, PRIMARY KEY(playlist_id, position))")
            db.execSQL("CREATE INDEX ix_playlist_order ON playlist_tracks(playlist_id, position)")
            db.execSQL("CREATE TABLE album_ratings (album_key TEXT PRIMARY KEY NOT NULL, rating INTEGER NOT NULL CHECK(rating BETWEEN 1 AND 5), updated_at INTEGER NOT NULL)")
        }

        override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) db.execSQL("CREATE TABLE album_ratings (album_key TEXT PRIMARY KEY NOT NULL, rating INTEGER NOT NULL CHECK(rating BETWEEN 1 AND 5), updated_at INTEGER NOT NULL)")
            if (oldVersion < 3) scrubLegacyDesktopUris(db)
        }

        private fun scrubLegacyDesktopUris(db:SQLiteDatabase){
            val remoteWithQuery="(uri LIKE 'http://%' OR uri LIKE 'https://%') AND (instr(uri,'?')>0 OR instr(uri,'#')>0)"
            val cleanUri="CASE WHEN instr(uri,'?')>0 THEN substr(uri,1,instr(uri,'?')-1) ELSE substr(uri,1,instr(uri,'#')-1) END"
            listOf("favorites","history").forEach { table ->
                db.execSQL("UPDATE OR IGNORE $table SET uri=$cleanUri WHERE $remoteWithQuery")
                db.execSQL("DELETE FROM $table WHERE $remoteWithQuery")
            }
            db.execSQL("UPDATE playlist_tracks SET uri=$cleanUri WHERE $remoteWithQuery")
        }
    }

    private companion object {
        val TRACK_COLUMNS = arrayOf("uri", "media_id", "title", "artist", "album_artist", "album", "track_number", "disc_number", "duration_ms", "year")
    }
}

private fun safeStoredTrackUri(uri:Uri):String = if(uri.scheme=="http"||uri.scheme=="https")
    uri.buildUpon().clearQuery().fragment(null).build().toString() else uri.toString()
