package org.cutwire.drift.feature.timeline

import org.cutwire.drift.core.model.Project
import kotlin.math.abs

data class SnapResult(
    val snappedTimeUs: Long,
    val didSnap: Boolean,
    val snapTargetUs: Long? = null
)

object SnapHelper {

    /**
     * Find the closest snap point within [thresholdUs] from the project clips and playhead.
     */
    fun findSnap(
        targetTimeUs: Long,
        project: Project?,
        playheadPositionUs: Long,
        thresholdUs: Long = 100_000L, // 100ms snap threshold
        excludeClipId: String? = null
    ): SnapResult {
        if (project == null) {
            return SnapResult(targetTimeUs, false)
        }

        val snapCandidates = mutableSetOf<Long>()
        // 1. Timeline start
        snapCandidates.add(0L)

        // 2. Current playhead
        if (playheadPositionUs > 0) {
            snapCandidates.add(playheadPositionUs)
        }

        // 3. Clip boundaries across all tracks
        for (track in project.tracks) {
            for (clip in track.clips) {
                if (clip.id == excludeClipId) continue
                snapCandidates.add(clip.timelineStartUs)
                snapCandidates.add(clip.timelineEndUs)
            }
        }

        var closestDistance = Long.MAX_VALUE
        var bestSnapTarget: Long? = null

        for (candidate in snapCandidates) {
            val dist = abs(targetTimeUs - candidate)
            if (dist <= thresholdUs && dist < closestDistance) {
                closestDistance = dist
                bestSnapTarget = candidate
            }
        }

        return if (bestSnapTarget != null) {
            SnapResult(
                snappedTimeUs = bestSnapTarget,
                didSnap = true,
                snapTargetUs = bestSnapTarget
            )
        } else {
            SnapResult(
                snappedTimeUs = targetTimeUs,
                didSnap = false
            )
        }
    }
}
