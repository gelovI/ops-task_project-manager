package com.ops.app.tasklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ops.app.projects.ProjectEditorState
import com.ops.app.sync.DevBaseUrl
import com.ops.app.sync.SyncConfig
import com.ops.app.taskeditor.TaskEditorState
import com.ops.core.model.*
import com.ops.core.usecase.ProjectRepository
import com.ops.core.usecase.TaskRepository
import com.ops.data.local.SyncOnce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import com.ops.core.usecase.CreateProject
import com.ops.core.util.IdGenerator
import com.ops.core.util.TimeProvider


class TaskListViewModel(
    private val repo: TaskRepository,
    private val projectRepository: ProjectRepository,
    private val syncOnce: SyncOnce,
    private val syncConfig: SyncConfig,
) : ViewModel() {
    enum class TaskFilter { ALL, OPEN, DONE }
    private val _filter = MutableStateFlow(TaskFilter.ALL)
    val filter: StateFlow<TaskFilter> = _filter.asStateFlow()
    fun setFilter(f: TaskFilter) { _filter.value = f }
    private val _uiState = MutableStateFlow(TaskListUiState())
    val uiState: StateFlow<TaskListUiState> = _uiState.asStateFlow()
    private var lastDeleted: TaskModel? = null
    private val _editor = MutableStateFlow<TaskEditorState?>(null)
    val editor: StateFlow<TaskEditorState?> = _editor.asStateFlow()
    val projects: StateFlow<List<ProjectModel>> =
        projectRepository.observeActive()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val baseUrlLabel: String
        get() = if (syncConfig.getBaseUrl().contains("10.0.2.2")) "EMU" else "PHONE"
    private var autoSyncJob: kotlinx.coroutines.Job? = null
    private var lastAutoSyncAt: Long = 0L
    private val minSyncIntervalMs = 30_000L
    fun toggleBaseUrl() {
        val current = syncConfig.getBaseUrl()
        val next = if (current == DevBaseUrl.EMULATOR) DevBaseUrl.PHONE else DevBaseUrl.EMULATOR
        syncConfig.setBaseUrl(next)
    }
    fun syncNow() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    syncing = it.syncing.copy(
                        syncing = true,
                        error = null
                    )
                )
            }

            runCatching {
                syncOnce.run()
            }.onSuccess { report ->
                val now = System.currentTimeMillis()

                syncConfig.setLastSyncAt(now)
                syncConfig.setLastPushed(report.pushed)
                syncConfig.setLastPulled(report.pulled)
                syncConfig.setLastError(null)

                _uiState.update {
                    it.copy(
                        syncing = it.syncing.copy(
                            syncing = false,
                            lastSyncAt = now,
                            pushed = report.pushed,
                            pulled = report.pulled,
                            error = null
                        )
                    )
                }
            }.onFailure { e ->
                val msg = e.message ?: e.javaClass.simpleName

                syncConfig.setLastError(msg)

                _uiState.update {
                    it.copy(
                        syncing = it.syncing.copy(
                            syncing = false,
                            error = msg
                        )
                    )
                }
            }
        }
    }

    init {
        _uiState.update {
            it.copy(
                syncing = it.syncing.copy(
                    lastSyncAt = syncConfig.getLastSyncAt(),
                    error = syncConfig.getLastError(),
                    pushed = syncConfig.getLastPushed(),
                    pulled = syncConfig.getLastPulled()
                )
            )
        }

        viewModelScope.launch {
            combine(repo.observeActive(), _filter) { list, f ->
                val base = list.filterNot { it.isDeleted }

                val filtered = when (f) {
                    TaskFilter.ALL -> base
                    TaskFilter.OPEN -> base.filterNot { it.isDone }
                    TaskFilter.DONE -> base.filter { it.isDone }
                }

                filtered.sortedWith(
                    compareBy<TaskModel> { it.isDone }
                        .thenByDescending { it.priority.value }
                        .thenByDescending { it.updatedAt }
                )
            }
                .catch { e -> _uiState.update { it.copy(loading = false, error = e.message) } }
                .collect { finalList ->
                    _uiState.update { it.copy(items = finalList, loading = false, error = null) }
                }
        }
        requestAutoSync("app_start")
    }

    fun requestAutoSync(reason: String) {
        val now = System.currentTimeMillis()

        if (now - lastAutoSyncAt < minSyncIntervalMs) return

        if (_uiState.value.syncing.syncing) return

        autoSyncJob?.cancel()
        autoSyncJob = viewModelScope.launch {
            kotlinx.coroutines.delay(800)

            val now2 = System.currentTimeMillis()
            if (now2 - lastAutoSyncAt < minSyncIntervalMs) return@launch
            if (_uiState.value.syncing.syncing) return@launch

            lastAutoSyncAt = now2
            syncNow()
        }
    }


    fun onAddClick() {
        _editor.value = TaskEditorState(
            mode = TaskEditorState.Mode.ADD,
            id = null,
            title = "",
            description = "",
            status = TaskStatus.OPEN,
            priority = TaskPriority.NORMAL,
            dueAt = null,
            projectId = null
        )
    }

    fun onEditClick(id: TaskId) {
        val t = _uiState.value.items.firstOrNull { it.id == id } ?: return
        _editor.value = TaskEditorState(
            mode = TaskEditorState.Mode.EDIT,
            id = t.id,
            title = t.title,
            description = t.description,
            status = t.status,
            priority = t.priority,
            dueAt = t.dueAt,
            projectId = t.projectId
        )
    }

    fun onDismissEditor() {
        _editor.value = null
    }

    fun onProjectChange(projectId: ProjectId?) {
        _editor.update { cur ->
            cur?.copy(projectId = projectId)
        }
    }

    fun onSaveEditor(
        title: String,
        description: String,
        status: TaskStatus,
        priority: TaskPriority,
        dueAt: Long?,
        projectId: ProjectId?
    ) {
        val e = _editor.value ?: return
        val now = System.currentTimeMillis()

        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) return

        val id = e.id ?: TaskId(UUID.randomUUID().toString())

        val model = TaskModel(
            id = id,
            title = trimmedTitle,
            description = description,
            status = status,
            dueAt = dueAt,
            projectId = projectId,
            priority = priority,
            updatedAt = now,
            deletedAt = null
        )

        viewModelScope.launch {
            repo.upsert(model)
            _editor.value = null
        }
    }

    fun onDeleteEditor() {
        val e = _editor.value ?: return
        val id = e.id ?: return
        val now = System.currentTimeMillis()

        val current = _uiState.value.items.firstOrNull { it.id == id } ?: return
        lastDeleted = current

        viewModelScope.launch {
            repo.softDelete(id = id, deletedAt = now, updatedAt = now)
            _editor.value = null
        }
    }

    fun undoDelete() {
        val task = lastDeleted ?: return
        lastDeleted = null

        viewModelScope.launch {
            repo.upsert(task.copy(deletedAt = null, updatedAt = System.currentTimeMillis()))
        }
    }
}
