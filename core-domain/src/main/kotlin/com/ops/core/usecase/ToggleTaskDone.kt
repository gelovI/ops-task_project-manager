package com.ops.core.usecase

import com.ops.core.model.TaskModel
import com.ops.core.model.TaskStatus
import com.ops.core.util.TimeProvider

class ToggleTaskDone(
    private val time: TimeProvider = TimeProvider.system
) {
    fun execute(task: TaskModel, done: Boolean): TaskModel {
        val now = time.now()
        return task.copy(
            status = if (done) TaskStatus.DONE else TaskStatus.OPEN,
            updatedAt = now
        )
    }
}
