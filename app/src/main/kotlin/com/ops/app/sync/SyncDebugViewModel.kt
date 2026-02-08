package com.ops.app.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ops.data.local.SqlDelightSyncDebugRepository
import com.ops.data.local.SyncDebugSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SyncDebugUiState(
    val loading: Boolean = true,
    val snapshot: SyncDebugSnapshot? = null,
    val error: String? = null
)

class SyncDebugViewModel(
    private val repo: SqlDelightSyncDebugRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SyncDebugUiState())
    val state: StateFlow<SyncDebugUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repo.snapshot() }
                .onSuccess { snap ->
                    _state.value = SyncDebugUiState(loading = false, snapshot = snap)
                }
                .onFailure { e ->
                    _state.value = SyncDebugUiState(loading = false, error = e.message ?: "Unknown error")
                }
        }
    }

    init {
        refresh()
    }
}