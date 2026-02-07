package com.ops.data.local

import com.ops.core.sync.OutboxWriter
import com.ops.db.OpsDatabase

class SqlDelightOutboxWriter(
    private val db: OpsDatabase
) : OutboxWriter {

    private val q = db.opsQueries

    override suspend fun enqueueUpsert(entity: String, entityId: String, updatedAt: Long, payload: String) {
        val now = System.currentTimeMillis()
        db.transaction {
            q.enqueueOutbox(
                entity = entity,
                entityId = entityId,
                op = "UPSERT",
                updatedAt = updatedAt,
                payload = payload,
                enqueuedAt = now
            )
        }
    }

    override suspend fun enqueueDelete(entity: String, entityId: String, updatedAt: Long) {
        val now = System.currentTimeMillis()
        db.transaction {
            q.enqueueOutbox(
                entity = entity,
                entityId = entityId,
                op = "DELETE",
                updatedAt = updatedAt,
                payload = null,
                enqueuedAt = now
            )
        }
    }
}
