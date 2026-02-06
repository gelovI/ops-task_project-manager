package com.ops.data.local

import com.ops.core.model.ProjectId
import com.ops.core.model.ProjectModel
import com.ops.db.Project

internal fun Project.toDomain(): ProjectModel =
    ProjectModel(
        id = ProjectId(id),
        name = name,
        color = color,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )
