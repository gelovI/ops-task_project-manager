package com.ops.core.model
data class ProjectModel(
    val id: ProjectId,
    val name: String,
    val color: Long?,
    val updatedAt: Long,
    val deletedAt: Long? = null
) {
    val isDeleted get() = deletedAt != null
}