package com.ops.core.model

import java.util.UUID

@JvmInline
value class TaskId(val value: String) {
    companion object {
        fun random(): TaskId = TaskId(UUID.randomUUID().toString())
    }
}
