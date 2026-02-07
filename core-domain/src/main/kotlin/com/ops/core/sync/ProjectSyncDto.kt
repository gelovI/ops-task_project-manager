package com.ops.core.sync

import kotlinx.serialization.Serializable

@Serializable
data class ProjectSyncDto(
    val id: String,
    val name: String,
    val color: Long?,
    val updatedAt: Long,
    val deletedAt: Long?
)
