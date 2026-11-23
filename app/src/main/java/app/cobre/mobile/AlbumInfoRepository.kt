package app.cobre.mobile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer

data class AlbumEdition(
    val id: String,
    val title: String,
    val creditedArtist: String,
    val date: String,
    val country: String,
    val format: String,
    val trackCount: Int,
)

data class AlbumInfoTrack(val position: String, val title: String, val artists: String)
data class AlbumBookletImage(val url: String, val thumbnail: String, val type: String, val comment: String)
data class AlbumInfo(val edition: AlbumEdition, val labels: List<String>, val tracks: List<AlbumInfoTrack>, val images: List<AlbumBookletImage>)

/** Credits are only shown after an exact artist/title match and an explicit release edition choice. */
class AlbumInfoRepository {
    suspend fun findEditions(album: Album): List<AlbumEdition> = withContext(Dispatchers.IO) {
        val artist = album.tracks.firstOrNull()?.albumArtist?.takeIf(String::isNotBlank) ?: album.artist
        if (artist.isBlank() || album.title.isBlank()) return@withContext emptyList()
        MusicBrainzRateLimiter.waitForSlot()
        val query = "release:\"${album.title.replace("\"", "") }\" AND artist:\"${artist.replace("\"", "") }\""
        val result = requestJson("https://musicbrainz.org/ws/2/release/?query=${encode(query)}&limit=15&fmt=json")
        val rows = result?.optJSONArray("releases") ?: return@withContext emptyList()
        buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                if (norm(row.optString("title")) != norm(album.title)) continue
                val credit = creditedArtist(row.optJSONArray("artist-credit"))
                if (norm(credit) != norm(artist)) continue
                val media = row.optJSONArray("media")
                var count = 0
                var formats = mutableListOf<String>()
                if (media != null) for (disc in 0 until media.length()) {
                    val medium = media.optJSONObject(disc) ?: continue
                    count += medium.optInt("track-count", medium.optJSONArray("tracks")?.length() ?: 0)
                    medium.optString("format").takeIf(String::isNotBlank)?.let(formats::add)
                }
                add(AlbumEdition(row.optString("id"), row.optString("title"), credit,
                    row.optString("date"), row.optString("country"), formats.distinct().joinToString(" / "), count))
            }
        }.sortedWith(compareBy<AlbumEdition>({ if (album.year > 0 && it.date.startsWith(album.year.toString())) 0 else 1 }, { kotlin.math.abs((it.trackCount.takeIf { count -> count > 0 } ?: album.tracks.size) - album.tracks.size) }, { it.date }))
            .take(8)
    }

    suspend fun load(edition: AlbumEdition): AlbumInfo = withContext(Dispatchers.IO) {
        MusicBrainzRateLimiter.waitForSlot()
        val detail = requestJson("https://musicbrainz.org/ws/2/release/${encode(edition.id)}?inc=artist-credits+labels+recordings&fmt=json")
            ?: throw IllegalStateException("MusicBrainz não retornou os créditos desta edição.")
        val labels = mutableListOf<String>()
        detail.optJSONArray("label-info")?.let { info -> for (i in 0 until info.length()) {
            val name = info.optJSONObject(i)?.optJSONObject("label")?.optString("name").orEmpty()
            if (name.isNotBlank()) labels += name
        } }
        val tracks = mutableListOf<AlbumInfoTrack>()
        detail.optJSONArray("media")?.let { media -> for (disc in 0 until media.length()) {
            val rows = media.optJSONObject(disc)?.optJSONArray("tracks") ?: continue
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val recording = row.optJSONObject("recording")
                val title = recording?.optString("title")?.takeIf(String::isNotBlank) ?: row.optString("title")
                val artists = creditedArtist(recording?.optJSONArray("artist-credit")).ifBlank { "Crédito específico não informado" }
                tracks += AlbumInfoTrack(row.optString("number").ifBlank { (i + 1).toString() }, title, artists)
            }
        } }
        val images = requestJson("https://coverartarchive.org/release/${encode(edition.id)}")?.optJSONArray("images")
        val artwork = buildList {
            if (images != null) for (i in 0 until images.length()) {
                val item = images.optJSONObject(i) ?: continue
                if (!item.optBoolean("approved", false)) continue
                val types = item.optJSONArray("types")
                val type = if (types == null) "Imagem" else (0 until types.length()).mapNotNull { types.optString(it).takeIf(String::isNotBlank) }.joinToString(" · ")
                val lower = type.lowercase()
                if (!("booklet" in lower || "back" in lower || "medium" in lower || "tray" in lower || "inside" in lower)) continue
                val thumbs = item.optJSONObject("thumbnails")
                val url = item.optString("image").takeIf(String::isNotBlank) ?: continue
                val thumbnail = thumbs?.optString("500")?.takeIf(String::isNotBlank) ?: url
                add(AlbumBookletImage(url, thumbnail, type, item.optString("comment")))
            }
        }.take(24)
        AlbumInfo(edition, labels.distinct(), tracks, artwork)
    }

    suspend fun loadImage(url: String): Bitmap? = withContext(Dispatchers.IO) {
        val bytes = requestBytes(url, "image/*", MAX_IMAGE_BYTES) ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > MAX_PIXELS) return@withContext null
        val sample = (maxOf(bounds.outWidth, bounds.outHeight) / MAX_IMAGE_DIMENSION).coerceAtLeast(1)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun requestJson(address: String): JSONObject? {
        val bytes = requestBytes(address, "application/json", MAX_JSON_BYTES) ?: return null
        return runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull()
    }

    private fun requestBytes(address: String, accept: String, maxBytes: Int): ByteArray? {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 7_000; readTimeout = 12_000; instanceFollowRedirects = true
            requestMethod = "GET"; setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "CobreMobile/0.23 (https://github.com/bruncosta077-boop/Cobre-Mobile)")
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val type = connection.contentType.orEmpty().substringBefore(';').trim().lowercase()
            if (accept.startsWith("image") && type.isNotBlank() && !type.startsWith("image/")) return null
            if (connection.contentLengthLong > maxBytes) return null
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream(); val buffer = ByteArray(8_192); var total = 0
                while (true) {
                    val read = input.read(buffer); if (read < 0) break
                    total += read; if (total > maxBytes) return null
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } finally { connection.disconnect() }
    }

    private fun creditedArtist(credits: org.json.JSONArray?): String = credits?.let { rows -> buildString {
        for (i in 0 until rows.length()) {
            val credit = rows.optJSONObject(i) ?: continue
            append(credit.optString("name").ifBlank { credit.optJSONObject("artist")?.optString("name").orEmpty() })
            append(credit.optString("joinphrase"))
        }
    }.trim() }.orEmpty()

    private fun norm(value: String) = Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9]+"), "")
    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        const val MAX_JSON_BYTES = 2 * 1024 * 1024
        const val MAX_IMAGE_BYTES = 20 * 1024 * 1024
        const val MAX_IMAGE_DIMENSION = 1_600
        const val MAX_PIXELS = 36_000_000L
    }
}

internal object MusicBrainzRateLimiter {
    private val lock = Any()
    private var lastRequestAt = 0L
    fun waitForSlot() = synchronized(lock) {
        val waitMs = 1_100L - (System.currentTimeMillis() - lastRequestAt)
        if (waitMs > 0) Thread.sleep(waitMs)
        lastRequestAt = System.currentTimeMillis()
    }
}
