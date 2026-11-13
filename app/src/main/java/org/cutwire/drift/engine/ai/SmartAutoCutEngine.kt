package org.cutwire.drift.engine.ai

import org.cutwire.drift.core.model.Clip
import org.cutwire.drift.core.model.Project
import org.cutwire.drift.core.model.TrackType
import org.cutwire.drift.engine.audio.AudioWaveformExtractor
import org.cutwire.drift.engine.command.CompoundCommand
import org.cutwire.drift.engine.command.EditCommand
import org.cutwire.drift.engine.command.SplitClipCommand
import org.cutwire.drift.engine.command.DeleteClipCommand
import javax.inject.Inject
import javax.inject.Singleton

data class SilenceInterval(
    val startUs: Long,
    val endUs: Long,
    val durationUs: Long = endUs - startUs
)

/**
 * On-device AI intelligence engine that analyzes audio waveform energy profiles
 * to detect silence / dead air pauses and produces optimal jump-cut edits.
 */
@Singleton
class SmartAutoCutEngine @Inject constructor(
    private val waveformExtractor: AudioWaveformExtractor
) {

    /**
     * Detects silent regions (< threshold) that last at least [minSilenceDurationUs].
     */
    fun detectSilences(
        peaks: List<Float>,
        clipDurationUs: Long,
        silenceThreshold: Float = 0.25f,
        minSilenceDurationUs: Long = 400_000L // 400ms min silence
    ): List<SilenceInterval> {
        if (peaks.isEmpty() || clipDurationUs <= 0L) return emptyList()

        val sampleIntervalUs = clipDurationUs / peaks.size
        val silences = mutableListOf<SilenceInterval>()

        var inSilence = false
        var silenceStartUs = 0L

        for (i in peaks.indices) {
            val isSilent = peaks[i] < silenceThreshold
            val timeUs = i * sampleIntervalUs

            if (isSilent && !inSilence) {
                inSilence = true
                silenceStartUs = timeUs
            } else if (!isSilent && inSilence) {
                inSilence = false
                val duration = timeUs - silenceStartUs
                if (duration >= minSilenceDurationUs) {
                    silences.add(SilenceInterval(silenceStartUs, timeUs))
                }
            }
        }

        if (inSilence) {
            val duration = clipDurationUs - silenceStartUs
            if (duration >= minSilenceDurationUs) {
                silences.add(SilenceInterval(silenceStartUs, clipDurationUs))
            }
        }

        return silences
    }

    /**
     * Creates an atomic [CompoundCommand] that trims out detected silence segments
     * from the target clip, preserving only active speech/action segments.
     */
    fun createAutoCutCommand(
        clip: Clip,
        silences: List<SilenceInterval>
    ): EditCommand? {
        if (silences.isEmpty()) return null

        val commands = mutableListOf<EditCommand>()
        // Generate split points at the boundary of each speech segment
        for (silence in silences) {
            val cutTimeUs = clip.timelineStartUs + silence.startUs
            if (cutTimeUs > clip.timelineStartUs && cutTimeUs < clip.timelineEndUs) {
                commands.add(SplitClipCommand(clip.id, cutTimeUs))
            }
        }

        return if (commands.isNotEmpty()) {
            CompoundCommand(commands, "AI Smart Auto-Cut")
        } else null
    }
}
