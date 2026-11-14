package com.westly.oneuilauncher.data

import android.content.Context

/**
 * The launcher's own settings.
 *
 * @param columns icons per row on the home screen and in the app drawer (4 or 5).
 * @param iconSize 0 = small, 1 = medium, 2 = large.
 * @param showLabels show app names under icons.
 * @param statusBarOverlay the user wants the custom status bar drawn over other apps.
 */
data class LauncherSettings(
    val columns: Int = 4,
    val iconSize: Int = 1,
    val showLabels: Boolean = true,
    val statusBarOverlay: Boolean = false
)

object SettingsStore {

    private const val PREFS = "launcher_settings"

    fun load(context: Context): LauncherSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val defaults = LauncherSettings()
        return LauncherSettings(
            columns = prefs.getInt("columns", defaults.columns).coerceIn(4, 5),
            iconSize = prefs.getInt("iconSize", defaults.iconSize).coerceIn(0, 2),
            showLabels = prefs.getBoolean("showLabels", defaults.showLabels),
            statusBarOverlay = prefs.getBoolean("statusBarOverlay", defaults.statusBarOverlay)
        )
    }

    fun save(context: Context, settings: LauncherSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("columns", settings.columns)
            .putInt("iconSize", settings.iconSize)
            .putBoolean("showLabels", settings.showLabels)
            .putBoolean("statusBarOverlay", settings.statusBarOverlay)
            .apply()
    }
}
