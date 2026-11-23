package app.cobre.mobile

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.Normalizer

data class LyricLine(val timeMs: Long?, val text: String)
data class TrackLyrics(val lines: List<LyricLine>, val source: String, val instrumental: Boolean = false) {
    val synchronized: Boolean get() = lines.any { it.timeMs != null }
    fun activeLine(positionMs: Long): Int = lines.indexOfLast { it.timeMs != null && it.timeMs <= positionMs }
}

class LyricsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("cobre-mobile-lyrics", Context.MODE_PRIVATE)

    suspend fun load(track: Track): TrackLyrics? = withContext(Dispatchers.IO) {
        readSiblingLyrics(track)?.let { return@withContext parse(it, "Arquivo local") }
        val key = digest("${norm(track.artist)}|${norm(track.title)}|${norm(track.album)}")
        val cached = prefs.getString(key, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        val cacheTtl = if (cached?.optBoolean("missing") == true) NEGATIVE_CACHE_TTL_MS else CACHE_TTL_MS
        if (cached != null && System.currentTimeMillis() - cached.optLong("time") < cacheTtl) {
            if (cached.optBoolean("missing")) return@withContext null
            return@withContext parse(cached.optString("lyrics"), cached.optString("source", "LRCLIB"))
        }

        val result = runCatching { fetch(track) }.getOrNull()
        val record = JSONObject().put("time", System.currentTimeMillis())
        if (result == null) {
            record.put("missing", true)
            prefs.edit().putString(key, record.toString()).apply()
            return@withContext null
        }
        record.put("lyrics", result.first).put("source", "LRCLIB")
        prefs.edit().putString(key, record.toString()).apply()
        parse(result.first, "LRCLIB")
    }

    private fun readSiblingLyrics(track: Track): String? {
        if (track.uri.scheme != "file") return null
        val audio = track.uri.path?.let(::File) ?: return null
        val base = audio.name.substringBeforeLast('.', audio.name)
        for (extension in listOf("lrc", "txt")) {
            val file = File(audio.parentFile, "$base.$extension")
            if (file.isFile && file.length() in 1..MAX_LOCAL_LYRICS_BYTES) {
                return runCatching { file.readText(Charsets.UTF_8) }.getOrNull()?.takeIf(String::isNotBlank)
            }
        }
        return null
    }

    private fun fetch(track: Track): Pair<String, Boolean>? {
        if (track.title.isBlank() || track.artist.isBlank()) return null
        val params = linkedMapOf("artist_name" to track.artist, "track_name" to track.title)
        if (track.album.isNotBlank()) params["album_name"] = track.album
        if (track.durationMs > 0) params["duration"] = ((track.durationMs + 500) / 1_000).toString()
        val direct = requestJson("https://lrclib.net/api/get?${query(params)}") as? JSONObject
        bestLyrics(direct)?.let { return it }

        val results = requestJson("https://lrclib.net/api/search?${query(mapOf("artist_name" to track.artist, "track_name" to track.title))}") as? JSONArray ?: return null
        val matching = (0 until results.length()).mapNotNull { results.optJSONObject(it) }
            .filter { norm(it.optString("trackName")) == norm(track.title) && norm(it.optString("artistName")) == norm(track.artist) }
            .minByOrNull { candidate ->
                val duration = candidate.optDouble("duration", -1.0)
                if (track.durationMs > 0 && duration >= 0) kotlin.math.abs(duration - track.durationMs / 1_000.0) else 0.0
            }
        return bestLyrics(matching)
    }

    private fun bestLyrics(json: JSONObject?): Pair<String, Boolean>? {
        if (json == null) return null
        if (json.optBoolean("instrumental")) return "[Instrumental]" to true
        val text = json.optString("syncedLyrics").takeIf(String::isNotBlank)
            ?: json.optString("plainLyrics").takeIf(String::isNotBlank)
        return text?.let { it to false }
    }

    private fun requestJson(address: String): Any? {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 10_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "CobreMobile/0.20 (https://github.com/bruncosta077-boop/Cobre-Mobile)")
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_RESPONSE_BYTES) return null
                    out.write(buffer, 0, count)
                }
                out.toString(Charsets.UTF_8.name())
            }
            if (body.trimStart().startsWith("[")) JSONArray(body) else JSONObject(body)
        } finally { connection.disconnect() }
    }

    private fun parse(text: String, source: String): TrackLyrics? {
        if (text == "[Instrumental]") return TrackLyrics(emptyList(), source, instrumental = true)
        val timed = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
        val lines = buildList {
            for (raw in text.lineSequence()) {
                val timestamps = timed.findAll(raw).toList()
                val content = raw.replace(timed, "").replace(Regex("\\[[a-z]{2,3}:[^]]*]", RegexOption.IGNORE_CASE), "").trim()
                if (content.isBlank()) continue
                if (timestamps.isEmpty()) {
                    add(LyricLine(null, content))
                    continue
                }
                for (match in timestamps) {
                    val minutes = match.groupValues[1].toLongOrNull() ?: continue
                    val seconds = match.groupValues[2].toLongOrNull() ?: continue
                    if (seconds > 59) continue
                    val fraction = match.groupValues[3]
                    val millis = when (fraction.length) { 1 -> (fraction.toLongOrNull() ?: 0) * 100; 2 -> (fraction.toLongOrNull() ?: 0) * 10; else -> fraction.toLongOrNull() ?: 0 }
                    add(LyricLine((minutes * 60 + seconds) * 1_000 + millis.coerceIn(0, 999), content))
                }
            }
        }.sortedWith(compareBy({ it.timeMs ?: Long.MAX_VALUE }))
        return lines.takeIf { it.isNotEmpty() }?.let { TrackLyrics(it, source) }
    }

    private fun query(values: Map<String, String>) = values.entries.joinToString("&") {
        "${URLEncoder.encode(it.key, Charsets.UTF_8.name())}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}"
    }

    private fun norm(value: String) = Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9]+"), "")

    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val CACHE_TTL_MS = 12 * 60 * 60 * 1_000L
        const val NEGATIVE_CACHE_TTL_MS = 30 * 60 * 1_000L
        const val MAX_RESPONSE_BYTES = 1_000_000
        const val MAX_LOCAL_LYRICS_BYTES = 512_000L
    }
}
