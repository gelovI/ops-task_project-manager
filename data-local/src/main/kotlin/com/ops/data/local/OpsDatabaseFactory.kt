package com.ops.data.local

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.ops.db.OpsDatabase

object OpsDatabaseFactory {
    fun create(context: Context): OpsDatabase {
        val driver = AndroidSqliteDriver(
            schema = OpsDatabase.Schema,
            context = context.applicationContext,
            name = "ops.db"
        )
        return OpsDatabase(driver)
    }
}
