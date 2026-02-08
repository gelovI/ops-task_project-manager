package com.ops.data.local

import com.ops.db.OpsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OutboxCountByEntity(val entity: String, val count: Long)
data class CursorRow(val entity: String, val cursor: Long)

data class SyncDebugSnapshot(
    val outboxTotal: Long,
    val outboxByEntity: List<OutboxCountByEntity>,
    val cursors: List<CursorRow>
)

class SqlDelightSyncDebugRepository(
    private val db: OpsDatabase
) {
    private val q = db.opsQueries

    suspend fun snapshot(): SyncDebugSnapshot = withContext(Dispatchers.IO) {
        val total = q.countOutbox().executeAsOne()
        val byEntity = q.countOutboxByEntity().executeAsList()
            .map { OutboxCountByEntity(it.entity, it.cnt) }
        val cursors = q.getAllCursors().executeAsList()
            .map { CursorRow(it.entity, it.cursor) }

        SyncDebugSnapshot(
            outboxTotal = total,
            outboxByEntity = byEntity,
            cursors = cursors
        )
    }
}
