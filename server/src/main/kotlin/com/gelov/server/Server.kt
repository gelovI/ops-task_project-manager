@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.gelov.server

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.DriverManager

@Serializable
data class SyncChange(
    // shared SyncChange hat outboxId: Long (non-null) -> server->client liefert 0L
    val outboxId: Long = 0L,
    val entity: String,
    val entityId: String,
    val op: String,           // "UPSERT" | "DELETE"
    val updatedAt: Long,      // epoch millis
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

fun main() {
    val jdbcUrl = "jdbc:sqlite:ops-sync.db"

    // Schema init (WICHTIG: PRAGMA außerhalb von Transaktionen)
    DriverManager.getConnection(jdbcUrl).use { conn ->
        conn.autoCommit = true
        initSchema(conn)
    }

    embeddedServer(
        Netty,
        host = "0.0.0.0",
        port = 8080
    ) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; explicitNulls = false })
        }

        routing {
            get("/") { call.respondText("ops-sync ok (sqlite + state)") }

            get("/debug/state") {
                DriverManager.getConnection(jdbcUrl).use { conn ->
                    val cursor = currentCursor(conn)
                    val logCount = countRows(conn, "change_log")
                    val seenCount = countRows(conn, "seen_outbox")
                    val stateCount = countRows(conn, "entity_state")
                    call.respondText("cursor=$cursor change_log=$logCount seen_outbox=$seenCount entity_state=$stateCount")
                }
            }

            get("/debug/state/dump") {
                val entity = call.request.queryParameters["entity"]
                    ?: return@get call.respondText("Missing ?entity=...")

                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50

                DriverManager.getConnection(jdbcUrl).use { conn ->
                    val rows = dumpEntityState(conn, entity, limit)
                    call.respond(rows)
                }
            }

            post("/sync/push") {
                val req = call.receive<SyncPushRequest>()

                DriverManager.getConnection(jdbcUrl).use { conn ->
                    val now = System.currentTimeMillis()
                    val acked = mutableListOf<Long>()

                    conn.autoCommit = false
                    try {
                        for (ch in req.changes) {
                            val outboxId = ch.outboxId

                            // Idempotenz: outboxId schon gesehen => nur ACK
                            if (outboxId != 0L && wasSeen(conn, outboxId)) {
                                acked += outboxId
                                continue
                            }

                            // 1) apply to server state (LWW)
                            applyChangeToState(conn, ch)

                            // 2) append to cursor log (für Pull)
                            appendLog(conn, ch)

                            // 3) mark seen + ack
                            if (outboxId != 0L) {
                                markSeen(conn, outboxId, now)
                                acked += outboxId
                            }
                        }

                        conn.commit()
                    } catch (t: Throwable) {
                        conn.rollback()
                        throw t
                    } finally {
                        conn.autoCommit = true
                    }

                    call.respond(SyncPushResponse(ackedOutboxIds = acked))
                }
            }

            get("/sync/pull") {
                val entity = call.request.queryParameters["entity"] ?: "task"
                val cursor = call.request.queryParameters["cursor"]?.toLongOrNull() ?: 0L

                DriverManager.getConnection(jdbcUrl).use { conn ->
                    val (changes, next) = pullChanges(conn, entity, cursor)
                    call.respond(SyncPullResponse(changes = changes, nextCursor = next))
                }
            }
        }
    }.start(wait = true)
}

/* =========================
   Schema + Helpers
   ========================= */

private fun initSchema(conn: Connection) {
    // PRAGMAs NICHT in einer TX ausführen
    conn.createStatement().use { st ->
        st.execute("PRAGMA journal_mode=WAL;")
        st.execute("PRAGMA synchronous=NORMAL;")
    }

    conn.createStatement().use { st ->
        st.execute(
            """
            CREATE TABLE IF NOT EXISTS server_meta (
              key TEXT NOT NULL PRIMARY KEY,
              value TEXT
            );
            """.trimIndent()
        )

        st.execute(
            """
            CREATE TABLE IF NOT EXISTS change_log (
              cursor INTEGER NOT NULL PRIMARY KEY,
              entity TEXT NOT NULL,
              entity_id TEXT NOT NULL,
              op TEXT NOT NULL,
              updated_at INTEGER NOT NULL,
              payload TEXT
            );
            """.trimIndent()
        )

        st.execute(
            """
            CREATE TABLE IF NOT EXISTS seen_outbox (
              outbox_id INTEGER NOT NULL PRIMARY KEY,
              seen_at INTEGER NOT NULL
            );
            """.trimIndent()
        )

        // Server State (LWW + Tombstone)
        st.execute(
            """
            CREATE TABLE IF NOT EXISTS entity_state (
              entity TEXT NOT NULL,
              entity_id TEXT NOT NULL,
              updated_at INTEGER NOT NULL,
              deleted_at INTEGER,
              payload TEXT,
              PRIMARY KEY(entity, entity_id)
            );
            """.trimIndent()
        )

        // Optional: Indizes
        st.execute("CREATE INDEX IF NOT EXISTS idx_log_entity_cursor ON change_log(entity, cursor);")
        st.execute("CREATE INDEX IF NOT EXISTS idx_state_entity_updated ON entity_state(entity, updated_at);")
    }

    // ensure meta cursor exists
    if (getMetaLong(conn, "cursor") == null) {
        setMetaLong(conn, "cursor", 0L)
    }
}

private fun currentCursor(conn: Connection): Long =
    getMetaLong(conn, "cursor") ?: 0L

private fun nextCursor(conn: Connection): Long {
    val cur = currentCursor(conn) + 1L
    setMetaLong(conn, "cursor", cur)
    return cur
}

private fun appendLog(conn: Connection, ch: SyncChange) {
    val c = nextCursor(conn)
    conn.prepareStatement(
        """
        INSERT INTO change_log(cursor, entity, entity_id, op, updated_at, payload)
        VALUES(?, ?, ?, ?, ?, ?)
        """.trimIndent()
    ).use { ps ->
        ps.setLong(1, c)
        ps.setString(2, ch.entity)
        ps.setString(3, ch.entityId)
        ps.setString(4, ch.op)
        ps.setLong(5, ch.updatedAt)
        ps.setString(6, ch.payload)
        ps.executeUpdate()
    }
}

/**
 * LWW + Tombstone:
 * - Wenn incoming.updatedAt <= current.updated_at => IGNORE (aber Log wird trotzdem appended)
 * - UPSERT => payload setzen, deleted_at = null
 * - DELETE => deleted_at = updatedAt, payload bleibt wie es ist (optional)
 */
private fun applyChangeToState(conn: Connection, ch: SyncChange) {
    val current = getStateRow(conn, ch.entity, ch.entityId)
    val currentUpdatedAt = current?.updatedAt ?: Long.MIN_VALUE

    if (ch.updatedAt <= currentUpdatedAt) {
        return
    }

    when (ch.op) {
        "UPSERT" -> {
            upsertState(
                conn = conn,
                entity = ch.entity,
                entityId = ch.entityId,
                updatedAt = ch.updatedAt,
                deletedAt = null,
                payload = ch.payload
            )
        }
        "DELETE" -> {
            // Tombstone
            upsertState(
                conn = conn,
                entity = ch.entity,
                entityId = ch.entityId,
                updatedAt = ch.updatedAt,
                deletedAt = ch.updatedAt,
                payload = current?.payload
            )
        }
        else -> {
            // unbekannte Ops -> ignorieren
        }
    }
}

private data class StateRow(
    val updatedAt: Long,
    val deletedAt: Long?,
    val payload: String?
)

private fun getStateRow(conn: Connection, entity: String, entityId: String): StateRow? {
    conn.prepareStatement(
        """
        SELECT updated_at, deleted_at, payload
        FROM entity_state
        WHERE entity=? AND entity_id=?
        """.trimIndent()
    ).use { ps ->
        ps.setString(1, entity)
        ps.setString(2, entityId)
        ps.executeQuery().use { rs ->
            if (!rs.next()) return null
            val upd = rs.getLong("updated_at")
            val del = rs.getLong("deleted_at").let { v -> if (rs.wasNull()) null else v }
            val payload = rs.getString("payload")
            return StateRow(updatedAt = upd, deletedAt = del, payload = payload)
        }
    }
}

private fun upsertState(
    conn: Connection,
    entity: String,
    entityId: String,
    updatedAt: Long,
    deletedAt: Long?,
    payload: String?
) {
    conn.prepareStatement(
        """
        INSERT INTO entity_state(entity, entity_id, updated_at, deleted_at, payload)
        VALUES(?, ?, ?, ?, ?)
        ON CONFLICT(entity, entity_id) DO UPDATE SET
          updated_at=excluded.updated_at,
          deleted_at=excluded.deleted_at,
          payload=excluded.payload
        """.trimIndent()
    ).use { ps ->
        ps.setString(1, entity)
        ps.setString(2, entityId)
        ps.setLong(3, updatedAt)
        if (deletedAt == null) ps.setNull(4, java.sql.Types.INTEGER) else ps.setLong(4, deletedAt)
        ps.setString(5, payload)
        ps.executeUpdate()
    }
}

private fun pullChanges(conn: Connection, entity: String, cursor: Long): Pair<List<SyncChange>, Long> {
    val list = mutableListOf<SyncChange>()

    conn.prepareStatement(
        """
        SELECT cursor, entity, entity_id, op, updated_at, payload
        FROM change_log
        WHERE entity = ? AND cursor > ?
        ORDER BY cursor ASC
        """.trimIndent()
    ).use { ps ->
        ps.setString(1, entity)
        ps.setLong(2, cursor)
        ps.executeQuery().use { rs ->
            while (rs.next()) {
                list += SyncChange(
                    outboxId = 0L, // server->client: kein outboxId
                    entity = rs.getString("entity"),
                    entityId = rs.getString("entity_id"),
                    op = rs.getString("op"),
                    updatedAt = rs.getLong("updated_at"),
                    payload = rs.getString("payload")
                )
            }
        }
    }

    return list to currentCursor(conn)
}

private fun wasSeen(conn: Connection, outboxId: Long): Boolean {
    conn.prepareStatement("SELECT 1 FROM seen_outbox WHERE outbox_id=?").use { ps ->
        ps.setLong(1, outboxId)
        ps.executeQuery().use { rs -> return rs.next() }
    }
}

private fun markSeen(conn: Connection, outboxId: Long, now: Long) {
    conn.prepareStatement(
        "INSERT OR REPLACE INTO seen_outbox(outbox_id, seen_at) VALUES(?, ?)"
    ).use { ps ->
        ps.setLong(1, outboxId)
        ps.setLong(2, now)
        ps.executeUpdate()
    }
}

private fun getMetaLong(conn: Connection, key: String): Long? {
    conn.prepareStatement("SELECT value FROM server_meta WHERE key=?").use { ps ->
        ps.setString(1, key)
        ps.executeQuery().use { rs ->
            if (!rs.next()) return null
            return rs.getString("value")?.toLongOrNull()
        }
    }
}

private fun setMetaLong(conn: Connection, key: String, value: Long) {
    conn.prepareStatement("INSERT OR REPLACE INTO server_meta(key, value) VALUES(?, ?)").use { ps ->
        ps.setString(1, key)
        ps.setString(2, value.toString())
        ps.executeUpdate()
    }
}

private fun countRows(conn: Connection, table: String): Long {
    conn.createStatement().use { st ->
        st.executeQuery("SELECT COUNT(*) AS c FROM $table").use { rs ->
            rs.next()
            return rs.getLong("c")
        }
    }
}

@Serializable
data class DebugStateRow(
    val entity: String,
    val entityId: String,
    val updatedAt: Long,
    val deletedAt: Long?,
    val payload: String?
)

private fun dumpEntityState(
    conn: Connection,
    entity: String,
    limit: Int
): List<DebugStateRow> {

    val list = mutableListOf<DebugStateRow>()

    conn.prepareStatement(
        """
        SELECT entity, entity_id, updated_at, deleted_at, payload
        FROM entity_state
        WHERE entity = ?
        ORDER BY updated_at DESC
        LIMIT ?
        """.trimIndent()
    ).use { ps ->
        ps.setString(1, entity)
        ps.setInt(2, limit)

        ps.executeQuery().use { rs ->
            while (rs.next()) {
                val deletedRaw = rs.getLong("deleted_at")
                val deleted = if (rs.wasNull()) null else deletedRaw

                list += DebugStateRow(
                    entity = rs.getString("entity"),
                    entityId = rs.getString("entity_id"),
                    updatedAt = rs.getLong("updated_at"),
                    deletedAt = deleted,
                    payload = rs.getString("payload")
                )
            }
        }
    }

    return list
}
