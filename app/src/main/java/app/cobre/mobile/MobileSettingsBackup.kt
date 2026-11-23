package app.cobre.mobile

import android.content.Context
import org.json.JSONObject

/** Portable preferences plus non-secret connection hints. Credentials, grants, folder permissions and audio files are never exported. */
object MobileSettingsBackup{
    private const val FORMAT="cobre-mobile-settings"
    private const val SCHEMA=2
    private val allowedAccents=setOf(
        0xFFC9773F.toInt() to 0xFFE6A06A.toInt(),0xFFD99AA9.toInt() to 0xFFF1C4CE.toInt(),
        0xFFD2AC67.toInt() to 0xFFE8CC8F.toInt(),0xFF6D91B8.toInt() to 0xFF91B7C7.toInt(),
        0xFFC97863.toInt() to 0xFFDCA779.toInt(),0xFF9B80C2.toInt() to 0xFFC698B0.toInt(),
        0xFF77A786.toInt() to 0xFFB4C39A.toInt(),
    )

    fun export(settings:MobileSettings,account:CobreIdRepository,context:Context):String{
        val audio=context.getSharedPreferences(PlaybackService.AUDIO_PREFS,Context.MODE_PRIVATE)
        val bands=JSONObject().apply{repeat(5){put("eqBand$it",audio.getInt("eq_band_$it",0))}}
        return JSONObject().put("format",FORMAT).put("schemaVersion",SCHEMA).put("createdAt",System.currentTimeMillis())
            .put("appearance",JSONObject().put("accentPrimary",settings.accentPrimary).put("accentSecondary",settings.accentSecondary).put("vinylPlayer",settings.vinylPlayer).put("dynamicAlbumColors",settings.dynamicAlbumColors))
            .put("audio",JSONObject().put("equalizerEnabled",audio.getBoolean("eq_enabled",false)).put("equalizerBands",bands)
                .put("replayGainEnabled",audio.getBoolean("replaygain_enabled",false)).put("bassEnabled",audio.getBoolean("bass_enabled",false))
                .put("bassStrength",audio.getInt("bass_strength",25)).put("spatialAudioEnabled",audio.getBoolean("virtualizer_enabled",false))
                .put("spatialAudioStrength",audio.getInt("virtualizer_strength",20)))
            .put("connection",JSONObject().put("serverUrl",account.serverUrl).put("accountEmail",account.savedEmail)
                .put("desktopOrigin",safeLocalDesktopOrigin(settings.desktopUrl))).toString(2)
    }

    fun import(jsonText:String,settings:MobileSettings,account:CobreIdRepository,context:Context):Boolean{
        require(jsonText.toByteArray(Charsets.UTF_8).size<=64*1024){"Este arquivo de backup é maior que o permitido."}
        val root=JSONObject(jsonText)
        val schema=root.optInt("schemaVersion",-1)
        require(root.getString("format")==FORMAT&&schema in 1..SCHEMA){"Este arquivo não é um backup compatível do Cobre Mobile."}
        val appearance=root.getJSONObject("appearance")
        val primary=appearance.getInt("accentPrimary");val secondary=appearance.getInt("accentSecondary")
        require((primary to secondary) in allowedAccents){"A cor deste backup não é reconhecida pelo Cobre Mobile."}
        val vinyl=appearance.getBoolean("vinylPlayer");val dynamic=appearance.getBoolean("dynamicAlbumColors")
        val audio=root.getJSONObject("audio");val bands=audio.getJSONObject("equalizerBands")
        val bandValues=(0..4).map{bands.getInt("eqBand$it").also{value->require(value in -12..12){"Ajuste de equalizador inválido no backup."}}}
        val bassStrength=audio.getInt("bassStrength").also{require(it in 0..100){"Ajuste de graves inválido no backup."}}
        val spatialStrength=audio.getInt("spatialAudioStrength").also{require(it in 0..100){"Ajuste de áudio espacial inválido no backup."}}
        val eqEnabled=audio.getBoolean("equalizerEnabled");val replayGain=audio.getBoolean("replayGainEnabled")
        val bassEnabled=audio.getBoolean("bassEnabled");val spatialEnabled=audio.getBoolean("spatialAudioEnabled")
        val connection=root.optJSONObject("connection")
        require((schema==1&&connection==null)||(schema==2&&connection!=null)){"O conteúdo do backup está incompleto."}
        if(connection!=null)require(connection.has("serverUrl")&&connection.has("accountEmail")&&connection.has("desktopOrigin")){"As informações de conexão do backup estão incompletas."}
        val serverUrl=connection?.optString("serverUrl").orEmpty()
        val accountEmail=connection?.optString("accountEmail").orEmpty()
        val desktopOrigin=connection?.optString("desktopOrigin").orEmpty().takeIf(String::isNotBlank)?.let(::safeLocalDesktopOrigin)
        require(connection==null||desktopOrigin!=null||connection.isNull("desktopOrigin")||connection.optString("desktopOrigin").isBlank()) {
            "O endereço local do Desktop não é válido neste backup."
        }
        // Validate account URL/email before changing any preferences.
        val normalizedServer=account.validateServerUrl(serverUrl)
        require(accountEmail.isBlank()||(accountEmail.length<=254&&accountEmail.contains('@')&&accountEmail.none(Char::isISOControl))) {
            "O e-mail do backup não é válido."
        }

        val audioPrefs=context.getSharedPreferences(PlaybackService.AUDIO_PREFS,Context.MODE_PRIVATE)
        check(audioPrefs.edit().putBoolean("eq_enabled",eqEnabled).putBoolean("replaygain_enabled",replayGain)
            .putBoolean("bass_enabled",bassEnabled).putInt("bass_strength",bassStrength)
            .putBoolean("virtualizer_enabled",spatialEnabled).putInt("virtualizer_strength",spatialStrength)
            .apply{bandValues.forEachIndexed{index,value->putInt("eq_band_$index",value)}}.commit()){"Não foi possível salvar os ajustes de áudio importados."}
        settings.setAccent(primary,secondary)
        settings.saveVinylPlayer(vinyl)
        settings.saveDynamicAlbumColors(dynamic)
        var sessionReset=false
        if(connection!=null){
            sessionReset=account.restoreNonSecretSettings(normalizedServer,accountEmail)
            if(safeLocalDesktopOrigin(settings.desktopUrl)!=desktopOrigin)account.clearDesktopGrant()
            settings.saveDesktopUrl(desktopOrigin)
        }
        settings.notifyPortableRestore()
        return sessionReset
    }

}
