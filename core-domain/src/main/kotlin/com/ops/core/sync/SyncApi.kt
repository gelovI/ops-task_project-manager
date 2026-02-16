package com.ops.core.sync

import kotlinx.serialization.Serializable

@Serializable
data class SyncChange(
    val outboxId: Long? = null,
    val entity: String,
    val entityId: String,
    val op: String,
    val updatedAt: Long,
    val payload: String? = null
)
@Serializable
data class SyncPushRequest(val changes: List<SyncChange>)
@Serializable
data class SyncPushResponse(val ackedOutboxIds: List<Long> = emptyList())
@Serializable
data class SyncPullResponse(
    val changes: List<SyncChange>,
    val nextCursor: Long
)

interface SyncApi {
    suspend fun push(req: SyncPushRequest): SyncPushResponse
    suspend fun pull(entity: String, cursor: Long): SyncPullResponse
}
