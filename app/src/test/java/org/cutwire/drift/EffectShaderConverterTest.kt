package org.cutwire.drift

import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.ScaleAndRotateTransformation
import org.cutwire.drift.core.model.Clip
import org.cutwire.drift.core.model.ClipTransform
import org.cutwire.drift.core.model.EffectInstance
import org.cutwire.drift.core.model.EffectType
import org.cutwire.drift.core.model.Project
import org.cutwire.drift.core.model.Track
import org.cutwire.drift.core.model.TrackType
import org.cutwire.drift.engine.command.AddEffectCommand
import org.cutwire.drift.engine.command.CommandManager
import org.cutwire.drift.engine.command.RemoveEffectCommand
import org.cutwire.drift.engine.command.UpdateEffectCommand
import org.cutwire.drift.engine.render.EffectShaderConverter
import org.cutwire.drift.engine.render.TextBitmapRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(UnstableApi::class)
class EffectShaderConverterTest {

    private lateinit var converter: EffectShaderConverter
    private lateinit var commandManager: CommandManager
    private lateinit var testProject: Project
    private val clipId = "clip-effects-test"

    @Before
    fun setUp() {
        converter = EffectShaderConverter(TextBitmapRenderer())
        commandManager = CommandManager()

        val clip = Clip(
            id = clipId,
            assetId = "asset-1",
            timelineStartUs = 0L,
            sourceStartUs = 0L,
            sourceEndUs = 5_000_000L,
            durationUs = 5_000_000L
        )

        val track = Track(
            id = "track-1",
            type = TrackType.VIDEO,
            order = 0,
            clips = listOf(clip)
        )

        testProject = Project(
            name = "Effects Test",
            tracks = listOf(track)
        )
    }

    @Test
    fun testConvertTransform_generatesScaleAndRotateTransformation() {
        val transform = ClipTransform(
            scaleX = 1.5f,
            scaleY = 1.5f,
            rotation = 45f
        )

        val effects = converter.convertEffects(transform = transform)
        assertEquals(1, effects.size)
        assertTrue(effects[0] is ScaleAndRotateTransformation)
    }

    @Test
    fun testConvertBrightnessAndContrast_generatesHslAdjustmentAndContrast() {
        val brightness = EffectInstance(
            type = EffectType.BRIGHTNESS,
            parameters = mapOf("brightness" to 0.25f)
        )
        val contrast = EffectInstance(
            type = EffectType.CONTRAST,
            parameters = mapOf("contrast" to 0.4f)
        )

        val effects = converter.convertEffects(effects = listOf(brightness, contrast))
        assertEquals(2, effects.size)
        assertTrue(effects.any { it is HslAdjustment })
        assertTrue(effects.any { it is Contrast })
    }

    @Test
    fun testConvertSaturation_generatesHslAdjustment() {
        val saturation = EffectInstance(
            type = EffectType.SATURATION,
            parameters = mapOf("saturation" to 1.5f)
        )

        val effects = converter.convertEffects(effects = listOf(saturation))
        assertEquals(1, effects.size)
        assertTrue(effects[0] is HslAdjustment)
    }

    @Test
    fun testAddUpdateRemoveEffectCommand_undoRedoCycle() {
        // 1. Add Effect
        val effect = EffectInstance(
            type = EffectType.TEMPERATURE,
            parameters = mapOf("temperature" to 0.5f)
        )
        val addCmd = AddEffectCommand(clipId, effect)
        val projWithEffect = commandManager.execute(addCmd, testProject)

        val clipWithEffect = projWithEffect.tracks.first().clips.first()
        assertEquals(1, clipWithEffect.effects.size)
        assertEquals(EffectType.TEMPERATURE, clipWithEffect.effects.first().type)

        // 2. Update Effect Parameter
        val updateCmd = UpdateEffectCommand(
            clipId = clipId,
            effectId = effect.id,
            newParameters = mapOf("temperature" to 0.8f)
        )
        val projUpdated = commandManager.execute(updateCmd, projWithEffect)
        val clipUpdated = projUpdated.tracks.first().clips.first()
        assertEquals(0.8f, clipUpdated.effects.first().parameters["temperature"])

        // 3. Undo Update
        val projUndone = commandManager.undo(projUpdated)
        val clipUndone = projUndone.tracks.first().clips.first()
        assertEquals(0.5f, clipUndone.effects.first().parameters["temperature"])

        // 4. Undo Add
        val projRestored = commandManager.undo(projUndone)
        val clipRestored = projRestored.tracks.first().clips.first()
        assertEquals(0, clipRestored.effects.size)

        // 5. Redo Add
        val projRedone = commandManager.redo(projRestored)
        assertEquals(1, projRedone.tracks.first().clips.first().effects.size)
    }
}
