package org.cutwire.drift.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.cutwire.drift.core.database.converter.Converters
import org.cutwire.drift.core.database.dao.ProjectDao
import org.cutwire.drift.core.database.entity.ProjectEntity

@Database(
    entities = [ProjectEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class DriftDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}
