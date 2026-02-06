package com.ops.core.util

fun interface TimeProvider {
    fun now(): Long

    companion object {
        val system: TimeProvider = TimeProvider { System.currentTimeMillis() }
    }
}
