package org.cutwire.drift.core.model

import java.util.UUID

enum class TrackType {
    VIDEO,
    AUDIO,
    TEXT,
    OVERLAY
}

data class Track(
    val id: String = UUID.randomUUID().toString(),
    val type: TrackType,
    val order: Int,
    val name: String = "",
    val isMuted: Boolean = false,
    val isHidden: Boolean = false,
    val isLocked: Boolean = false,
    val volume: Float = 1.0f,
    val clips: List<Clip> = emptyList()
) {
    val durationUs: Long
        get() = clips.maxOfOrNull { it.timelineEndUs } ?: 0L
}
