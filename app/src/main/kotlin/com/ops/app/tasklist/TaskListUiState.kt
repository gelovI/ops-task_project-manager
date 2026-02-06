package com.ops.app.tasklist

import com.ops.core.model.TaskModel

data class SyncUiState(
    val syncing: Boolean = false,
    val lastSyncAt: Long? = null,
    val pushed: Int = 0,
    val pulled: Int = 0,
    val error: String? = null
)

data class TaskListUiState(
    val items: List<TaskModel> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val syncing: SyncUiState = SyncUiState()
)
