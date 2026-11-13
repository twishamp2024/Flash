package org.cutwire.drift.engine.command

import org.cutwire.drift.core.model.Clip
import org.cutwire.drift.core.model.EffectInstance
import org.cutwire.drift.core.model.Project
import org.cutwire.drift.core.model.TextOverlay
import org.cutwire.drift.core.model.Track
import org.cutwire.drift.core.model.TrackType
import java.util.UUID

/**
 * Base interface for all timeline editing commands.
 * Follows the Command Pattern for non-destructive, deterministic Undo/Redo.
 * Every command operates on immutable [Project] states.
 */
interface EditCommand {
    val description: String

    /**
     * Apply the edit command to produce a new Project state.
     */
    fun execute(project: Project): Project

    /**
     * Reverse the edit command to restore the previous Project state.
     */
    fun undo(project: Project): Project
}

/**
 * Splits a clip at a specified timeline position in microseconds.
 * Preserves playback speed, transforms, volume, effects, and source offsets.
 */
class SplitClipCommand(
    val clipId: String,
    val splitTimeUs: Long
) : EditCommand {
    override val description: String = "Split Clip"

    private var originalClip: Clip? = null
    private var originalTrackId: String? = null
    private var generatedRightClipId: String = UUID.randomUUID().toString()

    override fun execute(project: Project): Project {
        var foundTrack: Track? = null
        var foundClip: Clip? = null

        for (track in project.tracks) {
            val clip = track.clips.firstOrNull { it.id == clipId }
            if (clip != null) {
                foundTrack = track
                foundClip = clip
                break
            }
        }

        if (foundTrack == null || foundClip == null) return project

        // Verify split time is strictly within clip bounds
        if (splitTimeUs <= foundClip.timelineStartUs || splitTimeUs >= foundClip.timelineEndUs) {
            return project
        }

        originalClip = foundClip
        originalTrackId = foundTrack.id

        val elapsedTimelineUs = splitTimeUs - foundClip.timelineStartUs
        val elapsedSourceUs = (elapsedTimelineUs * foundClip.playbackSpeed).toLong()
        val splitSourceUs = foundClip.sourceStartUs + elapsedSourceUs

        val leftClip = foundClip.copy(
            sourceEndUs = splitSourceUs,
            durationUs = elapsedTimelineUs
        )

        val rightDurationTimelineUs = foundClip.durationUs - elapsedTimelineUs
        val rightClip = Clip(
            id = generatedRightClipId,
            assetId = foundClip.assetId,
            timelineStartUs = splitTimeUs,
            sourceStartUs = splitSourceUs,
            sourceEndUs = foundClip.sourceEndUs,
            durationUs = rightDurationTimelineUs,
            playbackSpeed = foundClip.playbackSpeed,
            transform = foundClip.transform,
            opacity = foundClip.opacity,
            volume = foundClip.volume,
            isMuted = foundClip.isMuted,
            effects = foundClip.effects,
            keyframes = foundClip.keyframes
        )

        val updatedClips = foundTrack.clips.flatMap { c ->
            if (c.id == clipId) listOf(leftClip, rightClip) else listOf(c)
        }

        val updatedTracks = project.tracks.map { t ->
            if (t.id == foundTrack.id) t.copy(clips = updatedClips) else t
        }

        return project.copy(
            tracks = updatedTracks,
            updatedAt = System.currentTimeMillis()
        )
    }

    override fun undo(project: Project): Project {
        val orig = originalClip ?: return project
        val trackId = originalTrackId ?: return project

        val updatedTracks = project.tracks.map { t ->
            if (t.id == trackId) {
                val restoredClips = t.clips.mapNotNull { c ->
                    when (c.id) {
                        orig.id -> orig
                        generatedRightClipId -> null
                        else -> c
                    }
                }
                t.copy(clips = restoredClips)
            } else t
        }

        return project.copy(
            tracks = updatedTracks,
            updatedAt = System.currentTimeMillis()
        )
    }
}

/**
 * Trims a clip's in/out points and updates its timeline position/duration.
 */
class TrimClipCommand(
    val clipId: String,
    val newSourceStartUs: Long,
    val newSourceEndUs: Long,
    val newTimelineStartUs: Long,
    val ripple: Boolean = false
) : EditCommand {
    override val description: String = "Trim Clip"

    private var previousClip: Clip? = null
    private var previousTrackClips: List<Clip>? = null
    private var affectedTrackId: String? = null

    override fun execute(project: Project): Project {
        var targetTrack: Track? = null
        var targetClip: Clip? = null

        for (track in project.tracks) {
            val clip = track.clips.firstOrNull { it.id == clipId }
            if (clip != null) {
                targetTrack = track
                targetClip = clip
                break
            }
        }

        if (targetTrack == null || targetClip == null) return project

        previousClip = targetClip
        previousTrackClips = targetTrack.clips
        affectedTrackId = targetTrack.id

        val newSourceDurationUs = (newSourceEndUs - newSourceStartUs).coerceAtLeast(100_000L)
        val newDurationUs = (newSourceDurationUs / targetClip.playbackSpeed).toLong()

        val updatedClip = targetClip.copy(
            timelineStartUs = newTimelineStartUs,
            sourceStartUs = newSourceStartUs,
            sourceEndUs = newSourceEndUs,
            durationUs = newDurationUs
        )

        val updatedClips = if (ripple) {
            val durationDeltaUs = newDurationUs - targetClip.durationUs
            targetTrack.clips.map { c ->
                when {
                    c.id == clipId -> updatedClip
                    c.timelineStartUs > targetClip.timelineStartUs -> {
                        c.copy(timelineStartUs = (c.timelineStartUs + durationDeltaUs).coerceAtLeast(0L))
                    }
                    else -> c
                }
            }
        } else {
            targetTrack.clips.map { c ->
                if (c.id == clipId) updatedClip else c
            }
        }

        val updatedTracks = project.tracks.map { t ->
            if (t.id == targetTrack.id) t.copy(clips = updatedClips) else t
        }

        return project.copy(
            tracks = updatedTracks,
            updatedAt = System.currentTimeMillis()
        )
    }

    override fun undo(project: Project): Project {
        val trackId = affectedTrackId ?: return project
        val prevClips = previousTrackClips ?: return project

        val updatedTracks = project.tracks.map { t ->
            if (t.id == trackId) t.copy(clips = prevClips) else t
        }

        return project.copy(
            tracks = updatedTracks,
            updatedAt = System.currentTimeMillis()
        )
    }
}

/**
 * Moves a clip to a new timeline start position, optionally across tracks.
 */
class MoveClipCommand(
    val clipId: String,
    val targetTrackId: String,
    val newTimelineStartUs: Long
) : EditCommand {
    override val description: String = "Move Clip"

    private var previousClip: Clip? = null
    private var sourceTrackId: String? = null

    override fun execute(project: Project): Project {
        var foundClip: Clip? = null
        var foundTrack: Track? = null

        for (track in project.tracks) {
            val clip = track.clips.firstOrNull { it.id == clipId }
            if (clip != null) {
                foundClip = clip
                foundTrack = track
                break
            }
        }

        if (foundClip == null || foundTrack == null) return project

        previousClip = foundClip
        sourceTrackId = foundTrack.id

        val movedClip = foundClip.copy(
            timelineStartUs = newTimelineStartUs.coerceAtLeast(0L)
        )

        val updatedTracks = project.tracks.map { track ->
            when {
                track.id == sourceTrackId && track.id == targetTrackId -> {
                    val updated = track.clips.map { if (it.id == clipId) movedClip else it }
                    track.copy(clips = updated.sortedBy { it.timelineStartUs })
                }
                track.id == sourceTrackId -> {
                    track.copy(clips = track.clips.filterNot { it.id == clipId })
                }
                track.id == targetTrackId -> {
                    track.copy(clips = (track.clips + movedClip).sortedBy { it.timelineStartUs })
                }
                else -> track
            }
        }

        return project.copy(
            tracks = updatedTracks,
            updatedAt = System.currentTimeMillis()
        )
    }

    override fun undo(project: Project): Project {
        val origClip = previousClip ?: return project
        val srcTrackId = sourceTrackId ?: return project

        val updatedTracks = project.tracks.map { track ->
            when {
                track.id == srcTrackId && track.id == targetTrackId -> {
                    val updated = track.clips.map { if (it.id == clipId) origClip else it }
                    track.copy(clips = updated.sortedBy { it.timelineStartUs })
                }
                track.id == targetTrackId -> {
                    track.copy(clips = track.clips.filterNot { it.id == clipId })
                }
                track.id == srcTrackId -> {
                    track.copy(clips = (track.clips + origClip).sortedBy { it.timelineStartUs })
                }
                else -> track
            }
        }

        return project.copy(
            tracks = updatedTracks,
            updatedAt = System.currentTimeMillis()
        )
    }
}

/**
 * Deletes a clip from a track.
 */
class DeleteClipCommand(
    val clipId: String
) : EditCommand {
    override val description: String = "Delete Clip"

    private var deletedClip: Clip? = null
    private var originalTrackId: String? = null
    private var originalIndex: Int = -1

    override fun execute(project: Project): Project {
        var foundTrack: Track? = null
        var foundClip: Clip? = null
        var index = -1

        for (track in project.tracks) {
            val idx = track.clips.indexOfFirst { it.id == clipId }
            if (idx != -1) {
                foundTrack = track
                foundClip = track.clips[idx]
                index = idx
                break
            }
        }

        if (foundTrack == null || foundClip == null) return project

        deletedClip = foundClip
        originalTrackId = foundTrack.id
        originalIndex = index

        val updatedTracks = project.tracks.map { track ->
            if (track.id == foundTrack.id) {
                track.copy(clips = track.clips.filterNot { it.id == clipId })
            } else track
        }

        return project.copy(
            tracks = updatedTracks,
            updatedAt = System.currentTimeMillis()
        )
    }

    override fun undo(project: Project): Project {
        val clip = deletedClip ?: return project
        val trackId = originalTrackId ?: return project

        val updatedTracks = project.tracks.map { track ->
            if (track.id == trackId) {
                val list = track.clips.toMutableList()
                val insertIndex = originalIndex.coerceIn(0, list.size)
                list.add(insertIndex, clip)
                track.copy(clips = list)
            } else track
        }

        return project.copy(
            tracks = updatedTracks,
            updatedAt = System.currentTimeMillis()
        )
    }
}

/**
 * Duplicates a clip, placing the copy immediately after the original.
 */
class DuplicateClipCommand(
    val clipId: String,
    val newClipId: String = UUID.randomUUID().toString()
) : EditCommand {
    override val description: String = "Duplicate Clip"

    private var duplicatedClipId: String = newClipId
    private var targetTrackId: String? = null

    override fun execute(project: Project): Project {
        var foundTrack: Track? = null
        var foundClip: Clip? = null

        for (track in project.tracks) {
            val clip = track.clips.firstOrNull { it.id == clipId }
            if (clip != null) {
                foundTrack = track
                foundClip = clip
                break
            }
        }

        if (foundTrack == null || foundClip == null) return project
        targetTrackId = foundTrack.id

        val duplicate = foundClip.copy(
            id = duplicatedClipId,
            timelineStartUs = foundClip.timelineEndUs
        )

        val updatedTracks = project.tracks.map { track ->
            if (track.id == foundTrack.id) {
                track.copy(clips = (track.clips + duplicate).sortedBy { it.timelineStartUs })
            } else track
        }

        return project.copy(
            tracks = updatedTracks,
            updatedAt = System.currentTimeMillis()
        )
    }

    override fun undo(project: Project): Project {
        val trackId = targetTrackId ?: return project

        val updatedTracks = project.tracks.map { track ->
            if (track.id == trackId) {
                track.copy(clips = track.clips.filterNot { it.id == duplicatedClipId })
            } else track
        }

        return project.copy(
            tracks = updatedTracks,
            updatedAt = System.currentTimeMillis()
        )
    }
}

/**
 * Adds a new track to the project.
 */
class AddTrackCommand(
    val track: Track
) : EditCommand {
    override val description: String = "Add Track"

    override fun execute(project: Project): Project {
        val updatedTracks = project.tracks + track
        return project.copy(
            tracks = updatedTracks.sortedBy { it.order },
            updatedAt = System.currentTimeMillis()
        )
    }

    override fun undo(project: Project): Project {
        return project.copy(
            tracks = project.tracks.filterNot { it.id == track.id },
            updatedAt = System.currentTimeMillis()
        )
    }
}

/**
 * Deletes a track from the project.
 */
class DeleteTrackCommand(
    val trackId: String
) : EditCommand {
    override val description: String = "Delete Track"

    private var deletedTrack: Track? = null

    override fun execute(project: Project): Project {
        deletedTrack = project.tracks.firstOrNull { it.id == trackId }
        return project.copy(
            tracks = project.tracks.filterNot { it.id == trackId },
            updatedAt = System.currentTimeMillis()
        )
    }

    override fun undo(project: Project): Project {
        val track = deletedTrack ?: return project
        val updated = (project.tracks + track).sortedBy { it.order }
        return project.copy(
            tracks = updated,
            updatedAt = System.currentTimeMillis()
        )
    }
}

/**
 * Adds an effect to a clip.
 */
class AddEffectCommand(
    val clipId: String,
    val effect: EffectInstance
) : EditCommand {
    override val description: String = "Add ${effect.type.displayName}"

    override fun execute(project: Project): Project {
        val updatedTracks = project.tracks.map { track ->
            val updatedClips = track.clips.map { clip ->
                if (clip.id == clipId) {
                    clip.copy(effects = clip.effects + effect)
                } else clip
            }
            track.copy(clips = updatedClips)
        }
        return project.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
    }

    override fun undo(project: Project): Project {
        val updatedTracks = project.tracks.map { track ->
            val updatedClips = track.clips.map { clip ->
                if (clip.id == clipId) {
                    clip.copy(effects = clip.effects.filterNot { it.id == effect.id })
                } else clip
            }
            track.copy(clips = updatedClips)
        }
        return project.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
    }
}

/**
 * Updates parameters of an existing effect on a clip.
 */
class UpdateEffectCommand(
    val clipId: String,
    val effectId: String,
    val newParameters: Map<String, Float>
) : EditCommand {
    override val description: String = "Update Effect"
    private var previousParameters: Map<String, Float>? = null

    override fun execute(project: Project): Project {
        val updatedTracks = project.tracks.map { track ->
            val updatedClips = track.clips.map { clip ->
                if (clip.id == clipId) {
                    val updatedEffects = clip.effects.map { eff ->
                        if (eff.id == effectId) {
                            if (previousParameters == null) previousParameters = eff.parameters
                            eff.copy(parameters = newParameters)
                        } else eff
                    }
                    clip.copy(effects = updatedEffects)
                } else clip
            }
            track.copy(clips = updatedClips)
        }
        return project.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
    }

    override fun undo(project: Project): Project {
        val prevParams = previousParameters ?: return project
        val updatedTracks = project.tracks.map { track ->
            val updatedClips = track.clips.map { clip ->
                if (clip.id == clipId) {
                    val updatedEffects = clip.effects.map { eff ->
                        if (eff.id == effectId) {
                            eff.copy(parameters = prevParams)
                        } else eff
                    }
                    clip.copy(effects = updatedEffects)
                } else clip
            }
            track.copy(clips = updatedClips)
        }
        return project.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
    }
}

/**
 * Removes an effect from a clip.
 */
class RemoveEffectCommand(
    val clipId: String,
    val effectId: String
) : EditCommand {
    override val description: String = "Remove Effect"

    private var removedEffect: EffectInstance? = null
    private var originalIndex: Int = -1

    override fun execute(project: Project): Project {
        val updatedTracks = project.tracks.map { track ->
            val updatedClips = track.clips.map { clip ->
                if (clip.id == clipId) {
                    val idx = clip.effects.indexOfFirst { it.id == effectId }
                    if (idx != -1) {
                        removedEffect = clip.effects[idx]
                        originalIndex = idx
                    }
                    clip.copy(effects = clip.effects.filterNot { it.id == effectId })
                } else clip
            }
            track.copy(clips = updatedClips)
        }
        return project.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
    }

    override fun undo(project: Project): Project {
        val effect = removedEffect ?: return project
        val updatedTracks = project.tracks.map { track ->
            val updatedClips = track.clips.map { clip ->
                if (clip.id == clipId) {
                    val list = clip.effects.toMutableList()
                    val insertIdx = originalIndex.coerceIn(0, list.size)
                    list.add(insertIdx, effect)
                    clip.copy(effects = list)
                } else clip
            }
            track.copy(clips = updatedClips)
        }
        return project.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
    }
}

/**
 * A compound command that groups multiple edit commands into a single atomic undo/redo operation.
 */
class CompoundCommand(
    val commands: List<EditCommand>,
    override val description: String = "Compound Action"
) : EditCommand {
    override fun execute(project: Project): Project {
        var current = project
        for (cmd in commands) {
            current = cmd.execute(current)
        }
        return current
    }

    override fun undo(project: Project): Project {
        var current = project
        for (cmd in commands.reversed()) {
            current = cmd.undo(current)
        }
        return current
    }
}

/**
 * Updates a clip's audio volume (0.0f to 2.0f).
 */
class UpdateClipVolumeCommand(
    val clipId: String,
    val newVolume: Float
) : EditCommand {
    override val description: String = "Change Volume"
    private var previousVolume: Float? = null

    override fun execute(project: Project): Project {
        val updatedTracks = project.tracks.map { track ->
            val updatedClips = track.clips.map { clip ->
                if (clip.id == clipId) {
                    if (previousVolume == null) previousVolume = clip.volume
                    clip.copy(volume = newVolume.coerceIn(0.0f, 2.0f))
                } else clip
            }
            track.copy(clips = updatedClips)
        }
        return project.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
    }

    override fun undo(project: Project): Project {
        val prev = previousVolume ?: return project
        val updatedTracks = project.tracks.map { track ->
            val updatedClips = track.clips.map { clip ->
                if (clip.id == clipId) clip.copy(volume = prev) else clip
            }
            track.copy(clips = updatedClips)
        }
        return project.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
    }
}

/**
 * Toggles a clip's mute state.
 */
class ToggleClipMuteCommand(
    val clipId: String
) : EditCommand {
    override val description: String = "Toggle Clip Mute"
    private var previousMute: Boolean? = null

    override fun execute(project: Project): Project {
        val updatedTracks = project.tracks.map { track ->
            val updatedClips = track.clips.map { clip ->
                if (clip.id == clipId) {
                    if (previousMute == null) previousMute = clip.isMuted
                    clip.copy(isMuted = !clip.isMuted)
                } else clip
            }
            track.copy(clips = updatedClips)
        }
        return project.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
    }

    override fun undo(project: Project): Project {
        val prev = previousMute ?: return project
        val updatedTracks = project.tracks.map { track ->
            val updatedClips = track.clips.map { clip ->
                if (clip.id == clipId) clip.copy(isMuted = prev) else clip
            }
            track.copy(clips = updatedClips)
        }
        return project.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
    }
}

/**
 * Updates a clip's playback speed (0.25x to 4.0x), recalculating its timeline duration.
 */
class UpdateClipSpeedCommand(
    val clipId: String,
    val newSpeed: Float
) : EditCommand {
    override val description: String = "Change Speed"
    private var previousSpeed: Float? = null
    private var previousDurationUs: Long? = null

    override fun execute(project: Project): Project {
        val speed = newSpeed.coerceIn(0.25f, 4.0f)
        val updatedTracks = project.tracks.map { track ->
            val updatedClips = track.clips.map { clip ->
                if (clip.id == clipId) {
                    if (previousSpeed == null) {
                        previousSpeed = clip.playbackSpeed
                        previousDurationUs = clip.durationUs
                    }
                    val sourceSpanUs = clip.sourceEndUs - clip.sourceStartUs
                    val newDurationUs = (sourceSpanUs / speed).toLong().coerceAtLeast(100_000L)
                    clip.copy(playbackSpeed = speed, durationUs = newDurationUs)
                } else clip
            }
            track.copy(clips = updatedClips)
        }
        return project.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
    }

    override fun undo(project: Project): Project {
        val prevSpeed = previousSpeed ?: return project
        val prevDuration = previousDurationUs ?: return project
        val updatedTracks = project.tracks.map { track ->
            val updatedClips = track.clips.map { clip ->
                if (clip.id == clipId) clip.copy(playbackSpeed = prevSpeed, durationUs = prevDuration) else clip
            }
            track.copy(clips = updatedClips)
        }
        return project.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
    }
}

/**
 * Updates audio fade-in and fade-out durations.
 */
class UpdateClipFadeCommand(
    val clipId: String,
    val fadeInUs: Long,
    val fadeOutUs: Long
) : EditCommand {
    override val description: String = "Update Audio Fade"
    private var previousFadeIn: Long? = null
    private var previousFadeOut: Long? = null

    override fun execute(project: Project): Project {
        val updatedTracks = project.tracks.map { track ->
            val updatedClips = track.clips.map { clip ->
                if (clip.id == clipId) {
                    if (previousFadeIn == null) {
                        previousFadeIn = clip.audioFadeInUs
                        previousFadeOut = clip.audioFadeOutUs
                    }
                    clip.copy(audioFadeInUs = fadeInUs, audioFadeOutUs = fadeOutUs)
                } else clip
            }
            track.copy(clips = updatedClips)
        }
        return project.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
    }

    override fun undo(project: Project): Project {
        val prevIn = previousFadeIn ?: return project
        val prevOut = previousFadeOut ?: return project
        val updatedTracks = project.tracks.map { track ->
            val updatedClips = track.clips.map { clip ->
                if (clip.id == clipId) clip.copy(audioFadeInUs = prevIn, audioFadeOutUs = prevOut) else clip
            }
            track.copy(clips = updatedClips)
        }
        return project.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
    }
}

/**
 * Detaches audio from a video clip by:
 * 1. Muting the original video clip.
 * 2. Creating a linked audio clip on an AUDIO track with the same timing.
 */
class DetachAudioCommand(
    val clipId: String
) : EditCommand {
    override val description: String = "Detach Audio"
    private var createdAudioClipId: String? = null
    private var targetTrackId: String? = null

    override fun execute(project: Project): Project {
        var sourceClip: Clip? = null
        for (track in project.tracks) {
            val clip = track.clips.firstOrNull { it.id == clipId }
            if (clip != null) {
                sourceClip = clip
                break
            }
        }
        if (sourceClip == null) return project

        var audioTrack = project.tracks.firstOrNull { it.type == TrackType.AUDIO }
        val tracksList = project.tracks.toMutableList()

        if (audioTrack == null) {
            val newTrack = Track(
                type = TrackType.AUDIO,
                order = tracksList.size,
                name = "Audio ${tracksList.count { it.type == TrackType.AUDIO } + 1}"
            )
            tracksList.add(newTrack)
            audioTrack = newTrack
        }
        targetTrackId = audioTrack.id

        val audioClip = Clip(
            assetId = sourceClip.assetId,
            timelineStartUs = sourceClip.timelineStartUs,
            sourceStartUs = sourceClip.sourceStartUs,
            sourceEndUs = sourceClip.sourceEndUs,
            durationUs = sourceClip.durationUs,
            volume = sourceClip.volume,
            playbackSpeed = sourceClip.playbackSpeed
        )
        createdAudioClipId = audioClip.id

        val updatedTracks = tracksList.map { track ->
            when (track.id) {
                audioTrack.id -> track.copy(clips = (track.clips + audioClip).sortedBy { it.timelineStartUs })
                else -> {
                    val updatedClips = track.clips.map { clip ->
                        if (clip.id == clipId) clip.copy(isMuted = true) else clip
                    }
                    track.copy(clips = updatedClips)
                }
            }
        }

        return project.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
    }

    override fun undo(project: Project): Project {
        val audioClipId = createdAudioClipId ?: return project
        val updatedTracks = project.tracks.map { track ->
            val unmutedClips = track.clips.map { clip ->
                if (clip.id == clipId) clip.copy(isMuted = false) else clip
            }
            track.copy(clips = unmutedClips.filterNot { it.id == audioClipId })
        }
        return project.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
    }
}

/**
 * Adds a new text overlay clip on a TEXT track.
 */
class AddTextClipCommand(
    val textOverlay: TextOverlay,
    val timelineStartUs: Long,
    val durationUs: Long = 3_000_000L
) : EditCommand {
    override val description: String = "Add Text"
    private var createdClipId: String? = null
    private var targetTrackId: String? = null

    override fun execute(project: Project): Project {
        var textTrack = project.tracks.firstOrNull { it.type == TrackType.TEXT }
        val tracksList = project.tracks.toMutableList()

        if (textTrack == null) {
            val newTrack = Track(
                type = TrackType.TEXT,
                order = tracksList.size,
                name = "Text ${tracksList.count { it.type == TrackType.TEXT } + 1}"
            )
            tracksList.add(newTrack)
            textTrack = newTrack
        }
        targetTrackId = textTrack.id

        val textClip = Clip(
            assetId = textOverlay.id,
            timelineStartUs = timelineStartUs,
            sourceStartUs = 0L,
            sourceEndUs = durationUs,
            durationUs = durationUs,
            textOverlay = textOverlay
        )
        createdClipId = textClip.id

        val updatedTracks = tracksList.map { track ->
            if (track.id == textTrack.id) {
                track.copy(clips = (track.clips + textClip).sortedBy { it.timelineStartUs })
            } else track
        }

        return project.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
    }

    override fun undo(project: Project): Project {
        val clipId = createdClipId ?: return project
        val trackId = targetTrackId ?: return project
        val updatedTracks = project.tracks.map { track ->
            if (track.id == trackId) {
                track.copy(clips = track.clips.filterNot { it.id == clipId })
            } else track
        }
        return project.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
    }
}
