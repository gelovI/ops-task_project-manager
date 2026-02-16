package com.ops.core.model

data class OutboxRow(
    val outboxId: Long,
    val entity: String,
    val entityId: String,
    val op: String,
    val updatedAt: Long,
    val enqueuedAt: Long,
    val hasPayload: Boolean
)