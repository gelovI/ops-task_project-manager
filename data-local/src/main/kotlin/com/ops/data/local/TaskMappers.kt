package com.ops.data.local

import com.ops.core.model.*
import com.ops.db.Task

internal fun Task.toDomain(): TaskModel =
    TaskModel(
        id = TaskId(id),
        title = title,
        description = description,
        status = runCatching { TaskStatus.valueOf(status) }.getOrDefault(TaskStatus.OPEN),
        dueAt = dueAt,
        projectId = projectId?.let { ProjectId(it) },
        priority = TaskPriority.fromInt(priority.toInt()),
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )
