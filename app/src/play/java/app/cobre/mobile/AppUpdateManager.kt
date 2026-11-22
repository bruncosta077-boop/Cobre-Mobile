package app.cobre.mobile

import android.content.Context
import android.content.Intent
import java.io.File

data class MobileUpdate(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val expectedSha256: String?,
)

/** Play Store builds receive updates only through Google Play. */
class AppUpdateManager(@Suppress("UNUSED_PARAMETER") context: Context) {
    suspend fun checkForUpdate(@Suppress("UNUSED_PARAMETER") force: Boolean = false): MobileUpdate? = null
    suspend fun downloadAndVerify(@Suppress("UNUSED_PARAMETER") update: MobileUpdate, @Suppress("UNUSED_PARAMETER") onProgress: (Int) -> Unit): File =
        error("As atualizações desta edição são entregues pela Google Play Store.")
    fun canInstallPackages(): Boolean = false
    fun permissionSettingsIntent(): Intent = Intent()
    fun installerIntent(@Suppress("UNUSED_PARAMETER") apk: File): Intent = Intent()
    fun savePendingNotes(@Suppress("UNUSED_PARAMETER") update: MobileUpdate) = Unit
    fun takeNotesForCurrentVersion(): String? = null
}
