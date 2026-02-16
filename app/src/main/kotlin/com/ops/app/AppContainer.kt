package com.ops.app

import android.content.Context
import com.ops.app.sync.DevBaseUrl
import com.ops.app.sync.KtorSyncApi
import com.ops.app.sync.SyncConfig
import com.ops.app.sync.SyncDebugViewModel
import com.ops.app.util.AndroidLogger
import com.ops.core.usecase.ProjectRepository
import com.ops.core.usecase.SyncDebugRepository
import com.ops.core.usecase.TaskRepository
import com.ops.data.local.OpsDatabaseFactory
import com.ops.data.local.SqlDelightOutboxWriter
import com.ops.data.local.SqlDelightProjectRepository
import com.ops.data.local.SqlDelightSyncDebugRepository
import com.ops.data.local.SqlDelightTaskRepository
import com.ops.data.local.SyncOnce

class AppContainer(context: Context) {
    private val db = OpsDatabaseFactory.create(context)
    val outboxWriter = SqlDelightOutboxWriter(db)

    val taskRepository: TaskRepository = SqlDelightTaskRepository(db)
    val projectRepository: ProjectRepository = SqlDelightProjectRepository(db = db, outbox = outboxWriter)

    private val syncDebugRepo: SyncDebugRepository = SqlDelightSyncDebugRepository(db)
    val syncConfig = SyncConfig(context)
    val syncOnce = SyncOnce(
        db = db,
        apiProvider = { KtorSyncApi(DevBaseUrl.CURRENT) },
        logger = AndroidLogger(),
        debugRepo = syncDebugRepo
    )
    fun syncDebugViewModel(): SyncDebugViewModel =
        SyncDebugViewModel(
            debugRepo = syncDebugRepo,
            syncOnce = { syncOnce.run() }
        )
}


