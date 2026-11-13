package org.cutwire.drift.core.database.converter

import androidx.room.TypeConverter
import org.cutwire.drift.core.model.AspectRatio

class Converters {
    @TypeConverter
    fun fromAspectRatio(value: AspectRatio): String = value.name

    @TypeConverter
    fun toAspectRatio(value: String): AspectRatio = try {
        AspectRatio.valueOf(value)
    } catch (_: Exception) {
        AspectRatio.RATIO_16_9
    }
}
