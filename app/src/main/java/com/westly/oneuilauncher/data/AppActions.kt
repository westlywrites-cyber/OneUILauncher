package com.westly.oneuilauncher.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Things the long-press menu can do to an app. */
object AppActions {

    /** Opens the system "App info" page for the app. */
    fun openAppInfo(context: Context, packageName: String) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // Nothing to do if the settings page cannot be opened.
        }
    }

    /** Asks Android to uninstall the app. Android shows its own confirmation. */
    fun uninstall(context: Context, packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // Some system apps cannot be uninstalled.
        }
    }
}
