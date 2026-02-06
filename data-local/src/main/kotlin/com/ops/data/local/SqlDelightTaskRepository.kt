package com.ops.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.ops.core.model.TaskId
import com.ops.core.model.TaskModel
import com.ops.core.usecase.TaskRepository
import com.ops.db.OpsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlDelightTaskRepository(
    private val db: OpsDatabase
) : TaskRepository {

    private val q = db.opsQueries

    override fun observeActive(): Flow<List<TaskModel>> =
        q.selectActiveTasks()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: TaskId): TaskModel? =
        withContext(Dispatchers.IO) {
            q.selectTaskById(id.value)
                .executeAsOneOrNull()
                ?.toDomain()
        }

    override suspend fun upsert(task: TaskModel) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val effectiveUpdatedAt = if (task.updatedAt > 0) task.updatedAt else now

            val payload = org.json.JSONObject()
                .put("id", task.id.value)
                .put("title", task.title)
                .put("description", task.description)
                .put("status", task.status.name)
                .put("dueAt", task.dueAt) // null ok
                .put("projectId", task.projectId?.value) // null ok
                .put("priority", task.priority.value)
                .put("updatedAt", task.updatedAt)
                .put("deletedAt", task.deletedAt) // null ok
                .toString()

            db.transaction {
                q.upsertTask(
                    id = task.id.value,
                    title = task.title,
                    description = task.description,
                    status = task.status.name,
                    dueAt = task.dueAt,
                    projectId = task.projectId?.value,
                    priority = task.priority.value.toLong(),
                    updatedAt = effectiveUpdatedAt,
                    deletedAt = task.deletedAt
                )

                q.enqueueOutbox(
                    entity = "task",
                    entityId = task.id.value,
                    op = "UPSERT",
                    updatedAt = effectiveUpdatedAt,
                    payload = payload,
                    enqueuedAt = now
                )
            }
        }

    override suspend fun softDelete(id: TaskId, deletedAt: Long, updatedAt: Long) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()

            db.transaction {
                q.softDeleteTask(
                    deletedAt = deletedAt,
                    updatedAt = updatedAt,
                    id = id.value
                )

                q.enqueueOutbox(
                    entity = "task",
                    entityId = id.value,
                    op = "DELETE",
                    updatedAt = updatedAt,
                    payload = null,
                    enqueuedAt = now
                )
            }
        }
}
