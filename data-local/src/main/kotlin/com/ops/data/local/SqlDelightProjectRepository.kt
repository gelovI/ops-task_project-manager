package com.ops.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.ops.core.model.ProjectId
import com.ops.core.model.ProjectModel
import com.ops.core.sync.ProjectSyncDto
import com.ops.core.usecase.ProjectRepository
import com.ops.db.OpsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject

class SqlDelightProjectRepository(
    private val db: OpsDatabase,
    private val outbox: SqlDelightOutboxWriter,
    private val json: Json = Json { ignoreUnknownKeys = true }
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
            name = project.name,
            color = project.color,
            updatedAt = project.updatedAt,
            deletedAt = project.deletedAt
        )

        val payload = JSONObject().apply {
            put("id", project.id.value)
            put("name", project.name)
            put("color", project.color)
            put("updatedAt", project.updatedAt)
            put("deletedAt", project.deletedAt)
        }.toString()

        q.enqueueOutbox(
            entity = "project",
            entityId = project.id.value,
            op = "UPSERT",
            updatedAt = project.updatedAt,
            payload = payload,
            enqueuedAt = System.currentTimeMillis()
        )
    }

    override suspend fun softDelete(id: ProjectId, deletedAt: Long, updatedAt: Long) = withContext(Dispatchers.IO) {
        q.softDeleteProject(id = id.value, deletedAt = deletedAt, updatedAt = updatedAt)

        q.enqueueOutbox(
            entity = "project",
            entityId = id.value,
            op = "DELETE",
            updatedAt = updatedAt,
            payload = null,
            enqueuedAt = System.currentTimeMillis()
        )
    }


    override suspend fun getById(id: ProjectId): ProjectModel? =
        withContext(Dispatchers.IO) {
            q.selectProjectById(id.value)
                .executeAsOneOrNull()
                ?.toDomain()
        }

    override suspend fun archive(id: ProjectId, now: Long) {
        softDelete(id, deletedAt = now, updatedAt = now)
    }
}
