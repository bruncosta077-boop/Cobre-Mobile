package app.cobre.mobile

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.StatFs
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
        if (!force && now - preferences.getLong("last_check", 0L) < CHECK_INTERVAL_MS) return@withContext cachedUpdate()
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
            preferences.edit().putLong("last_check", now).apply {
                if (available == null) {
                    remove("available_version").remove("available_notes").remove("available_apk_url").remove("available_sha256")
                } else {
                    putString("available_version", available.version)
                    putString("available_notes", available.notes)
                    putString("available_apk_url", available.apkUrl)
                    available.expectedSha256?.let { putString("available_sha256", it) } ?: remove("available_sha256")
                }
            }.apply()
            available
        } finally {
            connection.disconnect()
        }
    }

    private fun cachedUpdate(): MobileUpdate? {
        val version = preferences.getString("available_version", null) ?: return null
        if (compareVersions(version, BuildConfig.VERSION_NAME) <= 0) return null
        val apkUrl = preferences.getString("available_apk_url", null) ?: return null
        val uri = runCatching { Uri.parse(apkUrl) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host != "github.com" ||
            !uri.path.orEmpty().startsWith("/bruncosta077-boop/Cobre-Mobile/releases/download/")) return null
        return MobileUpdate(
            version = version,
            notes = preferences.getString("available_notes", "").orEmpty().take(12_000),
            apkUrl = apkUrl,
            expectedSha256 = preferences.getString("available_sha256", null),
        )
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
            cleanupStaleDownloads()
            requireFreeSpace(requiredForDownload(total))
            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var nextSpaceCheck = 1024L * 1024
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        if (copied > MAX_APK_BYTES) throw IllegalStateException("O arquivo de atualização excede o tamanho permitido.")
                        if (copied >= nextSpaceCheck) {
                            requireFreeSpace(INSTALL_RESERVE_BYTES)
                            nextSpaceCheck = copied + 1024L * 1024
                        }
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
            requireFreeSpace(requiredForInstall(temporary.length()))
            if (destination.exists()) destination.delete()
            if (!temporary.renameTo(destination)) throw IllegalStateException("Não foi possível preparar o arquivo de instalação.")
            destination
        } finally {
            connection.disconnect()
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun cleanupStaleDownloads() {
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("Cobre-Mobile-") &&
                (file.name.endsWith(".apk") || file.name.endsWith(".apk.part"))) {
                runCatching { file.delete() }
            }
        }
    }

    private fun requireFreeSpace(requiredBytes: Long) {
        val available = runCatching { StatFs(context.cacheDir.absolutePath).availableBytes }
            .getOrElse { throw IllegalStateException("Não foi possível verificar o espaço livre do celular.") }
        if (available < requiredBytes) {
            val neededMb = ((requiredBytes - available) / (1024 * 1024) + 1).coerceAtLeast(1)
            throw IllegalStateException("Pouco espaço no celular. Libere pelo menos ${neededMb} MB e tente novamente.")
        }
    }

    private fun requiredForDownload(apkBytes: Long): Long {
        val apkSize = apkBytes.takeIf { it > 0 } ?: MIN_APK_BYTES
        // Keep room for the cached APK, Android's staging copy, and install overhead.
        return apkSize * 2 + INSTALL_RESERVE_BYTES
    }

    private fun requiredForInstall(apkBytes: Long): Long = apkBytes + INSTALL_RESERVE_BYTES

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
        val a = VERSION.matchEntire(left) ?: return left.compareTo(right)
        val b = VERSION.matchEntire(right) ?: return left.compareTo(right)
        for (index in 1..3) {
            val comparison = (a.groupValues[index].toIntOrNull() ?: 0).compareTo(b.groupValues[index].toIntOrNull() ?: 0)
            if (comparison != 0) return comparison
        }
        val aStage = a.groupValues[4].ifBlank { null }
        val bStage = b.groupValues[4].ifBlank { null }
        if (aStage == null || bStage == null) return when {
            aStage == null && bStage == null -> 0
            aStage == null -> 1 // A stable release ranks above any preview of the same version.
            else -> -1
        }
        val stageOrder = mapOf("alpha" to 0, "beta" to 1, "rc" to 2)
        val stageComparison = (stageOrder[aStage] ?: -1).compareTo(stageOrder[bStage] ?: -1)
        if (stageComparison != 0) return stageComparison
        return a.groupValues[5].toInt().compareTo(b.groupValues[5].toInt())
    }

    companion object {
        private const val RELEASES_URL = "https://api.github.com/repos/bruncosta077-boop/Cobre-Mobile/releases?per_page=20"
        private const val CHECK_INTERVAL_MS = 60 * 60 * 1000L
        private const val MAX_APK_BYTES = 250L * 1024 * 1024
        private const val MIN_APK_BYTES = 20L * 1024 * 1024
        private const val INSTALL_RESERVE_BYTES = 64L * 1024 * 1024
        private val RELEASE_TAG = Regex("mobile-v([0-9]+(?:\\.[0-9]+){1,2}(?:-(?:alpha|beta|rc)\\.[0-9]+)?)")
        private val VERSION = Regex("([0-9]+)\\.([0-9]+)(?:\\.([0-9]+))?(?:-(alpha|beta|rc)\\.([0-9]+))?")
    }
}
