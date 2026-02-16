package com.ops.app.sync

import com.ops.core.sync.SyncResetInfo
import com.ops.core.usecase.OutboxRowUi
import java.time.Instant

enum class SyncHealth { IDLE, RUNNING, PENDING, ERROR }

data class SyncDebugUiState(
    val isLoading: Boolean = false,

    // Health / Status
    val health: SyncHealth = SyncHealth.IDLE,
    val lastSyncAt: Instant? = null,
    val lastError: String? = null,

    // Outbox
    val outboxTotal: Int = 0,
    val outboxByEntity: Map<String, Int> = emptyMap(),
    val outboxLatest: List<OutboxRowUi> = emptyList(),

    // Cursor
    val cursorByEntity: Map<String, Long> = emptyMap(),

    //Reset Info
    val resetInfo: SyncResetInfo? = null
)
