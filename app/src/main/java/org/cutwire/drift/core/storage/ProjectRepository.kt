package org.cutwire.drift.core.storage

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.cutwire.drift.core.database.dao.ProjectDao
import org.cutwire.drift.core.database.entity.ProjectEntity
import org.cutwire.drift.core.model.AspectRatio
import org.cutwire.drift.core.model.Project
import org.cutwire.drift.core.model.Track
import org.cutwire.drift.core.model.TrackType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface ProjectRepository {
    fun getAllProjects(): Flow<List<Project>>
    fun getRecentProjects(limit: Int = 10): Flow<List<Project>>
    suspend fun getProject(id: String): Project?
    suspend fun saveProject(project: Project)
    suspend fun deleteProject(id: String)
    suspend fun createNewProject(name: String, aspectRatio: AspectRatio): Project
}

@Singleton
class ProjectRepositoryImpl @Inject constructor(
    private val projectDao: ProjectDao,
    private val ioDispatcher: CoroutineDispatcher
) : ProjectRepository {

    override fun getAllProjects(): Flow<List<Project>> {
        return projectDao.getAllProjects().map { entities ->
            entities.map { entityToProject(it) }
        }
    }

    override fun getRecentProjects(limit: Int): Flow<List<Project>> {
        return projectDao.getRecentProjects(limit).map { entities ->
            entities.map { entityToProject(it) }
        }
    }

    override suspend fun getProject(id: String): Project? = withContext(ioDispatcher) {
        val entity = projectDao.getProjectById(id) ?: return@withContext null
        entityToProject(entity)
    }

    override suspend fun saveProject(project: Project) = withContext(ioDispatcher) {
        val serialized = ProjectSerializer.serializeProject(project)
        val entity = ProjectEntity(
            id = project.id,
            name = project.name,
            aspectRatio = project.aspectRatio,
            width = project.width,
            height = project.height,
            frameRate = project.frameRate,
            durationUs = project.durationUs,
            thumbnailUri = project.thumbnailUri,
            serializedData = serialized,
            createdAt = project.createdAt,
            updatedAt = System.currentTimeMillis(),
            schemaVersion = project.schemaVersion
        )
        projectDao.insertOrUpdate(entity)
    }

    override suspend fun deleteProject(id: String) = withContext(ioDispatcher) {
        projectDao.deleteProjectById(id)
    }

    override suspend fun createNewProject(name: String, aspectRatio: AspectRatio): Project = withContext(ioDispatcher) {
        val defaultVideoTrack = Track(
            id = UUID.randomUUID().toString(),
            type = TrackType.VIDEO,
            order = 0,
            name = "Main Video"
        )
        val defaultAudioTrack = Track(
            id = UUID.randomUUID().toString(),
            type = TrackType.AUDIO,
            order = 1,
            name = "Audio 1"
        )

        val project = Project(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "New Edit" },
            aspectRatio = aspectRatio,
            width = aspectRatio.defaultWidth,
            height = aspectRatio.defaultHeight,
            frameRate = 30,
            tracks = listOf(defaultVideoTrack, defaultAudioTrack),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        saveProject(project)
        project
    }

    private fun entityToProject(entity: ProjectEntity): Project {
        return try {
            ProjectSerializer.deserializeProject(entity.serializedData)
        } catch (_: Exception) {
            Project(
                id = entity.id,
                name = entity.name,
                aspectRatio = entity.aspectRatio,
                width = entity.width,
                height = entity.height,
                frameRate = entity.frameRate,
                thumbnailUri = entity.thumbnailUri,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                schemaVersion = entity.schemaVersion
            )
        }
    }
}
