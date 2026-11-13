package org.cutwire.drift.core.model

import java.util.UUID

data class ClipTransform(
    val positionX: Float = 0.0f,   // -1.0 to 1.0 normalized
    val positionY: Float = 0.0f,   // -1.0 to 1.0 normalized
    val scaleX: Float = 1.0f,
    val scaleY: Float = 1.0f,
    val rotation: Float = 0.0f,    // 0 to 360 degrees
    val anchorX: Float = 0.5f,     // 0.0 to 1.0 pivot
    val anchorY: Float = 0.5f
)

data class KeyframePoint(
    val timeOffsetUs: Long,
    val value: Float,
    val easing: EasingCurve = EasingCurve.LINEAR
)

data class KeyframeTrack(
    val propertyName: String,
    val keyframes: List<KeyframePoint> = emptyList()
)

data class Clip(
    val id: String = UUID.randomUUID().toString(),
    val assetId: String,
    val timelineStartUs: Long,
    val sourceStartUs: Long,
    val sourceEndUs: Long,
    val durationUs: Long,
    val playbackSpeed: Float = 1.0f,
    val transform: ClipTransform = ClipTransform(),
    val opacity: Float = 1.0f,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val audioFadeInUs: Long = 0L,
    val audioFadeOutUs: Long = 0L,
    val effects: List<EffectInstance> = emptyList(),
    val keyframes: List<KeyframeTrack> = emptyList(),
    val transitionIn: TransitionInstance? = null,
    val transitionOut: TransitionInstance? = null,
    val textOverlay: TextOverlay? = null
) {
    val timelineEndUs: Long
        get() = timelineStartUs + durationUs
}
