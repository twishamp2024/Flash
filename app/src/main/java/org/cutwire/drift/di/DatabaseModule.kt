package org.cutwire.drift.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.cutwire.drift.core.database.DriftDatabase
import org.cutwire.drift.core.database.dao.ProjectDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDriftDatabase(
        @ApplicationContext context: Context
    ): DriftDatabase {
        return Room.databaseBuilder(
            context,
            DriftDatabase::class.java,
            "drift_projects.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideProjectDao(database: DriftDatabase): ProjectDao {
        return database.projectDao()
    }
}
