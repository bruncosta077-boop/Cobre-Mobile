package app.cobre.mobile

import android.content.Context
import android.net.Uri
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
        val savedDesktopAddress=accessPrefs.getString("desktop_library_url",null)
        if(!savedDesktopAddress.isNullOrBlank()){
            val safeAddress=safeLocalDesktopOrigin(savedDesktopAddress)
            val hadQuery=runCatching{Uri.parse(savedDesktopAddress).encodedQuery!=null}.getOrDefault(true)
            if(safeAddress==null||safeAddress!=savedDesktopAddress||hadQuery){
                val editor=accessPrefs.edit().putBoolean("desktop_secure_access_required",true)
                if(safeAddress==null)editor.remove("desktop_library_url")else editor.putString("desktop_library_url",safeAddress)
                editor.commit()
            }
        }
    }
    var accentPrimary by mutableIntStateOf(appearancePrefs.getInt("accent_primary", 0xFFC9773F.toInt()))
        private set
    var accentSecondary by mutableIntStateOf(appearancePrefs.getInt("accent_secondary", 0xFFE6A06A.toInt()))
        private set
    var vinylPlayer by mutableStateOf(appearancePrefs.getBoolean("vinyl_player", false))
        private set
    var dynamicAlbumColors by mutableStateOf(appearancePrefs.getBoolean("dynamic_album_colors", false))
        private set
    var folderUri by mutableStateOf(accessPrefs.getString("library_folder", null))
        private set
    var desktopUrl by mutableStateOf(accessPrefs.getString("desktop_library_url", null))
        private set
    var secureDesktopAccessRequired by mutableStateOf(accessPrefs.getBoolean("desktop_secure_access_required", false))
        private set
    var portableRestoreRevision by mutableIntStateOf(0)
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

    fun saveDynamicAlbumColors(enabled: Boolean) {
        dynamicAlbumColors = enabled
        appearancePrefs.edit().putBoolean("dynamic_album_colors", enabled).apply()
    }

    fun setFolder(uri: String?) {
        folderUri = uri
        accessPrefs.edit().putString("library_folder", uri).apply()
    }

    fun saveDesktopUrl(url: String?) {
        val raw=url?.trim()?.takeIf(String::isNotBlank)
        val safe=safeLocalDesktopOrigin(raw)
        desktopUrl=safe
        accessPrefs.edit().putString("desktop_library_url", desktopUrl).apply()
        if(raw!=null&&safe!=null&&runCatching{Uri.parse(raw).encodedQuery!=null}.getOrDefault(true))requireSecureDesktopAccess()
    }

    fun requireSecureDesktopAccess() {
        secureDesktopAccessRequired = true
        accessPrefs.edit().putBoolean("desktop_secure_access_required", true).apply()
    }

    fun notifyPortableRestore(){portableRestoreRevision++}
}

internal fun safeLocalDesktopOrigin(raw:String?):String?{
    if(raw.isNullOrBlank())return null
    val uri=runCatching{Uri.parse(raw.trim())}.getOrNull()?:return null
    val host=uri.host?.lowercase().orEmpty()
    val octets=host.split('.').map{it.toIntOrNull()?:return null}
    if(uri.scheme!="http"||uri.port !in -1..65535||octets.size!=4||octets.any{it !in 0..255}||uri.userInfo!=null||uri.fragment!=null||uri.path.orEmpty() !in setOf("","/")||
        !(octets[0]==10||(octets[0]==192&&octets[1]==168)||(octets[0]==172&&octets[1] in 16..31)||(octets[0]==169&&octets[1]==254)||octets[0]==127))return null
    return Uri.Builder().scheme("http").encodedAuthority(buildString{append(host);if(uri.port>=0)append(":${uri.port}")}).build().toString()
}
