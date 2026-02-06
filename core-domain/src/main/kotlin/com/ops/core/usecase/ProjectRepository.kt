package com.ops.core.usecase

import com.ops.core.model.ProjectId
import com.ops.core.model.ProjectModel
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {

    fun observeActive(): Flow<List<ProjectModel>>

    suspend fun getById(id: ProjectId): ProjectModel?

    suspend fun upsert(project: ProjectModel)

    suspend fun softDelete(id: ProjectId, deletedAt: Long, updatedAt: Long)
    suspend fun archive(id: ProjectId, now: Long)

}
