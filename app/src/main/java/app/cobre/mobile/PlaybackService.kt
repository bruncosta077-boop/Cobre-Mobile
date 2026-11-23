package app.cobre.mobile

import android.net.Uri
import android.os.SystemClock
import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import android.media.audiofx.BassBoost
import android.media.audiofx.Virtualizer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ln

/** Reprodução em segundo plano, notificação, tela de bloqueio e controles do sistema (MediaSession). */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val metricsLock = Any()
    private var activeStreamStartMs = 0L
    private var firstByteMs: Long? = null
    private var windowStartMs = 0L
    private var windowBytes = 0L
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var grantRefreshJob: Job? = null
    private var httpFactory: DefaultHttpDataSource.Factory? = null
    private val grantSecretPrefs by lazy { getSharedPreferences("cobre-id-secrets", MODE_PRIVATE) }
    private val audioPrefs by lazy { getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE) }
    private var audioEqualizer: Equalizer? = null
    private var audioBassBoost: BassBoost? = null
    private var audioVirtualizer: Virtualizer? = null
    private val audioPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "eq_enabled" || key?.startsWith("eq_band_") == true || key == "bass_enabled" || key == "bass_strength" || key == "virtualizer_enabled" || key == "virtualizer_strength") applyAudioEffectsSettings()
    }
    private val audioSessionListener = object : AnalyticsListener {
        override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
            audioPrefs.edit().putInt("audio_session_id", audioSessionId).apply()
            configureEqualizer(audioSessionId)
        }
    }
    private val grantSecretListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "desktop_grant_token") updateGrantHeader(httpFactory)
    }

    private val streamTransfers = object : TransferListener {
        override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
        override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
            if (!isDesktopStream(dataSpec.uri, isNetwork)) return
            synchronized(metricsLock) {
                activeStreamStartMs = SystemClock.elapsedRealtime()
                firstByteMs = null
                windowStartMs = activeStreamStartMs
                windowBytes = 0
            }
        }
        override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
            if (!isDesktopStream(dataSpec.uri, isNetwork) || bytesTransferred <= 0) return
            synchronized(metricsLock) {
                val now = SystemClock.elapsedRealtime()
                if (activeStreamStartMs == 0L) activeStreamStartMs = now
                if (firstByteMs == null) {
                    firstByteMs = now
                    saveMetric("streamLatencyMs", (now - activeStreamStartMs).coerceAtLeast(0))
                }
                windowBytes += bytesTransferred
                val elapsed = now - windowStartMs
                if (elapsed >= METRIC_WINDOW_MS) {
                    saveMetric("streamBytesPerSecond", (windowBytes * 1000L / elapsed).coerceAtLeast(0))
                    windowStartMs = now
                    windowBytes = 0
                }
            }
        }
        override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
            if (!isDesktopStream(dataSpec.uri, isNetwork)) return
            synchronized(metricsLock) {
                val elapsed = SystemClock.elapsedRealtime() - windowStartMs
                if (elapsed > 0 && windowBytes > 0) saveMetric("streamBytesPerSecond", windowBytes * 1000L / elapsed)
                activeStreamStartMs = 0
            }
        }
    }

    private fun isDesktopStream(uri: Uri, isNetwork: Boolean) = isNetwork && uri.scheme == "http" &&
        (uri.path?.startsWith("/stream/") == true || uri.path?.matches(Regex("/v1/tracks/\\d+/stream")) == true)

    private fun saveMetric(key: String, value: Long) {
        getSharedPreferences(METRICS_PREFS, MODE_PRIVATE).edit()
            .putLong(key, value.coerceIn(0, 1_000_000_000L))
            .putLong("measuredAtElapsedMs", SystemClock.elapsedRealtime())
            .apply()
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val cobreHttpFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(false)
        httpFactory = cobreHttpFactory
        grantSecretPrefs.registerOnSharedPreferenceChangeListener(grantSecretListener)
        audioPrefs.registerOnSharedPreferenceChangeListener(audioPrefsListener)
        audioPrefs.edit().putInt("audio_session_id", 0).putBoolean("audio_session_ready", false).putBoolean("eq_available", false)
            .putBoolean("bass_available", false).putBoolean("virtualizer_available", false).apply()
        updateGrantHeader(cobreHttpFactory)
        val dataSourceFactory = DefaultDataSource.Factory(this, cobreHttpFactory).setTransferListener(streamTransfers)
        val player = ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pausa ao desconectar o fone
            .build()
        player.addAnalyticsListener(audioSessionListener)
        session = MediaSession.Builder(this, player).build()
        grantRefreshJob = serviceScope.launch {
            while (isActive) {
                val repository=CobreIdRepository(applicationContext)
                if(repository.desktopGrantId()==null){delay(NO_GRANT_RETRY_MS);continue}
                val remaining=repository.desktopTokenExpiresAt()-System.currentTimeMillis()
                if(remaining>GRANT_REFRESH_MARGIN_MS){delay((remaining-GRANT_REFRESH_MARGIN_MS).coerceAtMost(GRANT_MAX_SLEEP_MS));continue}
                val refreshed=runCatching { repository.refreshDesktopGrant() }.isSuccess
                if(refreshed)updateGrantHeader(cobreHttpFactory) else delay(GRANT_RETRY_MS)
            }
        }
    }

    private fun updateGrantHeader(factory: DefaultHttpDataSource.Factory?) {
        val token = CobreIdRepository(applicationContext).currentDesktopGrantToken()
        factory?.setDefaultRequestProperties(if (token.isNullOrBlank()) emptyMap() else mapOf("Authorization" to "Bearer $token"))
    }

    private fun configureEqualizer(audioSessionId: Int) {
        releaseEqualizer()
        releaseBassBoost()
        releaseVirtualizer()
        if (audioSessionId <= 0) {
            audioPrefs.edit().putInt("audio_session_id", 0).putBoolean("audio_session_ready", false).apply()
            return
        }
        audioPrefs.edit().putBoolean("audio_session_ready", true).apply()
        runCatching {
            val effect=Equalizer(0,audioSessionId)
            audioEqualizer=effect
            check(effect.hasControl()) { "Equalizer control is owned by another audio effect." }
            val range=effect.bandLevelRange
            val targets=intArrayOf(60,250,1_000,4_000,12_000)
            for(index in 0 until effect.numberOfBands.toInt()){
                val band=index.toShort()
                val frequency=(effect.getCenterFreq(band)/1_000).coerceAtLeast(1).toDouble()
                val slot=targets.indices.minBy{abs(ln(frequency/targets[it].toDouble()))}
                val milliBel=(audioPrefs.getInt("eq_band_$slot",0)*100).coerceIn(range[0].toInt(),range[1].toInt())
                effect.setBandLevel(band,milliBel.toShort())
            }
            effect.enabled=audioPrefs.getBoolean("eq_enabled",false)
            audioPrefs.edit().putBoolean("eq_available",true).apply()
        }.onFailure {
            releaseEqualizer()
            audioPrefs.edit().putBoolean("eq_available",false).apply()
        }
        configureBassBoost(audioSessionId)
        configureVirtualizer(audioSessionId)
    }

    private fun applyAudioEffectsSettings() {
        applyEqualizerSettings()
        applyBassBoostSettings()
        applyVirtualizerSettings()
    }

    private fun applyEqualizerSettings() {
        val effect=audioEqualizer ?: return
        runCatching {
            val range=effect.bandLevelRange
            val targets=intArrayOf(60,250,1_000,4_000,12_000)
            for(index in 0 until effect.numberOfBands.toInt()){
                val band=index.toShort()
                val frequency=(effect.getCenterFreq(band)/1_000).coerceAtLeast(1).toDouble()
                val slot=targets.indices.minBy{abs(ln(frequency/targets[it].toDouble()))}
                val milliBel=(audioPrefs.getInt("eq_band_$slot",0)*100).coerceIn(range[0].toInt(),range[1].toInt())
                effect.setBandLevel(band,milliBel.toShort())
            }
            effect.enabled=audioPrefs.getBoolean("eq_enabled",false)
        }.onFailure { audioPrefs.edit().putBoolean("eq_available",false).apply() }
    }

    private fun configureBassBoost(audioSessionId: Int) {
        releaseBassBoost()
        runCatching {
            val effect = BassBoost(0, audioSessionId)
            audioBassBoost = effect
            check(effect.hasControl()) { "Bass boost control is owned by another audio effect." }
            applyBassBoostSettings()
            audioPrefs.edit().putBoolean("bass_available", true).apply()
        }.onFailure {
            releaseBassBoost()
            audioPrefs.edit().putBoolean("bass_available", false).apply()
        }
    }

    private fun applyBassBoostSettings() {
        val effect = audioBassBoost ?: return
        runCatching {
            val strength = audioPrefs.getInt("bass_strength", 25).coerceIn(0, 100) * 10
            if (effect.strengthSupported) effect.setStrength(strength.toShort())
            effect.enabled = audioPrefs.getBoolean("bass_enabled", false)
        }.onFailure { audioPrefs.edit().putBoolean("bass_available", false).apply() }
    }

    private fun configureVirtualizer(audioSessionId: Int) {
        releaseVirtualizer()
        runCatching {
            val effect = Virtualizer(0, audioSessionId)
            audioVirtualizer = effect
            check(effect.hasControl()) { "Virtualizer control is owned by another audio effect." }
            applyVirtualizerSettings()
            audioPrefs.edit().putBoolean("virtualizer_available", true).apply()
        }.onFailure {
            releaseVirtualizer()
            audioPrefs.edit().putBoolean("virtualizer_available", false).apply()
        }
    }

    private fun applyVirtualizerSettings() {
        val effect = audioVirtualizer ?: return
        runCatching {
            val strength = audioPrefs.getInt("virtualizer_strength", 20).coerceIn(0, 100) * 10
            if (effect.strengthSupported) effect.setStrength(strength.toShort())
            effect.enabled = audioPrefs.getBoolean("virtualizer_enabled", false)
        }.onFailure { audioPrefs.edit().putBoolean("virtualizer_available", false).apply() }
    }

    private fun releaseEqualizer() {
        runCatching { audioEqualizer?.release() }
        audioEqualizer=null
    }

    private fun releaseBassBoost() {
        runCatching { audioBassBoost?.release() }
        audioBassBoost = null
    }

    private fun releaseVirtualizer() {
        runCatching { audioVirtualizer?.release() }
        audioVirtualizer = null
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        grantSecretPrefs.unregisterOnSharedPreferenceChangeListener(grantSecretListener)
        audioPrefs.unregisterOnSharedPreferenceChangeListener(audioPrefsListener)
        grantRefreshJob?.cancel()
        serviceScope.cancel()
        session?.run { player.release(); release() }
        releaseEqualizer()
        releaseBassBoost()
        releaseVirtualizer()
        audioPrefs.edit().putInt("audio_session_id", 0).putBoolean("audio_session_ready", false).apply()
        session = null
        super.onDestroy()
    }

    companion object {
        const val METRICS_PREFS = "cobre-stream-metrics"
        const val AUDIO_PREFS = "cobre-mobile-audio"
        const val METRIC_WINDOW_MS = 5_000L
        const val METRICS_MAX_AGE_MS = 120_000L
        const val GRANT_REFRESH_MARGIN_MS = 3 * 60_000L
        const val GRANT_MAX_SLEEP_MS = 5 * 60_000L
        const val GRANT_RETRY_MS = 30_000L
        const val NO_GRANT_RETRY_MS = 60_000L
    }
}
