package app.cobre.mobile

import android.net.Uri
import android.os.SystemClock
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
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Reprodução em segundo plano, notificação, tela de bloqueio e controles do sistema (MediaSession). */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val metricsLock = Any()
    private var activeStreamStartMs = 0L
    private var firstByteMs: Long? = null
    private var windowStartMs = 0L
    private var windowBytes = 0L

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

    private fun isDesktopStream(uri: Uri, isNetwork: Boolean) = isNetwork && uri.scheme == "http" && uri.path?.startsWith("/stream/") == true

    private fun saveMetric(key: String, value: Long) {
        getSharedPreferences(METRICS_PREFS, MODE_PRIVATE).edit()
            .putLong(key, value.coerceIn(0, 1_000_000_000L))
            .putLong("measuredAtElapsedMs", SystemClock.elapsedRealtime())
            .apply()
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val httpFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(false)
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory).setTransferListener(streamTransfers)
        val player = ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pausa ao desconectar o fone
            .build()
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }

    companion object {
        const val METRICS_PREFS = "cobre-stream-metrics"
        const val METRIC_WINDOW_MS = 5_000L
        const val METRICS_MAX_AGE_MS = 120_000L
    }
}
