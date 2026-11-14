package com.westly.oneuilauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore

/** One launchable app (one launcher activity). */
data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String
) {
    val component: ComponentName get() = ComponentName(packageName, activityName)
    val key: String get() = "$packageName/$activityName"
}

object AppRepository {

    /** All apps that have a launcher icon, except this launcher itself, sorted A to Z. */
    fun loadLaunchableApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                AppInfo(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    activityName = it.activityInfo.name
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Picks the dock apps: the phone's Phone, Messages, Camera and Gallery apps,
     * in that order. A role the phone has no app for is skipped.
     */
    fun resolveDock(context: Context, apps: List<AppInfo>): List<AppInfo> {
        val pm = context.packageManager
        val roleIntents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY)
        )
        val dock = mutableListOf<AppInfo>()
        for (roleIntent in roleIntents) {
            val packages = pm.queryIntentActivities(roleIntent, PackageManager.MATCH_DEFAULT_ONLY)
                .map { it.activityInfo.packageName }
            val match = packages.firstNotNullOfOrNull { pkg ->
                apps.firstOrNull { it.packageName == pkg }
            }
            if (match != null && dock.none { it.key == match.key }) {
                dock.add(match)
            }
        }
        return dock
    }

    /** Opens the app. Does nothing if the app can no longer be started. */
    fun launch(context: Context, app: AppInfo) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // App was removed or cannot be opened; ignore.
        }
    }
}
