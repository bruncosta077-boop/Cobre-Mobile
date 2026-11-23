package app.cobre.mobile

import android.content.Context
import org.json.JSONObject

/** Portable settings only. Connection addresses, grants, credentials, folder permissions and audio files are never exported. */
object MobileSettingsBackup{
    private const val FORMAT="cobre-mobile-settings"
    private const val SCHEMA=1
    private val allowedAccents=setOf(
        0xFFC9773F.toInt() to 0xFFE6A06A.toInt(),0xFFD99AA9.toInt() to 0xFFF1C4CE.toInt(),
        0xFFD2AC67.toInt() to 0xFFE8CC8F.toInt(),0xFF6D91B8.toInt() to 0xFF91B7C7.toInt(),
        0xFFC97863.toInt() to 0xFFDCA779.toInt(),0xFF9B80C2.toInt() to 0xFFC698B0.toInt(),
        0xFF77A786.toInt() to 0xFFB4C39A.toInt(),
    )

    fun export(settings:MobileSettings,context:Context):String{
        val audio=context.getSharedPreferences(PlaybackService.AUDIO_PREFS,Context.MODE_PRIVATE)
        val bands=JSONObject().apply{repeat(5){put("eqBand$it",audio.getInt("eq_band_$it",0))}}
        return JSONObject().put("format",FORMAT).put("schemaVersion",SCHEMA).put("createdAt",System.currentTimeMillis())
            .put("appearance",JSONObject().put("accentPrimary",settings.accentPrimary).put("accentSecondary",settings.accentSecondary).put("vinylPlayer",settings.vinylPlayer).put("dynamicAlbumColors",settings.dynamicAlbumColors))
            .put("audio",JSONObject().put("equalizerEnabled",audio.getBoolean("eq_enabled",false)).put("equalizerBands",bands)
                .put("replayGainEnabled",audio.getBoolean("replaygain_enabled",false)).put("bassEnabled",audio.getBoolean("bass_enabled",false))
                .put("bassStrength",audio.getInt("bass_strength",25)).put("spatialAudioEnabled",audio.getBoolean("virtualizer_enabled",false))
                .put("spatialAudioStrength",audio.getInt("virtualizer_strength",20))).toString(2)
    }

    fun import(jsonText:String,settings:MobileSettings,context:Context){
        require(jsonText.toByteArray(Charsets.UTF_8).size<=64*1024){"Este arquivo de backup é maior que o permitido."}
        val root=JSONObject(jsonText)
        require(root.getString("format")==FORMAT&&root.getInt("schemaVersion")==SCHEMA){"Este arquivo não é um backup compatível do Cobre Mobile."}
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

        val audioPrefs=context.getSharedPreferences(PlaybackService.AUDIO_PREFS,Context.MODE_PRIVATE)
        check(audioPrefs.edit().putBoolean("eq_enabled",eqEnabled).putBoolean("replaygain_enabled",replayGain)
            .putBoolean("bass_enabled",bassEnabled).putInt("bass_strength",bassStrength)
            .putBoolean("virtualizer_enabled",spatialEnabled).putInt("virtualizer_strength",spatialStrength)
            .apply{bandValues.forEachIndexed{index,value->putInt("eq_band_$index",value)}}.commit()){"Não foi possível salvar os ajustes de áudio importados."}
        settings.setAccent(primary,secondary)
        settings.saveVinylPlayer(vinyl)
        settings.saveDynamicAlbumColors(dynamic)
        settings.notifyPortableRestore()
    }
}
