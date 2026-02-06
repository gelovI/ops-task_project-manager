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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

@Serializable
data class SyncChange(
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

private data class LogEntry(val cursor: Long, val change: SyncChange)

private val cursorGen = AtomicLong(0L)
// NICHT "log" nennen -> Ktor hat Application.log
private val changeLog = CopyOnWriteArrayList<LogEntry>()

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
fun main() {
    embeddedServer(
        Netty,
        host = "0.0.0.0",
        port = 8080
    ) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; explicitNulls = false })
        }

        routing {
            get("/") { call.respondText("ops-sync ok") }

            post("/sync/push") {
                val req = call.receive<SyncPushRequest>()
                req.changes.forEach { ch ->
                    val c = cursorGen.incrementAndGet()
                    changeLog.add(LogEntry(c, ch))
                }
                call.respond(SyncPushResponse())
            }

            get("/sync/pull") {
                val entity = call.request.queryParameters["entity"] ?: "task"
                val cursor = call.request.queryParameters["cursor"]?.toLongOrNull() ?: 0L

                val changes = changeLog
                    .asSequence()
                    .filter { it.cursor > cursor }
                    .map { it.change }
                    .filter { it.entity == entity }
                    .toList()

                call.respond(
                    SyncPullResponse(
                        changes = changes,
                        nextCursor = cursorGen.get()
                    )
                )
            }
        }
    }.start(wait = true)
}
