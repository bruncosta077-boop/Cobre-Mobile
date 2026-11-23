package app.cobre.mobile

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MobileSettings(context: Context) {
    private val appContext = context.applicationContext
    private val appearancePrefs = appContext.getSharedPreferences("cobre-mobile-appearance", Context.MODE_PRIVATE)
    private val accessPrefs = appContext.getSharedPreferences("cobre-mobile-local-access", Context.MODE_PRIVATE)
    init {
        val legacy = appContext.getSharedPreferences("cobre-mobile-settings", Context.MODE_PRIVATE)
        if (legacy.contains("accent_primary") && !appearancePrefs.contains("accent_primary")) {
            appearancePrefs.edit()
                .putInt("accent_primary", legacy.getInt("accent_primary", 0xFFC9773F.toInt()))
                .putInt("accent_secondary", legacy.getInt("accent_secondary", 0xFFE6A06A.toInt()))
                .commit()
        }
        val access = accessPrefs.edit()
        var hasAccessMigration = false
        if (legacy.contains("library_folder") && !accessPrefs.contains("library_folder")) {
            access.putString("library_folder", legacy.getString("library_folder", null)); hasAccessMigration = true
        }
        if (legacy.contains("desktop_library_url") && !accessPrefs.contains("desktop_library_url")) {
            access.putString("desktop_library_url", legacy.getString("desktop_library_url", null)); hasAccessMigration = true
        }
        if (hasAccessMigration) access.commit()
        if (legacy.all.isNotEmpty()) legacy.edit().clear().commit()
    }
    var accentPrimary by mutableIntStateOf(appearancePrefs.getInt("accent_primary", 0xFFC9773F.toInt()))
        private set
    var accentSecondary by mutableIntStateOf(appearancePrefs.getInt("accent_secondary", 0xFFE6A06A.toInt()))
        private set
    var vinylPlayer by mutableStateOf(appearancePrefs.getBoolean("vinyl_player", false))
        private set
    var folderUri by mutableStateOf(accessPrefs.getString("library_folder", null))
        private set
    var desktopUrl by mutableStateOf(accessPrefs.getString("desktop_library_url", null))
        private set
    var secureDesktopAccessRequired by mutableStateOf(accessPrefs.getBoolean("desktop_secure_access_required", false))
        private set

    fun setAccent(primary: Int, secondary: Int) {
        accentPrimary = primary
        accentSecondary = secondary
        appearancePrefs.edit().putInt("accent_primary", primary).putInt("accent_secondary", secondary).apply()
    }

    fun saveVinylPlayer(enabled: Boolean) {
        vinylPlayer = enabled
        appearancePrefs.edit().putBoolean("vinyl_player", enabled).apply()
    }

    fun setFolder(uri: String?) {
        folderUri = uri
        accessPrefs.edit().putString("library_folder", uri).apply()
    }

    fun saveDesktopUrl(url: String?) {
        desktopUrl = url?.trim()?.takeIf(String::isNotBlank)
        accessPrefs.edit().putString("desktop_library_url", desktopUrl).apply()
    }

    fun requireSecureDesktopAccess() {
        secureDesktopAccessRequired = true
        accessPrefs.edit().putBoolean("desktop_secure_access_required", true).apply()
    }
}
