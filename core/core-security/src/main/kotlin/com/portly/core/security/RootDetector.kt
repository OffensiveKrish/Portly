package com.portly.core.security

import android.os.Build
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class RootCheckResult(
    val isPotentiallyRooted: Boolean,
    val indicators: List<String>,
)

@Singleton
class RootDetector @Inject constructor() {

    fun check(): RootCheckResult {
        val indicators = mutableListOf<String>()

        if (Build.TAGS?.contains("test-keys") == true) {
            indicators += "Build tags contain test-keys"
        }

        val suPaths = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su",
        )
        for (path in suPaths) {
            if (runCatching { File(path).exists() }.getOrDefault(false)) {
                indicators += "Binary present: $path"
            }
        }

        return RootCheckResult(
            isPotentiallyRooted = indicators.isNotEmpty(),
            indicators = indicators,
        )
    }
}
