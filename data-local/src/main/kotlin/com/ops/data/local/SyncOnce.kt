package com.ops.data.local

import com.ops.core.sync.*
import com.ops.core.usecase.SyncDebugRepository
import com.ops.core.util.Logger
import com.ops.core.util.NoopLogger
import com.ops.db.OpsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class SyncReport(
    val pushed: Int,
    val pulled: Int
)

class SyncOnce(
    private val db: OpsDatabase,
    private val apiProvider: () -> SyncApi,
    private val logger: Logger = NoopLogger,
    private val debugRepo: SyncDebugRepository,
) {
    private val q = db.opsQueries

    private companion object {
        const val TAG = "SYNC"
    }

    suspend fun run(): SyncReport = withContext(Dispatchers.IO) {
        debugRepo.ensureMeta()

        val api = apiProvider()

        var pushedCount = 0
        var pulledCount = 0

        db.transaction {
            q.setMeta(SyncMetaKeys.LAST_SYNC_ERROR, null)
        }

        try {
            /* =========================
               1) PUSH (Outbox → Server)
               ========================= */
            val outbox = q.selectOutboxBatch(50).executeAsList()

            if (outbox.isNotEmpty()) {
                val changes = outbox.map {
                    SyncChange(
                        outboxId = it.outboxId,
                        entity = it.entity,
                        entityId = it.entityId,
                        op = it.op,
                        updatedAt = it.updatedAt,
                        payload = it.payload
                    )
                }

                val res = api.push(SyncPushRequest(changes))

                db.transaction {
                    res.ackedOutboxIds.forEach { id ->
                        q.deleteOutboxById(id)
                    }
                }

                pushedCount = res.ackedOutboxIds.size
                logger.d(TAG, "PUSH: outbox.size=${outbox.size} acked=${pushedCount}")
            } else {
                logger.d(TAG, "PUSH: outbox.size=0")
            }

            /* =========================
               2) PULL (Server → Client)
               ========================= */
            val entities = listOf("project", "task")

            entities.forEach { entity ->
                val localCursor = q.getCursor(entity).executeAsOneOrNull() ?: 0L

                // 1) normal Pull
                val pull1 = api.pull(entity = entity, cursor = localCursor)
                logger.d(
                    TAG,
                    "PULL[$entity]: cursor=$localCursor received=${pull1.changes.size} nextCursor=${pull1.nextCursor}"
                )

                // 2) Reset-Erkennung
                if (pull1.nextCursor < localCursor) {
                    val info = SyncResetInfo(
                        atEpochMs = System.currentTimeMillis(),
                        entity = entity,
                        localCursor = localCursor,
                        serverNextCursor = pull1.nextCursor,
                        note = "Server reset detected -> local cursor reset to 0"
                    )

                    db.transaction {
                        q.setMeta(SyncMetaKeys.SYNC_RESET_INFO, SyncResetInfoCodec.encode(info))
                        q.setCursor(entity, 0L)
                    }

                    logger.d(TAG, "PULL[$entity]: RESET detected -> repull from 0")

                    val pull2 = api.pull(entity = entity, cursor = 0L)
                    logger.d(
                        TAG,
                        "PULL[$entity]: REPULL cursor=0 received=${pull2.changes.size} nextCursor=${pull2.nextCursor}"
                    )

                    if (pull2.changes.isNotEmpty()) pulledCount += pull2.changes.size

                    db.transaction {
                        applyPulledChanges(entity = entity, changes = pull2.changes)
                        q.setCursor(entity, pull2.nextCursor)
                    }

                    return@forEach
                }

                // Normalfall
                if (pull1.changes.isNotEmpty()) pulledCount += pull1.changes.size

                db.transaction {
                    applyPulledChanges(entity = entity, changes = pull1.changes)
                    q.setCursor(entity, pull1.nextCursor)
                }
            }

            val nowMs = System.currentTimeMillis()
            db.transaction {
                q.setMeta(SyncMetaKeys.LAST_SYNC_AT, nowMs.toString())
                q.setMeta(SyncMetaKeys.LAST_SYNC_ERROR, null)
            }

            return@withContext SyncReport(pushed = pushedCount, pulled = pulledCount)

        } catch (t: Throwable) {
            val msg = t.message ?: t::class.java.simpleName
            logger.d(TAG, "run() ERROR: $msg")

            db.transaction {
                q.setMeta(SyncMetaKeys.LAST_SYNC_ERROR, msg)
            }

            throw t
        }
    }

    private fun applyPulledChanges(entity: String, changes: List<SyncChange>) {
        changes.forEach { ch ->
            when (entity) {
                "project" -> when (ch.op) {
                    "UPSERT" -> {
                        val payload = ch.payload ?: return@forEach
                        val o = JSONObject(payload)
                        q.upsertProject(
                            id = o.getString("id"),
                            name = o.getString("name"),
                            color = if (o.isNull("color")) null else o.getLong("color"),
                            updatedAt = o.getLong("updatedAt"),
                            deletedAt = if (o.isNull("deletedAt")) null else o.getLong("deletedAt")
                        )
                    }

                    "DELETE" -> q.softDeleteProject(
                        id = ch.entityId,
                        deletedAt = ch.updatedAt,
                        updatedAt = ch.updatedAt
                    )
                }

                "task" -> when (ch.op) {
                    "UPSERT" -> {
                        val payload = ch.payload ?: return@forEach
                        val o = JSONObject(payload)
                        q.upsertTask(
                            id = o.getString("id"),
                            title = o.getString("title"),
                            description = o.getString("description"),
                            status = o.getString("status"),
                            dueAt = if (o.isNull("dueAt")) null else o.getLong("dueAt"),
                            projectId = if (o.isNull("projectId")) null else o.getString("projectId"),
                            priority = o.getLong("priority"),
                            updatedAt = o.getLong("updatedAt"),
                            deletedAt = if (o.isNull("deletedAt")) null else o.getLong("deletedAt")
                        )
                    }

                    "DELETE" -> q.softDeleteTask(
                        id = ch.entityId,
                        deletedAt = ch.updatedAt,
                        updatedAt = ch.updatedAt
                    )
                }
            }
        }
    }
}