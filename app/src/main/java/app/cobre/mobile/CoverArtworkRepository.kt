package app.cobre.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.Normalizer

data class CoverCandidate(val source: String, val bytes: ByteArray)

class CoverArtworkRepository(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("cobre-mobile-cover-art", Context.MODE_PRIVATE)

    fun coverFile(track: Track): File? {
        val key = albumKey(track.albumArtist.ifBlank { track.artist }, track.album)
        return prefs.getString(key, null)?.let(::File)?.takeIf { it.isFile && it.length() > 0 }
    }

    suspend fun search(artist: String, album: String): List<CoverCandidate> = withContext(Dispatchers.IO) {
        if (artist.isBlank() || album.isBlank()) return@withContext emptyList()
        val found = linkedMapOf<String, CoverCandidate>()
        val artistNorm = norm(artist); val albumNorm = norm(album)

        val itunes = runCatching {
            requestJson("https://itunes.apple.com/search?entity=album&limit=30&country=us&term=" + encode("$artist $album")) as? JSONObject
        }.getOrNull()
        val results = itunes?.optJSONArray("results")
        if (results != null) for (i in 0 until results.length()) {
            val result = results.optJSONObject(i) ?: continue
            if (norm(result.optString("collectionName")) != albumNorm || norm(result.optString("artistName")) != artistNorm) continue
            val art = result.optString("artworkUrl100").takeIf(String::isNotBlank) ?: continue
            val full = art.replace(Regex("/\\d+x\\d+bb\\."), "/1000x1000bb.")
            addCandidate(found, full, "Apple Music")
            if (found.size >= MAX_CANDIDATES) break
        }

        if (found.size < MAX_CANDIDATES) runCatching {
            val query = "release:\"${album.replace("\"", "")}\" AND artist:\"${artist.replace("\"", "")}\""
            val mb = requestMusicBrainz("https://musicbrainz.org/ws/2/release/?query=${encode(query)}&limit=8&fmt=json") as? JSONObject
            val releases = mb?.optJSONArray("releases") ?: return@runCatching
            val ids = buildList {
                for (i in 0 until releases.length()) {
                    val release = releases.optJSONObject(i) ?: continue
                    if (norm(release.optString("title")) != albumNorm) continue
                    val credits = release.optJSONArray("artist-credit") ?: continue
                    val creditMatches = (0 until credits.length()).any { index ->
                        val credit = credits.optJSONObject(index) ?: return@any false
                        val name = credit.optString("name").ifBlank { credit.optJSONObject("artist")?.optString("name").orEmpty() }
                        norm(name) == artistNorm
                    }
                    if (creditMatches) release.optString("id").takeIf(String::isNotBlank)?.let(::add)
                }
            }.take(8)
            ids.forEach { id ->
                if (found.size < MAX_CANDIDATES) addCandidate(found, "https://coverartarchive.org/release/$id/front-500", "Cover Art Archive")
            }
        }
        found.values.toList()
    }

    suspend fun save(track: Track, candidate: CoverCandidate): Boolean = withContext(Dispatchers.IO) {
        val bitmap = decodeCover(candidate.bytes) ?: return@withContext false
        val file = File(app.filesDir, "album-art/${albumKey(track.albumArtist.ifBlank { track.artist }, track.album)}.jpg")
        if (!file.parentFile?.exists().let { it == true || file.parentFile?.mkdirs() == true }) return@withContext false
        val saved = runCatching { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) } }.getOrDefault(false)
        bitmap.recycle()
        if (saved) {
            prefs.edit().putString(albumKey(track.albumArtist.ifBlank { track.artist }, track.album), file.absolutePath).apply()
            CoverArtworkEvents.changed()
        }
        saved
    }

    private fun addCandidate(into: MutableMap<String, CoverCandidate>, address: String, source: String) {
        if (into.size >= MAX_CANDIDATES || into.containsKey(address)) return
        val bytes = runCatching { requestBytes(address) }.getOrNull() ?: return
        val normalized = normalizeCover(bytes) ?: return
        val hash = MessageDigest.getInstance("SHA-256").digest(normalized).joinToString("") { "%02x".format(it) }
        if (into.values.any { MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(it.bytes), hex(hash)) }) return
        into[address] = CoverCandidate(source, normalized)
    }

    private fun requestJson(address: String): Any? {
        val bytes = requestBytes(address, "application/json") ?: return null
        val text = String(bytes, Charsets.UTF_8)
        return if (text.trimStart().startsWith("[")) org.json.JSONArray(text) else JSONObject(text)
    }

    private fun requestMusicBrainz(address: String): Any? {
        synchronized(musicBrainzLock) {
            val waitMs = MUSICBRAINZ_INTERVAL_MS - (System.currentTimeMillis() - lastMusicBrainzRequestAt)
            if (waitMs > 0) Thread.sleep(waitMs)
            lastMusicBrainzRequestAt = System.currentTimeMillis()
        }
        return requestJson(address)
    }

    private fun requestBytes(address: String, accept: String = "image/*"): ByteArray? {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 6_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "CobreMobile/0.20 (https://github.com/bruncosta077-boop/Cobre-Mobile)")
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val contentType = connection.contentType.orEmpty().substringBefore(';').trim().lowercase()
            if (accept.startsWith("image") && contentType.isNotBlank() && !contentType.startsWith("image/")) return null
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_DOWNLOAD_BYTES) return null
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally { connection.disconnect() }
    }

    private fun normalizeCover(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth < MIN_DIMENSION || bounds.outHeight < MIN_DIMENSION) return null
        if (kotlin.math.abs(bounds.outWidth - bounds.outHeight).toFloat() / maxOf(bounds.outWidth, bounds.outHeight) > .12f) return null
        val sample = (maxOf(bounds.outWidth, bounds.outHeight) / MAX_DIMENSION).coerceAtLeast(1)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scaled = if (bitmap.width > MAX_DIMENSION || bitmap.height > MAX_DIMENSION) {
            Bitmap.createScaledBitmap(bitmap, MAX_DIMENSION, MAX_DIMENSION, true).also { if (it !== bitmap) bitmap.recycle() }
        } else bitmap
        return ByteArrayOutputStream().use { out ->
            val ok = scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
            scaled.recycle()
            if (ok) out.toByteArray() else null
        }
    }

    private fun decodeCover(bytes: ByteArray): Bitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    private fun albumKey(artist: String, album: String) = digest("${norm(artist)}|${norm(album)}")
    private fun norm(value: String) = Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9]+"), "")
    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private companion object {
        val musicBrainzLock = Any()
        var lastMusicBrainzRequestAt = 0L
        const val MUSICBRAINZ_INTERVAL_MS = 1_100L
        const val MAX_CANDIDATES = 6
        const val MAX_DOWNLOAD_BYTES = 15 * 1024 * 1024
        const val MAX_DIMENSION = 1_200
        const val MIN_DIMENSION = 150
    }
}

internal object CoverArtworkEvents {
    private val _revision = kotlinx.coroutines.flow.MutableStateFlow(0)
    val revision: kotlinx.coroutines.flow.StateFlow<Int> = _revision
    fun changed() { _revision.value = _revision.value + 1 }
}
