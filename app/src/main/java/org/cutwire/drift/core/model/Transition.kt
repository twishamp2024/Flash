package org.cutwire.drift.core.model

import java.util.UUID

enum class TransitionType(val displayName: String) {
    CROSSFADE("Cross Dissolve"),
    DIP_BLACK("Dip to Black"),
    DIP_WHITE("Dip to White"),
    WIPE_LEFT("Wipe Left"),
    WIPE_RIGHT("Wipe Right"),
    WIPE_UP("Wipe Up"),
    WIPE_DOWN("Wipe Down"),
    PUSH_LEFT("Push Left"),
    PUSH_RIGHT("Push Right"),
    ZOOM_IN("Zoom In"),
    ZOOM_OUT("Zoom Out")
}

enum class EasingCurve(val displayName: String) {
    LINEAR("Linear"),
    EASE_IN("Ease In"),
    EASE_OUT("Ease Out"),
    EASE_IN_OUT("Ease In/Out"),
    BOUNCE("Bounce"),
    ELASTIC("Elastic")
}

data class TransitionInstance(
    val id: String = UUID.randomUUID().toString(),
    val type: TransitionType,
    val durationUs: Long = 500_000L, // Default 0.5s
    val easing: EasingCurve = EasingCurve.EASE_IN_OUT
)
