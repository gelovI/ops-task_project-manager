package com.ops.core.usecase

import java.time.Instant

data class OutboxRowUi(
    val outboxId: Long,
    val entity: String,
    val entityId: String,
    val op: String,
    val at: Instant
)

interface SyncDebugRepository {
    suspend fun outboxCount(): Int
    suspend fun outboxCountByEntity(): Map<String, Int>
    suspend fun outboxLatest(limit: Int): List<OutboxRowUi>
    suspend fun cursorByEntity(): Map<String, Long>
    suspend fun ensureMeta()
    suspend fun lastSyncAt(): Instant?
    suspend fun lastSyncError(): String?
    suspend fun setLastSyncAt(at: Instant)
    suspend fun setLastSyncError(msg: String?)
    suspend fun resetInfo(): com.ops.core.sync.SyncResetInfo?
    suspend fun clearResetInfo()
}