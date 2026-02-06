package com.ops.core.usecase

import com.ops.core.model.*
import com.ops.core.util.IdGenerator
import com.ops.core.util.TimeProvider

class CreateTask(
    private val time: TimeProvider = TimeProvider.system
) {
    fun execute(
        title: String,
        description: String = "",
        projectId: ProjectId? = null,
        dueAt: Long? = null,
        priority: TaskPriority = TaskPriority.NORMAL
    ): TaskModel {
        val now = time.now()
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty()) { "title must not be blank" }

        return TaskModel(
            id = TaskId(IdGenerator.uuid()),
            title = cleanTitle,
            description = description,
            status = TaskStatus.OPEN,
            dueAt = dueAt,
            projectId = projectId,
            priority = priority,
            updatedAt = now,
            deletedAt = null
        )
    }
}
