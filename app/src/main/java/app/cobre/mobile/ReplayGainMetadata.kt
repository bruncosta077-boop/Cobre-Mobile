package app.cobre.mobile

import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.CommentFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import androidx.media3.extractor.mp3.Mp3InfoReplayGain

internal data class ReplayGainAdjustment(val gainDb: Float, val peak: Float?)

/** Reads standard ReplayGain tags exposed by Media3; unsupported/missing tags leave volume unchanged. */
@OptIn(UnstableApi::class)
internal fun replayGainAdjustment(metadata: Metadata): ReplayGainAdjustment? {
    var trackGain: Float? = null
    var albumGain: Float? = null
    var trackPeak: Float? = null
    var albumPeak: Float? = null

    fun accept(key: String?, value: String) {
        val normalizedKey = key.orEmpty().trim().uppercase().substringBefore('=')
        val normalizedValue = value.substringAfter('=', value).trim()
        val number = NUMBER.find(normalizedValue)?.value?.toFloatOrNull()?.takeIf(Float::isFinite) ?: return
        when (normalizedKey) {
            "REPLAYGAIN_TRACK_GAIN" -> if (trackGain == null && number in -60f..24f) trackGain = number
            "REPLAYGAIN_ALBUM_GAIN" -> if (albumGain == null && number in -60f..24f) albumGain = number
            "REPLAYGAIN_TRACK_PEAK" -> if (trackPeak == null && number > 0f && number <= 20f) trackPeak = number
            "REPLAYGAIN_ALBUM_PEAK" -> if (albumPeak == null && number > 0f && number <= 20f) albumPeak = number
        }
    }

    for (index in 0 until metadata.length()) {
        when (val entry = metadata[index]) {
            is VorbisComment -> accept(entry.key, entry.value)
            is TextInformationFrame -> if (entry.id == "TXXX") {
                entry.values.forEach { accept(entry.description.orEmpty(), it) }
            }
            is CommentFrame -> accept(entry.description, entry.text)
            is Mp3InfoReplayGain -> {
                val fields = listOfNotNull(entry.field1, entry.field2)
                val radio = fields.firstOrNull { it.name == Mp3InfoReplayGain.GainField.NAME_RADIO } ?: fields.firstOrNull()
                if (trackGain == null && radio?.gain?.isFinite() == true && radio.gain in -60f..24f) trackGain = radio.gain
                if (trackPeak == null && entry.peak.isFinite() && entry.peak > 0f && entry.peak <= 20f) trackPeak = entry.peak
            }
        }
    }

    val gain = trackGain ?: albumGain ?: return null
    return ReplayGainAdjustment(gain, trackPeak ?: albumPeak)
}

private val NUMBER = Regex("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)")
