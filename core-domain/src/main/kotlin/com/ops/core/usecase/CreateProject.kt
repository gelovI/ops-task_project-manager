package com.ops.core.usecase

import com.ops.core.model.ProjectId
import com.ops.core.model.ProjectModel
import com.ops.core.util.ColorUtil
import com.ops.core.util.IdGenerator
import com.ops.core.util.TimeProvider

class CreateProject(
    private val repo: ProjectRepository,
    private val time: TimeProvider
) {
    suspend fun run(name: String): ProjectId {
        val now = time.now()
        val id = ProjectId(IdGenerator.uuid())

        val color = ColorUtil.pick(name.trim().hashCode() and 0x7fffffff)

        repo.upsert(
            ProjectModel(
                id = id,
                name = name.trim(),
                color = color,
                updatedAt = now,
                deletedAt = null
            )
        )
        return id
    }
}
