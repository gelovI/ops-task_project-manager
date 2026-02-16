package com.ops.core.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SyncResetInfo(
    val atEpochMs: Long,
    val entity: String,
    val localCursor: Long,
    val serverNextCursor: Long,
    val note: String? = null
)

object SyncResetInfoCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encode(info: SyncResetInfo): String = json.encodeToString(info)

    fun decodeOrNull(raw: String?): SyncResetInfo? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<SyncResetInfo>(raw) }.getOrNull()
    }
}