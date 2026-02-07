package com.ops.core.sync

interface OutboxWriter {
    suspend fun enqueueUpsert(
        entity: String,
        entityId: String,
        updatedAt: Long,
        payload: String
    )

    suspend fun enqueueDelete(
        entity: String,
        entityId: String,
        updatedAt: Long
    )
}
