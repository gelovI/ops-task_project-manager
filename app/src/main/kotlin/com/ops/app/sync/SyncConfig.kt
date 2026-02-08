package com.ops.app.sync

import android.content.Context

class SyncConfig(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sync", Context.MODE_PRIVATE)

    fun getBaseUrl(): String = prefs.getString("baseUrl", DevBaseUrl.CURRENT) ?: DevBaseUrl.CURRENT
    fun setBaseUrl(url: String) { prefs.edit().putString("baseUrl", url).apply() }

    fun getLastSyncAt(): Long? =
        if (prefs.contains("last_sync_at")) prefs.getLong("last_sync_at", 0L) else null

    fun setLastSyncAt(value: Long) {
        prefs.edit().putLong("last_sync_at", value).apply()
    }

    fun getLastError(): String? = prefs.getString("last_sync_error", null)

    fun setLastError(value: String?) {
        prefs.edit().putString("last_sync_error", value).apply()
    }

    fun getLastPushed(): Int = prefs.getInt("last_pushed", 0)
    fun setLastPushed(value: Int) { prefs.edit().putInt("last_pushed", value).apply() }

    fun getLastPulled(): Int = prefs.getInt("last_pulled", 0)
    fun setLastPulled(value: Int) { prefs.edit().putInt("last_pulled", value).apply() }
}
