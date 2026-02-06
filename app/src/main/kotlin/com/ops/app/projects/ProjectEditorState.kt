package com.ops.app.projects

import com.ops.core.model.ProjectId

data class ProjectEditorState(
    val mode: Mode,
    val id: ProjectId?,
    val name: String,
    val color: Long?
) {
    enum class Mode { ADD, EDIT }
}
