package app.cobre.mobile

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Client for the currently implemented Cobre Server account, presence and device endpoints. */
class CobreIdRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("cobre-id", Context.MODE_PRIVATE)
    private var accessToken: String? = null

    val serverUrl: String get() = prefs.getString("server_url", "").orEmpty()
    val savedEmail: String get() = prefs.getString("email", "").orEmpty()
    val hasSavedSession: Boolean get() = !readRefreshToken().isNullOrBlank()

    fun saveServerUrl(value: String) {
        prefs.edit().putString("server_url", normalizeUrl(value)).apply()
    }

    suspend fun checkServer(): Boolean = withContext(Dispatchers.IO) {
        requestBlocking("/health", "GET", null, authorize = false).optBoolean("ok")
    }

    suspend fun login(email: String, password: String, createAccount: Boolean) = withContext(Dispatchers.IO) {
        val device = JSONObject().put("name", android.os.Build.MODEL ?: "Android").put("type", "mobile")
        val body = JSONObject().put("email", email.trim()).put("password", password).put("device", device)
        acceptSession(requestBlocking(if (createAccount) "/v1/register" else "/v1/login", "POST", body, authorize = false))
        prefs.edit().putString("email", email.trim()).apply()
    }

    suspend fun restoreSession() = withContext(Dispatchers.IO) { refreshBlocking() }

    suspend fun loadDevices(): List<CobreDevice> = withContext(Dispatchers.IO) {
        val result = requestBlocking("/v1/devices", "GET", null)
        val list: JSONArray = result.optJSONArray("devices") ?: JSONArray()
        (0 until list.length()).mapNotNull { i ->
            val item = list.optJSONObject(i) ?: return@mapNotNull null
            CobreDevice(
                id = item.optString("id"),
                name = item.optString("name", "Dispositivo"),
                type = item.optString("type", ""),
                online = item.optBoolean("online"),
                current = item.optBoolean("current"),
                lastSeen = if (item.isNull("lastSeen")) 0L else item.optLong("lastSeen"),
            )
        }
    }

    suspend fun sendPresence() = withContext(Dispatchers.IO) {
        requestBlocking(
            "/v1/presence", "POST",
            JSONObject().put("meta", JSONObject().put("platform", "Android").put("appVersion", "0.3.0")),
        )
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        try { requestBlocking("/v1/logout", "POST", JSONObject()) } finally {
            accessToken = null
            prefs.edit().remove("refresh_token").remove("email").apply()
        }
    }

    private fun acceptSession(json: JSONObject) {
        accessToken = json.getString("accessToken")
        saveRefreshToken(json.getString("refreshToken"))
    }

    private fun refreshBlocking() {
        val refresh = readRefreshToken() ?: throw CobreIdException("Entre na sua conta Cobre ID para conectar este aparelho.")
        try {
            val result = requestBlocking("/v1/refresh", "POST", JSONObject().put("refreshToken", refresh), authorize = false, allowRefresh = false)
            acceptSession(result)
        } catch (error: Exception) {
            accessToken = null
            if (error is CobreIdException && error.statusCode == 401) {
                prefs.edit().remove("refresh_token").remove("email").apply()
            }
            throw error
        }
    }

    private fun requestBlocking(
        path: String,
        method: String,
        body: JSONObject?,
        authorize: Boolean = true,
        allowRefresh: Boolean = true,
    ): JSONObject {
        val base = normalizeUrl(serverUrl)
        if (base.isBlank()) throw CobreIdException("Informe o endereço do Cobre Server.")
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 6_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-store")
            if (authorize) accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val response = if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
            if (status == 401 && authorize && allowRefresh && !path.endsWith("/refresh") && hasSavedSession) {
                refreshBlocking()
                return requestBlocking(path, method, body, authorize, allowRefresh = false)
            }
            if (status !in 200..299) throw CobreIdException(response.optString("message", "Falha de conexão com o servidor."), status)
            return response
        } catch (error: CobreIdException) {
            throw error
        } catch (error: Exception) {
            throw CobreIdException(error.message?.takeIf(String::isNotBlank) ?: "Não foi possível alcançar o servidor.")
        } finally {
            connection.disconnect()
        }
    }

    private fun saveRefreshToken(token: String) {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit().putString("refresh_token", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    private fun readRefreshToken(): String? = runCatching {
        val stored = prefs.getString("refresh_token", null) ?: return null
        val packed = Base64.decode(stored, Base64.NO_WRAP)
        val iv = packed.copyOfRange(0, 12)
        val payload = packed.copyOfRange(12, packed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(payload), Charsets.UTF_8)
    }.getOrNull()

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "cobre_id_refresh_v1"
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun normalizeUrl(raw: String): String {
        val value = raw.trim().trimEnd('/')
        if (value.isBlank()) return ""
        val uri = runCatching { URI(value) }.getOrElse { throw CobreIdException("Endereço do servidor inválido.") }
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if (scheme !in setOf("https", "http") || host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null) {
            throw CobreIdException("Use um endereço HTTPS ou o endereço HTTP privado da sua rede local.")
        }
        if (scheme == "http" && !isPrivateHost(host)) {
            throw CobreIdException("Por segurança, HTTP só é aceito para endereço local. Use HTTPS fora da rede local.")
        }
        return value
    }

    private fun isPrivateHost(host: String): Boolean {
        if (host == "localhost" || host == "::1" || host.endsWith(".local")) return true
        val parts = host.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        return octets[0] == 10 || octets[0] == 127 ||
            (octets[0] == 192 && octets[1] == 168) ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 169 && octets[1] == 254)
    }
}

data class CobreDevice(
    val id: String,
    val name: String,
    val type: String,
    val online: Boolean,
    val current: Boolean,
    val lastSeen: Long,
)

class CobreIdException(message: String, val statusCode: Int? = null) : Exception(message)
