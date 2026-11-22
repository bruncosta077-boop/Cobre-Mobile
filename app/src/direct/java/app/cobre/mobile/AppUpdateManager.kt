package app.cobre.mobile

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class MobileUpdate(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val expectedSha256: String?,
)

class AppUpdateManager(private val context: Context) {
    private val preferences = context.getSharedPreferences("cobre-mobile-updates", Context.MODE_PRIVATE)

    suspend fun checkForUpdate(force: Boolean = false): MobileUpdate? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - preferences.getLong("last_check", 0L) < CHECK_INTERVAL_MS) return@withContext null
        val connection = URL(RELEASES_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "Cobre-Mobile/${BuildConfig.VERSION_NAME}")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("GitHub respondeu HTTP ${connection.responseCode} ao verificar atualizações.")
            }
            val releases = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            val available = (0 until releases.length()).mapNotNull { index ->
                val release = releases.optJSONObject(index) ?: return@mapNotNull null
                val match = RELEASE_TAG.matchEntire(release.optString("tag_name")) ?: return@mapNotNull null
                if (release.optBoolean("draft")) return@mapNotNull null
                val version = match.groupValues[1]
                if (compareVersions(version, BuildConfig.VERSION_NAME) <= 0) return@mapNotNull null
                val assets = release.optJSONArray("assets") ?: return@mapNotNull null
                val expectedName = "Cobre-Mobile-$version.apk"
                val asset = (0 until assets.length()).mapNotNull(assets::optJSONObject)
                    .firstOrNull { it.optString("name") == expectedName } ?: return@mapNotNull null
                val download = asset.optString("browser_download_url")
                val uri = runCatching { Uri.parse(download) }.getOrNull() ?: return@mapNotNull null
                if (uri.scheme != "https" || uri.host != "github.com" ||
                    !uri.path.orEmpty().startsWith("/bruncosta077-boop/Cobre-Mobile/releases/download/")) return@mapNotNull null
                MobileUpdate(
                    version = version,
                    notes = release.optString("body").take(12_000),
                    apkUrl = download,
                    expectedSha256 = asset.optString("digest").takeIf { it.startsWith("sha256:") }?.substringAfter("sha256:"),
                )
            }.maxWithOrNull { left, right -> compareVersions(left.version, right.version) }
            preferences.edit().putLong("last_check", now).apply()
            available
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadAndVerify(update: MobileUpdate, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val connection = URL(update.apkUrl).openConnection() as HttpURLConnection
        val destination = File(context.cacheDir, "Cobre-Mobile-${update.version}.apk")
        val temporary = File(context.cacheDir, "Cobre-Mobile-${update.version}.apk.part")
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/octet-stream")
            connection.setRequestProperty("User-Agent", "Cobre-Mobile/${BuildConfig.VERSION_NAME}")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("Não foi possível baixar a atualização (HTTP ${connection.responseCode}).")
            }
            val total = connection.contentLengthLong
            if (total > MAX_APK_BYTES) throw IllegalStateException("O arquivo de atualização excede o tamanho permitido.")
            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        if (copied > MAX_APK_BYTES) throw IllegalStateException("O arquivo de atualização excede o tamanho permitido.")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        if (total > 0) onProgress((copied * 100 / total).toInt().coerceIn(0, 100))
                    }
                    output.flush()
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (!update.expectedSha256.isNullOrBlank() && !hash.equals(update.expectedSha256, ignoreCase = true)) {
                throw IllegalStateException("A verificação de integridade do APK falhou. Nenhuma instalação foi iniciada.")
            }
            val archive = if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageArchiveInfo(temporary.absolutePath, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(temporary.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            } ?: throw IllegalStateException("O arquivo baixado não é um APK válido.")
            if (archive.packageName != context.packageName) throw IllegalStateException("Este APK não pertence ao Cobre Mobile.")
            val installed = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            if (archive.longVersionCode <= installed.longVersionCode) throw IllegalStateException("A versão baixada não é mais recente que a instalada.")
            val installedSigners = installed.signingInfo?.apkContentsSigners.orEmpty().map { it.toCharsString() }.toSet()
            val archiveSigners = archive.signingInfo?.apkContentsSigners.orEmpty().map { it.toCharsString() }.toSet()
            if (installedSigners.isEmpty() || installedSigners != archiveSigners) {
                throw IllegalStateException("A assinatura não corresponde à versão instalada. Para proteger seus dados, a atualização foi cancelada.")
            }
            if (destination.exists()) destination.delete()
            if (!temporary.renameTo(destination)) throw IllegalStateException("Não foi possível preparar o arquivo de instalação.")
            destination
        } finally {
            connection.disconnect()
            if (temporary.exists()) temporary.delete()
        }
    }

    fun canInstallPackages(): Boolean = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()
    fun permissionSettingsIntent(): Intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
    fun installerIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun savePendingNotes(update: MobileUpdate) {
        preferences.edit().putString("pending_version", update.version).putString("pending_notes", update.notes).apply()
    }

    fun takeNotesForCurrentVersion(): String? {
        if (preferences.getString("pending_version", null) != BuildConfig.VERSION_NAME) return null
        val notes = preferences.getString("pending_notes", null)
        preferences.edit().remove("pending_version").remove("pending_notes").apply()
        return notes?.takeIf(String::isNotBlank)
    }

    private fun compareVersions(left: String, right: String): Int {
        val a = left.split('.').map { it.toIntOrNull() ?: 0 }
        val b = right.split('.').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(a.size, b.size)) {
            val comparison = a.getOrElse(index) { 0 }.compareTo(b.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }

    companion object {
        private const val RELEASES_URL = "https://api.github.com/repos/bruncosta077-boop/Cobre-Mobile/releases?per_page=20"
        private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
        private const val MAX_APK_BYTES = 250L * 1024 * 1024
        private val RELEASE_TAG = Regex("mobile-v([0-9]+(?:\\.[0-9]+){1,2})")
    }
}
