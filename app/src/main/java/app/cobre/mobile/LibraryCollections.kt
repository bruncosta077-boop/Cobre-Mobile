package app.cobre.mobile

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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

    fun isFavorite(track: Track) = favorites.any { it.uri == track.uri }

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

    private fun trackValues(track: Track) = ContentValues().apply {
        put("uri", track.uri.toString()); put("media_id", track.id); put("title", track.title)
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

    private class CollectionsDatabase(context: Context) : SQLiteOpenHelper(context, "cobre-mobile-library.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            val trackFields = "uri TEXT NOT NULL, media_id INTEGER NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, album_artist TEXT NOT NULL, album TEXT NOT NULL, track_number INTEGER NOT NULL, disc_number INTEGER NOT NULL, duration_ms INTEGER NOT NULL, year INTEGER NOT NULL"
            db.execSQL("CREATE TABLE favorites ($trackFields, saved_at INTEGER NOT NULL, PRIMARY KEY(uri))")
            db.execSQL("CREATE TABLE history ($trackFields, played_at INTEGER NOT NULL, PRIMARY KEY(uri))")
            db.execSQL("CREATE TABLE playlists (_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL COLLATE NOCASE UNIQUE, created_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE playlist_tracks (playlist_id INTEGER NOT NULL REFERENCES playlists(_id) ON DELETE CASCADE, position INTEGER NOT NULL, $trackFields, PRIMARY KEY(playlist_id, position))")
            db.execSQL("CREATE INDEX ix_playlist_order ON playlist_tracks(playlist_id, position)")
        }

        override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // No schema migrations are needed in version 1. Add explicit migrations before bumping the DB version.
        }
    }

    private companion object {
        val TRACK_COLUMNS = arrayOf("uri", "media_id", "title", "artist", "album_artist", "album", "track_number", "disc_number", "duration_ms", "year")
    }
}
