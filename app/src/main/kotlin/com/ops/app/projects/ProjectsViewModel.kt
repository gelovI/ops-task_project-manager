package com.ops.app.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ops.core.model.ProjectId
import com.ops.core.model.ProjectModel
import com.ops.core.usecase.CreateProject
import com.ops.core.usecase.ProjectRepository
import com.ops.core.util.TimeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectsUiState(
    val confirmDelete: ProjectModel? = null
)

class ProjectsViewModel(
    private val repo: ProjectRepository,
) : ViewModel() {

    val projects: StateFlow<List<ProjectModel>> =
        repo.observeActive()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ui = MutableStateFlow(ProjectsUiState())
    val ui: StateFlow<ProjectsUiState> = _ui.asStateFlow()

    private val _editor = MutableStateFlow<ProjectEditorState?>(null)
    val editor: StateFlow<ProjectEditorState?> = _editor.asStateFlow()

    private val createProject = CreateProject(
        repo = repo,
        time = object : TimeProvider {
            override fun now(): Long = System.currentTimeMillis()
        }
    )

    fun onAddClick() {
        _editor.value = ProjectEditorState(
            mode = ProjectEditorState.Mode.ADD,
            id = null,
            name = "",
            color = null
        )
    }

    fun onRowClick(id: ProjectId) {
        viewModelScope.launch {
            val p = repo.getById(id) ?: return@launch
            _editor.value = ProjectEditorState(
                mode = ProjectEditorState.Mode.EDIT,
                id = p.id,
                name = p.name,
                color = p.color
            )
        }
    }

    fun dismissEditor() {
        _editor.value = null
    }

    fun save(name: String, color: Long?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            val state = _editor.value ?: return@launch
            val now = System.currentTimeMillis()

            when (state.mode) {
                ProjectEditorState.Mode.ADD -> {
                    // ✅ CreateProject erstellt + speichert (nur EINMAL)
                    val newId: ProjectId = createProject.run(trimmed)

                    // optional: falls du Farbe direkt setzen willst:
                    if (color != null) {
                        val created = repo.getById(newId) ?: return@launch
                        repo.upsert(created.copy(color = color, updatedAt = now))
                    }
                }

                ProjectEditorState.Mode.EDIT -> {
                    val id = state.id ?: return@launch
                    val current = repo.getById(id) ?: return@launch
                    repo.upsert(
                        current.copy(
                            name = trimmed,
                            color = color,
                            updatedAt = now,
                            deletedAt = null
                        )
                    )
                }
            }

            _editor.value = null
        }
    }

    // ---------- B: Long-Press + Confirm ----------

    fun requestDelete(project: ProjectModel) {
        _ui.update { it.copy(confirmDelete = project) }
    }

    fun cancelDelete() {
        _ui.update { it.copy(confirmDelete = null) }
    }

    fun confirmDelete() {
        val p = _ui.value.confirmDelete ?: return
        val now = System.currentTimeMillis()

        viewModelScope.launch {
            repo.softDelete(id = p.id, deletedAt = now, updatedAt = now)
            _ui.update { it.copy(confirmDelete = null) }
        }
    }
}