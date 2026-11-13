package org.cutwire.drift.core.model

import java.util.UUID

enum class MediaType {
    VIDEO,
    AUDIO,
    IMAGE
}

data class MediaAsset(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String,
    val displayName: String,
    val mediaType: MediaType,
    val durationUs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 30.0f,
    val bitRate: Long = 0L,
    val mimeType: String = "",
    val thumbnailPath: String? = null,
    val dateAdded: Long = System.currentTimeMillis()
)
