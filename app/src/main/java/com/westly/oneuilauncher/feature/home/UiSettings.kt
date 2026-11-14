package com.westly.oneuilauncher.feature.home

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Icon size (the square icon itself, in dp). Set from Launcher settings. */
val LocalIconSize = compositionLocalOf<Dp> { 56.dp }

/** Whether app names are shown under icons. */
val LocalShowLabels = compositionLocalOf { true }

/** Icons per row in the app drawer. */
val LocalGridColumns = compositionLocalOf { 4 }

/** Notifications per app (package name to count), used for the dots on icons. */
val LocalBadgeCounts = compositionLocalOf<Map<String, Int>> { emptyMap() }

/** Converts the saved icon size choice (0, 1, 2) to dp. */
fun iconSizeFor(choice: Int): Dp = when (choice) {
    0 -> 48.dp
    2 -> 64.dp
    else -> 56.dp
}
