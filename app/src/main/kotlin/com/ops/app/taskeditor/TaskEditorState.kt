package com.ops.app.taskeditor

import com.ops.core.model.*

data class TaskEditorState(
    val mode: Mode,
    val id: TaskId?,
    val title: String,
    val description: String,
    val status: TaskStatus,
    val priority: TaskPriority,
    val dueAt: Long?,
    val projectId: ProjectId?
) {
    enum class Mode { ADD, EDIT }
}
