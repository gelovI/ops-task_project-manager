package com.ops.app.ui

import androidx.compose.ui.graphics.Color

fun projectColorOrNull(raw: Long?): Color? {
    if (raw == null) return null

    val v = raw

    val argb = if (v in 0x000000..0xFFFFFF) (0xFF000000L or v) else v

    return Color(argb.toInt())
}
