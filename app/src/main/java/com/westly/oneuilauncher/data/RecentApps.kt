package com.westly.oneuilauncher.data

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

data class RecentApp(val app: AppInfo, val lastUsed: Long)

/**
 * Recently used apps, taken from Android's usage statistics.
 *
 * Android does not let a launcher replace the system Recents button, so the launcher has its
 * own "Recent apps" screen instead. It needs the "Usage access" permission.
 */
object RecentApps {

    fun hasAccess(context: Context): Boolean {
        return try {
            val ops = context.getSystemService(AppOpsManager::class.java)
            val mode = if (Build.VERSION.SDK_INT >= 29) {
                ops.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    fun openAccessSettings(context: Context) {
        val attempts = listOf(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        )
        for (intent in attempts) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
                // Try the next screen.
            }
        }
    }

    /** Blocking: run off the main thread. Newest first. */
    fun load(context: Context, apps: List<AppInfo>, limit: Int): List<RecentApp> {
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val now = System.currentTimeMillis()
        val stats = try {
            manager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - THREE_DAYS_MS,
                now
            )
        } catch (_: Exception) {
            null
        }
        if (stats == null) return emptyList()

        val lastUsed = HashMap<String, Long>()
        for (entry in stats) {
            val previous = lastUsed[entry.packageName] ?: 0L
            if (entry.lastTimeUsed > previous) lastUsed[entry.packageName] = entry.lastTimeUsed
        }

        val byPackage = apps.associateBy { it.packageName }
        return lastUsed.entries
            .mapNotNull { (pkg, time) -> byPackage[pkg]?.let { RecentApp(it, time) } }
            .sortedByDescending { it.lastUsed }
            .take(limit)
    }

    /** Stops the app's background processes (Android decides whether anything is running). */
    fun close(context: Context, packageName: String) {
        try {
            context.getSystemService(ActivityManager::class.java).killBackgroundProcesses(packageName)
        } catch (_: Exception) {
            // Nothing to stop.
        }
    }

    private const val THREE_DAYS_MS = 3L * 24 * 60 * 60 * 1000
}
