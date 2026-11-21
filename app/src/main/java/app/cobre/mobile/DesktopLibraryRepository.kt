package app.cobre.mobile

import android.net.Uri
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/** Reads the Desktop's existing LAN library endpoint and produces streamable Media3 items. */
data class DesktopLibrarySnapshot(val tracks: List<Track>, val catalogResponseMs: Long)

class DesktopLibraryRepository {
    suspend fun load(address: String, grantToken: String? = null): DesktopLibrarySnapshot = withContext(Dispatchers.IO) {
        if (!grantToken.isNullOrBlank()) return@withContext loadAuthorized(address, grantToken)
        loadLegacy(address)
    }

    suspend fun loadAuthorized(origin: String, grantToken: String): DesktopLibrarySnapshot = withContext(Dispatchers.IO) {
        val base = parseOrigin(origin)
        val startedAt = SystemClock.elapsedRealtime()
        val endpoint = Uri.parse(base).buildUpon().appendPath("v1").appendPath("library").build()
        val connection = (URL(endpoint.toString()).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 6_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-store")
            setRequestProperty("Authorization", "Bearer $grantToken")
        }
        val items = try {
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException(when(status) {
                    HttpURLConnection.HTTP_UNAUTHORIZED -> if (detail.contains("invalid_host_grant") || detail.contains("host_grant_revoked")) "A autorização do computador expirou ou foi removida. Reconecte pelo QR e aprove o novo pedido no Desktop." else "A permissão do computador expirou. Reconecte pelo QR e aprove o novo pedido no Desktop."
                    HttpURLConnection.HTTP_FORBIDDEN -> "Esta permissão não inclui a biblioteca. Reconecte pelo QR e aprove o novo pedido no Desktop."
                    HttpURLConnection.HTTP_UNAVAILABLE -> when {
                        detail.contains("cobre_id_unreachable") -> "O Desktop não alcança o Cobre Server. Confira se o Server está aberto e se o Desktop está conectado à mesma Conta Cobre ID."
                        detail.contains("cobre_id_unavailable") || detail.contains("authorization_unavailable") -> "O Desktop não conseguiu validar o acesso no Cobre Server. Abra Conta Cobre no Desktop e confirme que aparece conectado; depois reconecte pelo QR."
                        else -> "O serviço de autorização do Desktop está indisponível. Confira o Cobre Server e tente novamente."
                    }
                    else -> "Desktop respondeu HTTP $status ${detail.take(100)}".trim()
                })
            }
            val tracks = org.json.JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optJSONArray("tracks")
                ?: throw IllegalStateException("O Desktop retornou um catálogo inválido.")
            buildList(tracks.length()) {
                for (index in 0 until tracks.length()) {
                    val row = tracks.optJSONObject(index) ?: continue
                    val id = row.optInt("id", -1)
                    if (id < 0) continue
                    val stream = Uri.parse(base).buildUpon().appendPath("v1").appendPath("tracks")
                        .appendPath(id.toString()).appendPath("stream").build()
                    val artist = row.optString("artist").takeIf(String::isNotBlank) ?: "Artista desconhecido"
                    add(Track(-id.toLong() - 1, stream,
                        row.optString("title").takeIf(String::isNotBlank) ?: "Faixa sem título", artist, artist,
                        row.optString("album").takeIf(String::isNotBlank) ?: "Sem álbum",
                        row.optInt("trackNumber").coerceAtLeast(0), 0,
                        row.optLong("durationSeconds").coerceAtLeast(0) * 1_000L,
                        row.optInt("year").coerceAtLeast(0)))
                }
            }
        } finally { connection.disconnect() }
        DesktopLibrarySnapshot(items, (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0))
    }

    private suspend fun loadLegacy(address: String): DesktopLibrarySnapshot = withContext(Dispatchers.IO) {
        val connectionInfo = parseAddress(address)
        val startedAt = SystemClock.elapsedRealtime()
        val endpoint = Uri.parse(connectionInfo.origin).buildUpon()
            .appendPath("api").appendPath("lib").appendQueryParameter("k", connectionInfo.token).build()
        val connection = (URL(endpoint.toString()).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false // A chave LAN nunca deve seguir um redirecionamento para outro servidor.
            connectTimeout = 5_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-store")
        }
        val items = try {
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException(if (status == HttpURLConnection.HTTP_UNAUTHORIZED) "Chave de acesso recusada pelo Cobre Desktop." else "Desktop respondeu HTTP $status ${detail.take(100)}".trim())
            }
            val catalog = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            buildList(catalog.length()) {
                for (index in 0 until catalog.length()) {
                    val row = catalog.optJSONArray(index) ?: continue
                    val desktopId = row.optInt(0, -1)
                    if (desktopId < 0) continue
                    val title = row.optString(1).takeIf(String::isNotBlank) ?: "Faixa sem título"
                    val artist = row.optString(2).takeIf(String::isNotBlank) ?: "Artista desconhecido"
                    val album = row.optString(3).takeIf(String::isNotBlank) ?: "Sem álbum"
                    val stream = Uri.parse(connectionInfo.origin).buildUpon()
                        .appendPath("stream").appendPath(desktopId.toString())
                        .appendQueryParameter("k", connectionInfo.token).build()
                    add(Track(
                        id = -desktopId.toLong() - 1,
                        uri = stream,
                        title = title,
                        artist = artist,
                        albumArtist = artist,
                        album = album,
                        trackNumber = row.optInt(7).coerceAtLeast(0),
                        discNumber = 0,
                        durationMs = (row.optLong(6).coerceAtLeast(0) * 1_000L),
                        year = row.optInt(4).coerceAtLeast(0),
                    ))
                }
            }
        } finally {
            connection.disconnect()
        }
        DesktopLibrarySnapshot(items, (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0))
    }

    fun coverUri(track: Track): Uri? {
        if (!track.isDesktopTrack()) return null
        val stream = track.uri
        val path = stream.path.orEmpty().let {
            if (it.startsWith("/v1/tracks/") && it.endsWith("/stream")) it.removeSuffix("/stream") + "/cover"
            else it.replaceFirst("/stream/", "/cover/")
        }
        return stream.buildUpon().encodedPath(path).build()
    }

    private data class Address(val origin: String, val token: String)

    private fun parseAddress(raw: String): Address {
        val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull()
            ?: throw IllegalArgumentException("Cole o endereço exibido em Acesso remoto no Cobre Desktop.")
        val host = uri.host?.lowercase().orEmpty()
        val token = uri.getQueryParameter("k").orEmpty()
        if (uri.scheme != "http" || !isPrivateIpv4(host) || uri.userInfo != null || uri.fragment != null || !token.matches(Regex("[A-Fa-f0-9]{18,128}"))) {
            throw IllegalArgumentException("Use o endereço HTTP local completo mostrado pelo Cobre Desktop (normalmente termina em /?k=...).")
        }
        val authority = buildString { append(host); if (uri.port >= 0) append(":${uri.port}") }
        return Address("http://$authority", token)
    }

    private fun parseOrigin(raw: String): String {
        val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull()
            ?: throw IllegalArgumentException("Informe o endereço local do Cobre Desktop.")
        val host = uri.host?.lowercase().orEmpty()
        val legacyKeyOnly=uri.query?.let{query->query.startsWith("k=")&&query.substringAfter("k=").matches(Regex("[A-Fa-f0-9]{18,128}"))}?:true
        if (uri.scheme != "http" || !isPrivateIpv4(host) || uri.userInfo != null || !legacyKeyOnly || uri.fragment != null ||
            uri.path.orEmpty().isNotBlank() && uri.path != "/") {
            throw IllegalArgumentException("Use o endereço HTTP local do Desktop, sem a chave antiga (ex.: http://192.168.1.20:8787).")
        }
        val authority = buildString { append(host); if (uri.port >= 0) append(":${uri.port}") }
        return "http://$authority"
    }

    private fun isPrivateIpv4(host: String): Boolean {
        val octets = host.split('.').map { it.toIntOrNull() ?: return false }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        val first = octets[0]; val second = octets[1]
        return first == 10 || (first == 192 && second == 168) || (first == 172 && second in 16..31) ||
            (first == 169 && second == 254) || (first == 127)
    }
}

private fun Track.isDesktopTrack() = uri.scheme == "http" && (uri.path?.startsWith("/stream/") == true || uri.path?.startsWith("/v1/tracks/") == true)
