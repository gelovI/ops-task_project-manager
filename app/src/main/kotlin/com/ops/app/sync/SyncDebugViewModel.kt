package com.ops.app.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ops.core.usecase.SyncDebugRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SyncDebugViewModel(
    private val debugRepo: SyncDebugRepository,
    private val syncOnce: suspend () -> Unit
) : ViewModel() {

    private val _state = MutableStateFlow(SyncDebugUiState(isLoading = true))
    val state: StateFlow<SyncDebugUiState> = _state

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            debugRepo.ensureMeta()

            val outboxTotal = debugRepo.outboxCount()
            val outboxByEntity = debugRepo.outboxCountByEntity()
            val latest = debugRepo.outboxLatest(limit = 30)
            val cursorByEntity = debugRepo.cursorByEntity()
            val lastSyncAt = debugRepo.lastSyncAt()
            val lastError = debugRepo.lastSyncError()

            val health = when {
                lastError != null -> SyncHealth.ERROR
                outboxTotal > 0 -> SyncHealth.PENDING
                else -> SyncHealth.IDLE
            }

            val resetInfo = debugRepo.resetInfo()

            _state.update {
                it.copy(
                    isLoading = false,
                    health = health,
                    resetInfo = resetInfo,
                    lastSyncAt = lastSyncAt,
                    lastError = lastError,
                    outboxTotal = outboxTotal,
                    outboxByEntity = outboxByEntity,
                    outboxLatest = latest,
                    cursorByEntity = cursorByEntity
                )
            }
        }
    }

    fun runSyncOnce() {
        viewModelScope.launch {
            _state.update { it.copy(health = SyncHealth.RUNNING, lastError = null) }
            try {
                syncOnce()
            } catch (t: Throwable) {
            } finally {
                refresh()
            }
        }
    }

    fun clearResetInfo() {
        viewModelScope.launch {
            debugRepo.clearResetInfo()
            refresh()
        }
    }
}