package com.westly.oneuilauncher.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Starts, stops and permission-checks the status bar overlay. */
object OverlayController {

    /** True when the user has allowed "Display over other apps" for this launcher. */
    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun start(context: Context) {
        if (!canDraw(context)) return
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, StatusBarOverlayService::class.java)
            )
        } catch (_: Exception) {
            // Android can refuse to start a service in rare background situations.
        }
    }

    fun stop(context: Context) {
        try {
            context.stopService(Intent(context, StatusBarOverlayService::class.java))
        } catch (_: Exception) {
            // Nothing to stop.
        }
    }

    /** Opens the system page where the user allows "Display over other apps". */
    fun openPermissionScreen(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // The settings page is missing on this phone.
        }
    }
}
