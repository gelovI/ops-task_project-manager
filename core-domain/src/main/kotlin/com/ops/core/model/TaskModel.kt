package com.ops.core.model

data class TaskModel(
    val id: TaskId,
    val title: String,
    val description: String,
    val status: TaskStatus,
    val dueAt: Long?,          // epoch millis, optional
    val projectId: ProjectId?, // optional
    val priority: TaskPriority,
    val updatedAt: Long,       // epoch millis
    val deletedAt: Long?       // tombstone for offline-first deletes
) {
    val isDeleted: Boolean get() = deletedAt != null
    val isDone: Boolean get() = status == TaskStatus.DONE
}
