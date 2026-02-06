package com.ops.core.util

object ColorUtil {
    val DEFAULTS: List<Long> = listOf(
        0xFF8E24AA, // purple
        0xFF3949AB, // indigo
        0xFF1E88E5, // blue
        0xFF00897B, // teal
        0xFF43A047, // green
        0xFFF4511E, // orange
        0xFFE53935  // red
    )

    fun pick(index: Int): Long =
        DEFAULTS[(index % DEFAULTS.size + DEFAULTS.size) % DEFAULTS.size]
}