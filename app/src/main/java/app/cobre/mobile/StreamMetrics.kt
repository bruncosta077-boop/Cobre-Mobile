package app.cobre.mobile

import android.content.Context
import android.os.SystemClock

/** Measurements are generated only for audio received from the Desktop LAN stream. */
data class StreamMetrics(val latencyMs: Long, val bytesPerSecond: Long) {
    companion object {
        fun recent(context: Context): StreamMetrics? {
            val prefs = context.applicationContext.getSharedPreferences(PlaybackService.METRICS_PREFS, Context.MODE_PRIVATE)
            val measuredAt = prefs.getLong("measuredAtElapsedMs", 0L)
            val age = SystemClock.elapsedRealtime() - measuredAt
            if (measuredAt == 0L || age !in 0..PlaybackService.METRICS_MAX_AGE_MS) return null
            val latency = prefs.getLong("streamLatencyMs", -1L)
            val rate = prefs.getLong("streamBytesPerSecond", -1L)
            if (latency !in 0..120_000 || rate <= 0) return null
            return StreamMetrics(latency, rate)
        }
    }
}
