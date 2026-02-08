package com.ops.app.sync

import android.os.Build
import com.ops.app.BuildConfig

object DevBaseUrl {
    const val EMULATOR_URL = "http://10.0.2.2:8080"

    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
                Build.PRODUCT.contains("sdk") ||
                Build.HARDWARE.contains("ranchu") ||
                Build.HARDWARE.contains("goldfish")

    val CURRENT: String
        get() = if (isEmulator()) EMULATOR_URL else BuildConfig.DEV_BASE_URL
}
