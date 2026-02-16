package com.ops.data.local

import com.ops.core.sync.SyncMetaKeys
import com.ops.core.sync.SyncResetInfo
import com.ops.core.sync.SyncResetInfoCodec
import com.ops.core.usecase.OutboxRowUi
import com.ops.core.usecase.SyncDebugRepository
import com.ops.db.OpsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

class SqlDelightSyncDebugRepository(
    private val db: OpsDatabase
) : SyncDebugRepository {

    private val q = db.opsQueries

    override suspend fun outboxCount(): Int = withContext(Dispatchers.IO) {
        q.countOutbox().executeAsOne().toInt()
    }

    override suspend fun outboxCountByEntity(): Map<String, Int> = withContext(Dispatchers.IO) {
        q.countOutboxByEntity()
            .executeAsList()
            .associate { row -> row.entity to row.cnt.toInt() }
    }

    override suspend fun outboxLatest(limit: Int): List<OutboxRowUi> = withContext(Dispatchers.IO) {
        q.getOutboxLatest(limit = limit.toLong())
            .executeAsList()
            .map { r ->
                OutboxRowUi(
                    outboxId = r.outboxId,
                    entity = r.entity,
                    entityId = r.entityId,
                    op = r.op,
                    at = Instant.ofEpochMilli(r.enqueuedAt)
                )
            }
    }

    override suspend fun cursorByEntity(): Map<String, Long> = withContext(Dispatchers.IO) {
        q.getAllCursors()
            .executeAsList()
            .associate { row -> row.entity to row.cursor }
    }

    override suspend fun lastSyncAt(): Instant? = withContext(Dispatchers.IO) {
        val s = q.getMeta(SyncMetaKeys.LAST_SYNC_AT).executeAsOneOrNull()?.value_
        s?.toLongOrNull()?.let { Instant.ofEpochMilli(it) }
    }

    override suspend fun lastSyncError(): String? = withContext(Dispatchers.IO) {
        q.getMeta(SyncMetaKeys.LAST_SYNC_ERROR).executeAsOneOrNull()?.value_
    }

    override suspend fun setLastSyncAt(at: Instant) = withContext(Dispatchers.IO) {
        q.setMeta(SyncMetaKeys.LAST_SYNC_AT, at.toEpochMilli().toString())
    }

    override suspend fun setLastSyncError(msg: String?) = withContext(Dispatchers.IO) {
        if (msg == null) q.deleteMeta(SyncMetaKeys.LAST_SYNC_ERROR)
        else q.setMeta(SyncMetaKeys.LAST_SYNC_ERROR, msg)
    }

    override suspend fun ensureMeta() = withContext(Dispatchers.IO) {
        q.setMeta(SyncMetaKeys.LAST_SYNC_AT, q.getMeta(SyncMetaKeys.LAST_SYNC_AT).executeAsOneOrNull()?.value_)
        q.setMeta(SyncMetaKeys.LAST_SYNC_ERROR, q.getMeta(SyncMetaKeys.LAST_SYNC_ERROR).executeAsOneOrNull()?.value_)
        q.setMeta(SyncMetaKeys.SYNC_RESET_INFO, q.getMeta(SyncMetaKeys.SYNC_RESET_INFO).executeAsOneOrNull()?.value_)
    }

    override suspend fun resetInfo(): SyncResetInfo? = withContext(Dispatchers.IO) {
        val raw = q.getMeta(SyncMetaKeys.SYNC_RESET_INFO).executeAsOneOrNull()?.value_
        SyncResetInfoCodec.decodeOrNull(raw)
    }

    override suspend fun clearResetInfo() = withContext(Dispatchers.IO) {
        q.deleteMeta(SyncMetaKeys.SYNC_RESET_INFO)
    }
}
