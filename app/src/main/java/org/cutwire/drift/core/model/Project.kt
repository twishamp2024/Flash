package org.cutwire.drift.core.model

import java.util.UUID

data class ExportSettings(
    val resolutionWidth: Int = 1920,
    val resolutionHeight: Int = 1080,
    val frameRate: Int = 30,
    val bitRateBps: Long = 10_000_000L, // 10 Mbps
    val mimeType: String = "video/avc",
    val audioBitRateBps: Int = 192_000,
    val audioSampleRate: Int = 44_100
)

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val aspectRatio: AspectRatio = AspectRatio.RATIO_16_9,
    val width: Int = 1920,
    val height: Int = 1080,
    val frameRate: Int = 30,
    val tracks: List<Track> = emptyList(),
    val assets: List<MediaAsset> = emptyList(),
    val exportSettings: ExportSettings = ExportSettings(),
    val thumbnailUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val schemaVersion: Int = 1
) {
    val durationUs: Long
        get() = tracks.maxOfOrNull { it.durationUs } ?: 0L
}
