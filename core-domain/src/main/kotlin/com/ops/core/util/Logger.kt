package com.ops.core.util

interface Logger {
    fun d(tag: String, msg: String)
    fun e(tag: String, msg: String, tr: Throwable? = null)
}

object NoopLogger : Logger {
    override fun d(tag: String, msg: String) = Unit
    override fun e(tag: String, msg: String, tr: Throwable?) = Unit
}
