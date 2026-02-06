package com.ops.core.util

import java.util.UUID

object IdGenerator {
    fun uuid(): String = UUID.randomUUID().toString()
}
