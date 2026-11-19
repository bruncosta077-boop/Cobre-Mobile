package app.cobre.mobile

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MobileSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("cobre-mobile-settings", Context.MODE_PRIVATE)
    var accentPrimary by mutableIntStateOf(prefs.getInt("accent_primary", 0xFFC9773F.toInt()))
        private set
    var accentSecondary by mutableIntStateOf(prefs.getInt("accent_secondary", 0xFFE6A06A.toInt()))
        private set
    var folderUri by mutableStateOf(prefs.getString("library_folder", null))
        private set
    var desktopUrl by mutableStateOf(prefs.getString("desktop_library_url", null))
        private set

    fun setAccent(primary: Int, secondary: Int) {
        accentPrimary = primary
        accentSecondary = secondary
        prefs.edit().putInt("accent_primary", primary).putInt("accent_secondary", secondary).apply()
    }

    fun setFolder(uri: String?) {
        folderUri = uri
        prefs.edit().putString("library_folder", uri).apply()
    }

    fun saveDesktopUrl(url: String?) {
        desktopUrl = url?.trim()?.takeIf(String::isNotBlank)
        prefs.edit().putString("desktop_library_url", desktopUrl).apply()
    }
}
