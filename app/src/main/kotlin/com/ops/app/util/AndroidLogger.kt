package com.ops.app.util

import android.util.Log
import com.ops.core.util.Logger

class AndroidLogger : Logger {
    override fun d(tag: String, msg: String) { Log.d(tag, msg) }
    override fun e(tag: String, msg: String, tr: Throwable?) { Log.e(tag, msg, tr) }
}
