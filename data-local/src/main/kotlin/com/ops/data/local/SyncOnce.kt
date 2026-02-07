package com.ops.data.local

import com.ops.core.sync.*
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
    private val apiProvider: () -> SyncApi
) {

    private val q = db.opsQueries

    suspend fun run(): SyncReport = withContext(Dispatchers.IO) {
        val api = apiProvider()

        var pushedCount = 0
        var pulledCount = 0

        /* =========================
           1) PUSH (Outbox → Server)
           ========================= */
        val outbox = q.selectOutboxBatch(50).executeAsList()

        if (outbox.isNotEmpty()) {
            val changes = outbox.map {
                SyncChange(
                    entity = it.entity,
                    entityId = it.entityId,
                    op = it.op,
                    updatedAt = it.updatedAt,
                    payload = it.payload
                )
            }

            // Push zum Server
            api.push(SyncPushRequest(changes))

            pushedCount = outbox.size

            // Outbox nach erfolgreichem Push löschen
            db.transaction {
                outbox.forEach { row ->
                    q.deleteOutboxById(row.outboxId)
                }
            }
        }

        /* =========================
   2) PULL (Server → Client)
   ========================= */
        val entities = listOf("project", "task")

        entities.forEach { entity ->
            val cursor = q.getCursor(entity).executeAsOneOrNull() ?: 0L
            val pull = api.pull(entity = entity, cursor = cursor)

            if (pull.changes.isNotEmpty()) {
                pulledCount += pull.changes.size

                db.transaction {
                    pull.changes.forEach { ch ->
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

                                "DELETE" -> {
                                    q.softDeleteProject(
                                        id = ch.entityId,
                                        deletedAt = ch.updatedAt,
                                        updatedAt = ch.updatedAt
                                    )
                                }
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

                                "DELETE" -> {
                                    q.softDeleteTask(
                                        id = ch.entityId,
                                        deletedAt = ch.updatedAt,
                                        updatedAt = ch.updatedAt
                                    )
                                }
                            }
                        }
                    }

                    q.setCursor(entity, pull.nextCursor)
                }
            }
        }

        /* =========================
           Ergebnis für UI
           ========================= */
        SyncReport(
            pushed = pushedCount,
            pulled = pulledCount
        )
    }
}