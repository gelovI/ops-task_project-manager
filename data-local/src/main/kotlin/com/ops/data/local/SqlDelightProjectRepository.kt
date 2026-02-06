package com.ops.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.ops.core.model.ProjectId
import com.ops.core.model.ProjectModel
import com.ops.core.usecase.ProjectRepository
import com.ops.db.OpsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlDelightProjectRepository(
    private val db: OpsDatabase
) : ProjectRepository {

    private val q = db.opsQueries

    override fun observeActive(): Flow<List<ProjectModel>> =
        q.selectActiveProjects()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun upsert(project: ProjectModel) = withContext(Dispatchers.IO) {
        q.upsertProject(
            id = project.id.value,
            name = project.name ?: "Unbenannt",
            color = project.color,
            updatedAt = project.updatedAt,
            deletedAt = project.deletedAt
        )
    }

    override suspend fun softDelete(id: ProjectId, deletedAt: Long, updatedAt: Long) =
        withContext(Dispatchers.IO) {
            q.softDeleteProject(
                deletedAt = deletedAt,
                updatedAt = updatedAt,
                id = id.value
            )
        }

    override suspend fun getById(id: ProjectId): ProjectModel? =
        withContext(Dispatchers.IO) {
            q.selectProjectById(id.value)
                .executeAsOneOrNull()
                ?.toDomain()
        }

    override suspend fun archive(id: ProjectId, now: Long) {
        q.archiveProject(
            deletedAt = now,
            updatedAt = now,
            id = id.value
        )
    }
}
