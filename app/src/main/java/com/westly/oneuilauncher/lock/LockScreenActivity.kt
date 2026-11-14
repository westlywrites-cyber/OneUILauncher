package com.westly.oneuilauncher.lock

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.westly.oneuilauncher.data.WallpaperStore
import com.westly.oneuilauncher.feature.wallpaper.LauncherWallpaper
import com.westly.oneuilauncher.notifications.NotificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The launcher's lock screen. It is shown on top of Android's real lock screen when the display
 * turns on. You still unlock with your normal PIN, pattern or fingerprint: swiping up asks
 * Android to unlock, and Android shows its own prompt.
 *
 * It shows only the app icons and the number of notifications, never their text.
 */
class LockScreenActivity : ComponentActivity() {

    private val unlockedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Android has unlocked (for example by fingerprint): this screen is no longer needed.
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )

        ContextCompat.registerReceiver(
            this,
            unlockedReceiver,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            LockScreen(
                onUnlock = { unlock(null) },
                onPhone = { unlock { openPhone() } },
                onCamera = { openCamera() }
            )
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(unlockedReceiver)
        } catch (_: Exception) {
            // Already unregistered.
        }
        super.onDestroy()
    }

    /** Asks Android to unlock; it shows the PIN, pattern or fingerprint prompt if one is set. */
    private fun unlock(after: (() -> Unit)?) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked) {
            after?.invoke()
            finish()
            return
        }
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    after?.invoke()
                    finish()
                }
            }
        )
    }

    private fun openPhone() {
        try {
            startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            // No phone app.
        }
    }

    /** Opens the camera without unlocking (Android's secure camera mode). */
    private fun openCamera() {
        try {
            startActivity(
                Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            unlock {
                try {
                    startActivity(
                        Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {
                    // No camera app.
                }
            }
        }
    }
}

@Composable
private fun LockScreen(
    onUnlock: () -> Unit,
    onPhone: () -> Unit,
    onCamera: () -> Unit
) {
    val context = LocalContext.current

    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val time = SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(now))
    val date = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(now))

    var dragSum by remember { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { delta ->
        dragSum = (dragSum + delta).coerceAtMost(0f) // only upwards
    }

    BackHandler { onUnlock() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .draggable(
                orientation = Orientation.Vertical,
                state = dragState,
                onDragStopped = {
                    val shouldUnlock = dragSum < -200f
                    dragSum = 0f
                    if (shouldUnlock) onUnlock()
                }
            )
    ) {
        LauncherWallpaper(choice = WallpaperStore.load(context), stamp = 0)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.20f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = dragSum * 0.6f
                    alpha = (1f + dragSum / 900f).coerceIn(0.3f, 1f)
                }
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(72.dp))
            Text(
                text = time,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 92.sp,
                    fontWeight = FontWeight.Light
                )
            )
            Text(
                text = date,
                style = TextStyle(color = Color.White.copy(alpha = 0.9f), fontSize = 18.sp)
            )

            Spacer(modifier = Modifier.height(32.dp))
            NotificationSummary()

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Swipe up to unlock",
                style = TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ShortcutButton(glyph = "📞", onClick = onPhone)
                ShortcutButton(glyph = "📷", onClick = onCamera)
            }
        }
    }
}

/** App icons and a count. Never shows what the notifications say. */
@Composable
private fun NotificationSummary() {
    val items = NotificationRepository.items
    if (items.isEmpty()) return

    val packages = items.map { it.packageName }.distinct().take(6)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            packages.forEach { pkg -> PackageIcon(pkg) }
        }
        Text(
            text = if (items.size == 1) "1 notification" else "${items.size} notifications",
            modifier = Modifier.padding(top = 10.dp),
            style = TextStyle(color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
        )
    }
}

@Composable
private fun PackageIcon(packageName: String) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { 32.dp.roundToPx() }
    val icon by produceState<ImageBitmap?>(initialValue = null, key1 = packageName) {
        value = withContext(Dispatchers.IO) {
            try {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(width = sizePx, height = sizePx)
                    .asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }
    Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))) {
        icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(32.dp)) }
    }
}

@Composable
private fun ShortcutButton(glyph: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = glyph, style = TextStyle(fontSize = 22.sp))
    }
}
