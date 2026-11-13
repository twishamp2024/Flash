package org.cutwire.drift

import org.cutwire.drift.core.model.Clip
import org.cutwire.drift.core.model.MediaAsset
import org.cutwire.drift.core.model.MediaType
import org.cutwire.drift.core.model.Project
import org.cutwire.drift.core.model.Track
import org.cutwire.drift.core.model.TrackType
import org.cutwire.drift.engine.command.AddTrackCommand
import org.cutwire.drift.engine.command.CommandManager
import org.cutwire.drift.engine.command.CompoundCommand
import org.cutwire.drift.engine.command.DeleteClipCommand
import org.cutwire.drift.engine.command.DeleteTrackCommand
import org.cutwire.drift.engine.command.DetachAudioCommand
import org.cutwire.drift.engine.command.DuplicateClipCommand
import org.cutwire.drift.engine.command.MoveClipCommand
import org.cutwire.drift.engine.command.SplitClipCommand
import org.cutwire.drift.engine.command.ToggleClipMuteCommand
import org.cutwire.drift.engine.command.TrimClipCommand
import org.cutwire.drift.engine.command.UpdateClipFadeCommand
import org.cutwire.drift.engine.command.UpdateClipSpeedCommand
import org.cutwire.drift.engine.command.UpdateClipVolumeCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class CommandManagerTest {

    private lateinit var commandManager: CommandManager
    private lateinit var sampleProject: Project
    private val clip1Id = "clip-1"
    private val clip2Id = "clip-2"
    private val track1Id = "track-1"
    private val asset1Id = "asset-1"

    @Before
    fun setUp() {
        commandManager = CommandManager()

        val asset = MediaAsset(
            id = asset1Id,
            uriString = "file:///dummy/video.mp4",
            displayName = "video.mp4",
            mediaType = MediaType.VIDEO,
            durationUs = 10_000_000L, // 10 seconds
            width = 1920,
            height = 1080
        )

        val clip1 = Clip(
            id = clip1Id,
            assetId = asset1Id,
            timelineStartUs = 0L,
            sourceStartUs = 0L,
            sourceEndUs = 4_000_000L,
            durationUs = 4_000_000L
        )

        val clip2 = Clip(
            id = clip2Id,
            assetId = asset1Id,
            timelineStartUs = 4_000_000L,
            sourceStartUs = 4_000_000L,
            sourceEndUs = 10_000_000L,
            durationUs = 6_000_000L
        )

        val track = Track(
            id = track1Id,
            type = TrackType.VIDEO,
            order = 0,
            clips = listOf(clip1, clip2)
        )

        sampleProject = Project(
            name = "Test Project",
            tracks = listOf(track),
            assets = listOf(asset)
        )
    }

    @Test
    fun testSplitClip_executesAndUndoesCorrectly() {
        // Split clip1 (0..4s) at 1.5s
        val splitCommand = SplitClipCommand(clipId = clip1Id, splitTimeUs = 1_500_000L)
        val projectAfterSplit = commandManager.execute(splitCommand, sampleProject)

        val track = projectAfterSplit.tracks.first { it.id == track1Id }
        assertEquals(3, track.clips.size)

        val leftClip = track.clips[0]
        val rightClip = track.clips[1]

        assertEquals(clip1Id, leftClip.id)
        assertEquals(0L, leftClip.timelineStartUs)
        assertEquals(1_500_000L, leftClip.durationUs)
        assertEquals(1_500_000L, leftClip.sourceEndUs)

        assertEquals(1_500_000L, rightClip.timelineStartUs)
        assertEquals(2_500_000L, rightClip.durationUs)
        assertEquals(1_500_000L, rightClip.sourceStartUs)
        assertEquals(4_000_000L, rightClip.sourceEndUs)

        assertTrue(commandManager.canUndo.value)
        assertFalse(commandManager.canRedo.value)

        // Undo
        val projectAfterUndo = commandManager.undo(projectAfterSplit)
        val trackAfterUndo = projectAfterUndo.tracks.first { it.id == track1Id }
        assertEquals(2, trackAfterUndo.clips.size)
        assertEquals(clip1Id, trackAfterUndo.clips[0].id)
        assertEquals(4_000_000L, trackAfterUndo.clips[0].durationUs)

        assertFalse(commandManager.canUndo.value)
        assertTrue(commandManager.canRedo.value)

        // Redo
        val projectAfterRedo = commandManager.redo(projectAfterUndo)
        val trackAfterRedo = projectAfterRedo.tracks.first { it.id == track1Id }
        assertEquals(3, trackAfterRedo.clips.size)
    }

    @Test
    fun testTrimClip_executesAndUndoesCorrectly() {
        val trimCommand = TrimClipCommand(
            clipId = clip1Id,
            newSourceStartUs = 1_000_000L,
            newSourceEndUs = 3_000_000L,
            newTimelineStartUs = 0L,
            ripple = false
        )

        val trimmedProject = commandManager.execute(trimCommand, sampleProject)
        val clip = trimmedProject.tracks.first().clips.first { it.id == clip1Id }
        assertEquals(1_000_000L, clip.sourceStartUs)
        assertEquals(3_000_000L, clip.sourceEndUs)
        assertEquals(2_000_000L, clip.durationUs)

        val undone = commandManager.undo(trimmedProject)
        val restoredClip = undone.tracks.first().clips.first { it.id == clip1Id }
        assertEquals(0L, restoredClip.sourceStartUs)
        assertEquals(4_000_000L, restoredClip.sourceEndUs)
        assertEquals(4_000_000L, restoredClip.durationUs)
    }

    @Test
    fun testMoveClip_executesAndUndoesCorrectly() {
        val moveCommand = MoveClipCommand(
            clipId = clip1Id,
            targetTrackId = track1Id,
            newTimelineStartUs = 12_000_000L
        )

        val movedProject = commandManager.execute(moveCommand, sampleProject)
        val clip = movedProject.tracks.first().clips.first { it.id == clip1Id }
        assertEquals(12_000_000L, clip.timelineStartUs)

        val undone = commandManager.undo(movedProject)
        val restored = undone.tracks.first().clips.first { it.id == clip1Id }
        assertEquals(0L, restored.timelineStartUs)
    }

    @Test
    fun testDeleteClip_executesAndUndoesCorrectly() {
        val deleteCommand = DeleteClipCommand(clipId = clip1Id)

        val deletedProject = commandManager.execute(deleteCommand, sampleProject)
        val track = deletedProject.tracks.first()
        assertEquals(1, track.clips.size)
        assertEquals(clip2Id, track.clips[0].id)

        val undone = commandManager.undo(deletedProject)
        val restoredTrack = undone.tracks.first()
        assertEquals(2, restoredTrack.clips.size)
        assertEquals(clip1Id, restoredTrack.clips[0].id)
    }

    @Test
    fun testDuplicateClip_executesAndUndoesCorrectly() {
        val dupId = "dup-clip-1"
        val duplicateCommand = DuplicateClipCommand(clipId = clip1Id, newClipId = dupId)

        val duplicatedProject = commandManager.execute(duplicateCommand, sampleProject)
        val track = duplicatedProject.tracks.first()
        assertEquals(3, track.clips.size)
        val dup = track.clips.firstOrNull { it.id == dupId }
        assertNotNull(dup)
        assertEquals(4_000_000L, dup!!.timelineStartUs)

        val undone = commandManager.undo(duplicatedProject)
        val restoredTrack = undone.tracks.first()
        assertEquals(2, restoredTrack.clips.size)
        assertNull(restoredTrack.clips.firstOrNull { it.id == dupId })
    }

    @Test
    fun testCompoundCommand_executesAndUndoesInReverseOrder() {
        val addTrackCmd = AddTrackCommand(
            Track(id = "audio-track-1", type = TrackType.AUDIO, order = 1)
        )
        val deleteClipCmd = DeleteClipCommand(clipId = clip2Id)

        val compoundCmd = CompoundCommand(
            description = "Add Audio and Remove Clip 2",
            commands = listOf(addTrackCmd, deleteClipCmd)
        )

        val resultProject = commandManager.execute(compoundCmd, sampleProject)
        assertEquals(2, resultProject.tracks.size)
        assertEquals(1, resultProject.tracks.first().clips.size)

        val undone = commandManager.undo(resultProject)
        assertEquals(1, undone.tracks.size)
        assertEquals(2, undone.tracks.first().clips.size)
    }

    @Test
    fun testUpdateClipVolumeAndMute_undoRedo() {
        val volCmd = UpdateClipVolumeCommand(clip1Id, 1.5f)
        val projWithVol = commandManager.execute(volCmd, sampleProject)
        val clipVol = projWithVol.tracks.first().clips.first { it.id == clip1Id }
        assertEquals(1.5f, clipVol.volume)

        val muteCmd = ToggleClipMuteCommand(clip1Id)
        val projMuted = commandManager.execute(muteCmd, projWithVol)
        val clipMuted = projMuted.tracks.first().clips.first { it.id == clip1Id }
        assertTrue(clipMuted.isMuted)

        val unMuted = commandManager.undo(projMuted)
        assertFalse(unMuted.tracks.first().clips.first { it.id == clip1Id }.isMuted)

        val restored = commandManager.undo(unMuted)
        assertEquals(1.0f, restored.tracks.first().clips.first { it.id == clip1Id }.volume)
    }

    @Test
    fun testUpdateClipSpeed_recalculatesDuration() {
        val speedCmd = UpdateClipSpeedCommand(clip1Id, 2.0f)
        val projFast = commandManager.execute(speedCmd, sampleProject)
        val clipFast = projFast.tracks.first().clips.first { it.id == clip1Id }
        assertEquals(2.0f, clipFast.playbackSpeed)
        assertEquals(2_000_000L, clipFast.durationUs)

        val restored = commandManager.undo(projFast)
        val clipRestored = restored.tracks.first().clips.first { it.id == clip1Id }
        assertEquals(1.0f, clipRestored.playbackSpeed)
        assertEquals(4_000_000L, clipRestored.durationUs)
    }

    @Test
    fun testDetachAudioCommand_createsAudioTrackAndMutesSource() {
        val detachCmd = DetachAudioCommand(clip1Id)
        val detachedProj = commandManager.execute(detachCmd, sampleProject)

        val sourceClip = detachedProj.tracks.first().clips.first { it.id == clip1Id }
        assertTrue(sourceClip.isMuted)

        val audioTrack = detachedProj.tracks.firstOrNull { it.type == TrackType.AUDIO }
        assertNotNull(audioTrack)
        assertEquals(1, audioTrack!!.clips.size)
        assertEquals(sourceClip.timelineStartUs, audioTrack.clips.first().timelineStartUs)
        assertEquals(sourceClip.durationUs, audioTrack.clips.first().durationUs)

        val restored = commandManager.undo(detachedProj)
        val restoredSourceClip = restored.tracks.first().clips.first { it.id == clip1Id }
        assertFalse(restoredSourceClip.isMuted)
    }
}
