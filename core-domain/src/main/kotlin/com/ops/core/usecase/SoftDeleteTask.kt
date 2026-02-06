package com.ops.core.usecase

import com.ops.core.model.TaskModel
import com.ops.core.util.TimeProvider

class SoftDeleteTask(
    private val time: TimeProvider = TimeProvider.system
) {
    fun execute(task: TaskModel): TaskModel {
        val now = time.now()
        return task.copy(
            deletedAt = now,
            updatedAt = now
        )
    }
}

