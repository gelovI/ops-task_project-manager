package com.ops.core.model

enum class TaskPriority(val value: Int) {
    LOW(0),
    NORMAL(1),
    HIGH(2);

    companion object {
        fun fromInt(v: Int): TaskPriority =
            entries.firstOrNull { it.value == v } ?: NORMAL
    }
}
