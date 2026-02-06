package com.ops.app.sync

import com.ops.core.sync.SyncApi
import com.ops.core.sync.SyncPullResponse
import com.ops.core.sync.SyncPushRequest
import com.ops.core.sync.SyncPushResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class KtorSyncApi(
    private val baseUrl: String
) : SyncApi {

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                }
            )
        }
    }

    override suspend fun push(req: SyncPushRequest): SyncPushResponse {
        return client.post("$baseUrl/sync/push") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()
    }

    override suspend fun pull(entity: String, cursor: Long): SyncPullResponse {
        return client.get("$baseUrl/sync/pull") {
            url {
                parameters.append("entity", entity)
                parameters.append("cursor", cursor.toString())
            }
        }.body()
    }
}
