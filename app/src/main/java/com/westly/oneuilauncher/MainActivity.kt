package com.westly.oneuilauncher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import com.westly.oneuilauncher.feature.home.HomeScreen

class MainActivity : ComponentActivity() {

    // Counters that tell the screen to refresh the app list / go back to page 1.
    private val resumeTick = mutableIntStateOf(0)
    private val homeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            HomeScreen(
                resumeTick = resumeTick.intValue,
                homeTick = homeTick.intValue
            )
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing the Home button while already on the launcher returns to page 1.
        if (intent.action == Intent.ACTION_MAIN) {
            homeTick.intValue++
        }
    }
}
