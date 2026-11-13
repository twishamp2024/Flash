package org.cutwire.drift.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.cutwire.drift.core.model.AspectRatio

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val aspectRatio: AspectRatio,
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val durationUs: Long,
    val thumbnailUri: String?,
    val serializedData: String, // Complete JSON payload of tracks, clips, effects & assets
    val createdAt: Long,
    val updatedAt: Long,
    val schemaVersion: Int
)
