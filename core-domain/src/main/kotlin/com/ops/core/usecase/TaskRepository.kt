package com.ops.core.usecase

import com.ops.core.model.TaskId
import com.ops.core.model.TaskModel
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeActive(): Flow<List<TaskModel>>
    suspend fun getById(id: TaskId): TaskModel?
    suspend fun upsert(task: TaskModel)
    suspend fun softDelete(id: TaskId, deletedAt: Long, updatedAt: Long)
}
