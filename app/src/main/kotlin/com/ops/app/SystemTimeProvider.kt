package com.ops.app

import com.ops.core.util.TimeProvider

class SystemTimeProvider : TimeProvider {
    override fun now(): Long = System.currentTimeMillis()
}