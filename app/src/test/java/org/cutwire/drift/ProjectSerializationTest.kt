package org.cutwire.drift

import org.cutwire.drift.core.model.*
import org.cutwire.drift.core.storage.ProjectSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProjectSerializationTest {

    @Test
    fun testProjectRoundTripSerialization() {
        val original = Project(
            id = "test-project-123",
            name = "My Travel Edit",
            aspectRatio = AspectRatio.RATIO_9_16,
            width = 1080,
            height = 1920,
            frameRate = 30,
            tracks = listOf(
                Track(
                    id = "track-v1",
                    type = TrackType.VIDEO,
                    order = 0,
                    name = "Main Video",
                    clips = listOf(
                        Clip(
                            id = "clip-1",
                            assetId = "asset-vid-1",
                            timelineStartUs = 0L,
                            sourceStartUs = 500_000L,
                            sourceEndUs = 3_500_000L,
                            durationUs = 3_000_000L,
                            transform = ClipTransform(scaleX = 1.2f, scaleY = 1.2f, rotation = 45f),
                            effects = listOf(
                                EffectInstance(
                                    type = EffectType.BRIGHTNESS,
                                    parameters = mapOf("brightness" to 0.25f)
                                )
                            )
                        )
                    )
                ),
                Track(
                    id = "track-a1",
                    type = TrackType.AUDIO,
                    order = 1,
                    name = "Music Track",
                    volume = 0.8f
                )
            ),
            assets = listOf(
                MediaAsset(
                    id = "asset-vid-1",
                    uriString = "content://media/external/video/media/42",
                    displayName = "drone_shot.mp4",
                    mediaType = MediaType.VIDEO,
                    durationUs = 10_000_000L,
                    width = 1920,
                    height = 1080
                )
            )
        )

        val json = ProjectSerializer.serializeProject(original)
        assertNotNull(json)

        val restored = ProjectSerializer.deserializeProject(json)

        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.aspectRatio, restored.aspectRatio)
        assertEquals(original.width, restored.width)
        assertEquals(original.height, restored.height)
        assertEquals(original.tracks.size, restored.tracks.size)
        assertEquals(original.assets.size, restored.assets.size)

        val clip = restored.tracks[0].clips[0]
        assertEquals("clip-1", clip.id)
        assertEquals(3_000_000L, clip.durationUs)
        assertEquals(1.2f, clip.transform.scaleX, 0.001f)
        assertEquals(45f, clip.transform.rotation, 0.001f)
        assertEquals(1, clip.effects.size)
        assertEquals(EffectType.BRIGHTNESS, clip.effects[0].type)
        assertEquals(0.25f, clip.effects[0].parameters["brightness"] ?: 0f, 0.001f)
    }
}
