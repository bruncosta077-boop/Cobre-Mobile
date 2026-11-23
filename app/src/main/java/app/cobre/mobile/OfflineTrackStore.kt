package app.cobre.mobile

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class OfflineStorageSummary(val trackCount:Int,val usedBytes:Long,val limitBytes:Long)

/** Private, app-managed offline copies of tracks streamed directly from a paired Desktop. */
class OfflineTrackStore(context: Context) {
    private val appContext = context.applicationContext
    private val db = OfflineDatabase(appContext)
    private val operations = Mutex()
    var tracks by androidx.compose.runtime.mutableStateOf(emptyList<Track>())
        private set

    suspend fun load(): OfflineStorageSummary = operations.withLock { withContext(Dispatchers.IO) {
        tracks = readAll()
        storageSummaryOnIo()
    }}

    suspend fun contains(track: Track): Boolean = withContext(Dispatchers.IO) {
        db.readableDatabase
            .query("offline_tracks", arrayOf("source_key"), "source_key=?", arrayOf(track.libraryIdentity()), null, null, null)
            .use { it.moveToFirst() }
    }

    suspend fun storageSummary(): OfflineStorageSummary = withContext(Dispatchers.IO) { storageSummaryOnIo() }

    private fun storageSummaryOnIo(): OfflineStorageSummary = db.readableDatabase.rawQuery("SELECT COUNT(*), COALESCE(SUM(byte_size),0) FROM offline_tracks",null).use{cursor->
        if(cursor.moveToFirst())OfflineStorageSummary(cursor.getInt(0),cursor.getLong(1),MAX_LIBRARY_BYTES)else OfflineStorageSummary(0,0,MAX_LIBRARY_BYTES)
    }

    suspend fun clearAll():Int=operations.withLock { withContext(Dispatchers.IO){
        val paths=db.readableDatabase.query("offline_tracks",arrayOf("file_path"),null,null,null,null,null).use{cursor->buildList(cursor.count){while(cursor.moveToNext())add(cursor.getString(0))}}
        db.writableDatabase.delete("offline_tracks",null,null)
        paths.forEach{path->ownedAudioFile(path)?.delete()}
        val directory=offlineDirectory()
        directory.listFiles()?.filter{it.isFile&&it.name.matches(PART_FILE_NAME)&&runCatching{it.canonicalFile.parentFile==directory}.getOrDefault(false)}?.forEach{it.delete()}
        tracks=readAll()
        paths.size
    }}

    suspend fun download(track: Track, grantToken: String, expectedDesktopUrl: String, onProgress: (Int) -> Unit = {}): Track = operations.withLock { withContext(Dispatchers.IO) {
        val source = track.sourceUri ?: track.uri
        val uri = validateStreamUri(source, expectedDesktopUrl)
        require(grantToken.isNotBlank()) { "A autorização do computador expirou. Conecte novamente pelo QR." }

        val directory = File(appContext.filesDir, "offline-audio").apply { if (!exists() && !mkdirs()) error("Não foi possível criar a pasta de músicas offline.") }
        val sourceKey = track.libraryIdentity()
        val key = sha256(sourceKey)
        val existing = db.readableDatabase.query("offline_tracks", arrayOf("file_path"), "source_key=?", arrayOf(sourceKey), null, null, null)
            .use { if (it.moveToFirst()) it.getString(0) else null }
        val existingFile=existing?.let(::ownedAudioFile)
        if (existingFile != null && existingFile.isFile && existingFile.length() > 0) {
            db.writableDatabase.update("offline_tracks", ContentValues().apply { put("source_uri", uri.toString()) }, "source_key=?", arrayOf(sourceKey))
            tracks = readAll()
            return@withContext track.copy(id = -key.take(15).toLong(16), uri = Uri.fromFile(existingFile), sourceUri = uri)
        }
        if(existing!=null)db.writableDatabase.delete("offline_tracks","source_key=?",arrayOf(sourceKey))

        val diskUsed = db.readableDatabase.rawQuery("SELECT COALESCE(SUM(byte_size),0) FROM offline_tracks", null).use { if (it.moveToFirst()) it.getLong(0) else 0L }
        if (diskUsed >= MAX_LIBRARY_BYTES) error("O limite de 2 GB para músicas offline foi atingido. Remova algumas faixas e tente novamente.")
        if (directory.usableSpace < MIN_FREE_BYTES) error("Há pouco espaço livre no celular para baixar esta faixa.")

        val connection = (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 8_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("Accept", "audio/*, application/octet-stream")
            setRequestProperty("Cache-Control", "no-store")
            setRequestProperty("Authorization", "Bearer $grantToken")
        }
        val part = File(directory, "$key.part")
        try {
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val message = when (status) {
                    401 -> "A autorização do computador expirou ou foi removida. Conecte novamente pelo QR."
                    403 -> "A permissão atual não permite baixar áudio do computador."
                    503 -> "O Desktop não conseguiu confirmar a autorização no Cobre Server. Confira se o Server está ativo."
                    else -> "O Desktop respondeu HTTP $status${detail.take(80).takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()}"
                }
                error(message)
            }
            val contentType = connection.contentType.orEmpty().substringBefore(';').trim().lowercase()
            if (contentType.isNotEmpty() && !contentType.startsWith("audio/") && contentType != "application/octet-stream") {
                error("O Desktop retornou um arquivo que não parece ser áudio.")
            }
            val expected = connection.contentLengthLong
            if (expected > MAX_TRACK_BYTES) error("Esta faixa excede o limite de 512 MB para um download offline.")
            val extension = when (contentType) {
                "audio/mpeg" -> "mp3"
                "audio/flac", "audio/x-flac" -> "flac"
                "audio/wav", "audio/wave", "audio/x-wav" -> "wav"
                "audio/ogg", "audio/opus" -> "ogg"
                "audio/mp4", "audio/x-m4a" -> "m4a"
                "audio/aac" -> "aac"
                else -> "audio"
            }
            val destination = File(directory, "$key.$extension")
            var total = 0L
            connection.inputStream.use { input ->
                part.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_TRACK_BYTES || diskUsed + total > MAX_LIBRARY_BYTES) {
                            error("O limite de espaço para músicas offline foi atingido.")
                        }
                        output.write(buffer, 0, count)
                        if (expected > 0) onProgress(((total * 100) / expected).toInt().coerceIn(0, 99))
                    }
                    output.flush()
                }
            }
            if (total == 0L || (expected > 0 && total != expected)) error("O download terminou incompleto. Tente novamente.")
            destination.delete()
            if (!part.renameTo(destination)) error("Não foi possível salvar o download no celular.")
            try {
                val values = ContentValues().apply {
                    put("source_key", sourceKey); put("source_uri", uri.toString()); put("file_path", destination.absolutePath); put("byte_size", total)
                    put("media_id", track.id); put("title", track.title); put("artist", track.artist)
                    put("album_artist", track.albumArtist); put("album", track.album)
                    put("track_number", track.trackNumber); put("disc_number", track.discNumber)
                    put("duration_ms", track.durationMs); put("year", track.year); put("saved_at", System.currentTimeMillis())
                }
                check(db.writableDatabase.insertWithOnConflict("offline_tracks", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) {
                    "Não foi possível registrar o download offline no celular."
                }
            } catch (error: Exception) {
                destination.delete()
                throw error
            }
            runCatching { existing?.let(::File)?.takeIf { it != destination }?.delete() }
            tracks = readAll()
            onProgress(100)
            return@withContext track.copy(id = -key.take(15).toLong(16), uri = Uri.fromFile(destination), sourceUri = uri)
        } finally {
            connection.disconnect()
            part.delete()
        }
    }}

    suspend fun remove(track: Track) = operations.withLock { withContext(Dispatchers.IO) {
        val key = track.libraryIdentity()
        val path = db.readableDatabase.query("offline_tracks", arrayOf("file_path"), "source_key=?", arrayOf(key), null, null, null)
            .use { if (it.moveToFirst()) it.getString(0) else null }
        db.writableDatabase.delete("offline_tracks", "source_key=?", arrayOf(key))
        path?.let(::ownedAudioFile)?.delete()
        tracks = readAll()
    }}

    private fun readAll(): List<Track> {
        val columns = arrayOf("source_uri", "file_path", "media_id", "title", "artist", "album_artist", "album", "track_number", "disc_number", "duration_ms", "year")
        val missingFiles = mutableListOf<String>()
        val result = db.readableDatabase.query("offline_tracks", columns, null, null, null, null, "saved_at DESC").use { cursor ->
            buildList(cursor.count) {
                while (cursor.moveToNext()) {
                    val storedPath=cursor.getString(1)
                    val file = ownedAudioFile(storedPath)
                    val source = Uri.parse(cursor.getString(0))
                    if (file==null || !file.isFile || file.length() == 0L) {
                        missingFiles += storedPath
                        continue
                    }
                    add(Track(cursor.getLong(2), Uri.fromFile(file), cursor.getString(3), cursor.getString(4), cursor.getString(5),
                        cursor.getString(6), cursor.getInt(7), cursor.getInt(8), cursor.getLong(9), cursor.getInt(10), source))
                }
            }
        }
        missingFiles.forEach { db.writableDatabase.delete("offline_tracks", "file_path=?", arrayOf(it)) }
        return result
    }

    private fun offlineDirectory()=File(appContext.filesDir,"offline-audio").canonicalFile

    private fun ownedAudioFile(path:String):File?=runCatching{
        val file=File(path).canonicalFile
        file.takeIf{it.parentFile==offlineDirectory()&&it.name.matches(AUDIO_FILE_NAME)}
    }.getOrNull()

    private fun validateStreamUri(uri: Uri, expectedDesktopUrl: String): Uri {
        val host = uri.host.orEmpty().split('.').map { it.toIntOrNull() ?: return invalidStream() }
        val private = host.size == 4 && host.all { it in 0..255 } &&
            (host[0] == 10 || (host[0] == 192 && host[1] == 168) || (host[0] == 172 && host[1] in 16..31) || (host[0] == 169 && host[1] == 254) || host[0] == 127)
        val expected = runCatching { Uri.parse(expectedDesktopUrl.trim()) }.getOrNull() ?: return invalidStream()
        val expectedHost = expected.host.orEmpty().lowercase()
        val port = if (uri.port == -1) 80 else uri.port
        val expectedPort = if (expected.port == -1) 80 else expected.port
        if (uri.scheme != "http" || !private || uri.userInfo != null || uri.query != null || uri.fragment != null ||
            !uri.path.orEmpty().matches(Regex("/v1/tracks/\\d+/stream")) || expected.scheme != "http" || expected.userInfo != null ||
            expectedHost != uri.host.orEmpty().lowercase() || expectedPort != port) return invalidStream()
        return uri
    }

    private fun invalidStream(): Nothing = throw IllegalArgumentException("Só é possível baixar faixas pela conexão Cobre ID segura com o Desktop.")
    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private class OfflineDatabase(context: Context) : SQLiteOpenHelper(context, "cobre-offline-tracks.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE offline_tracks (
                source_key TEXT NOT NULL PRIMARY KEY, source_uri TEXT NOT NULL, file_path TEXT NOT NULL UNIQUE, byte_size INTEGER NOT NULL,
                media_id INTEGER NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, album_artist TEXT NOT NULL,
                album TEXT NOT NULL, track_number INTEGER NOT NULL, disc_number INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL, year INTEGER NOT NULL, saved_at INTEGER NOT NULL)""")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        val AUDIO_FILE_NAME=Regex("[a-f0-9]{64}\\.(mp3|flac|wav|ogg|m4a|aac|audio)")
        val PART_FILE_NAME=Regex("[a-f0-9]{64}\\.part")
        const val MAX_TRACK_BYTES = 512L * 1024 * 1024
        const val MAX_LIBRARY_BYTES = 2L * 1024 * 1024 * 1024
        const val MIN_FREE_BYTES = 64L * 1024 * 1024
    }
}
