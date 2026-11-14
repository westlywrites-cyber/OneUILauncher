package com.westly.oneuilauncher.qs

import android.graphics.Color as AndroidColor
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.oneuilauncher.notifications.NotificationAccess
import com.westly.oneuilauncher.notifications.NotificationRepository
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The launcher's Quick Settings panel. It opens over whatever app is showing when you swipe
 * down on the right half of the launcher status bar. Swipe up, tap outside or press Back to close.
 */
class QuickSettingsActivity : ComponentActivity() {

    private val refreshTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            QuickSettingsPanel(
                refreshTick = refreshTick.intValue,
                onFinish = { finish() }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTick.intValue++
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

private class Tile(
    val label: String,
    val glyph: String,
    val active: Boolean,
    val onClick: () -> Unit
)

@Composable
private fun QuickSettingsPanel(refreshTick: Int, onFinish: () -> Unit) {
    val context = LocalContext.current

    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    fun close() {
        if (!closing) {
            closing = true
            visible = false
        }
    }
    LaunchedEffect(closing) {
        if (closing) {
            delay(240L)
            onFinish()
        }
    }
    BackHandler { close() }

    // Bumped after a tile changes something, so the tiles re-read their state.
    var bump by remember { mutableIntStateOf(0) }

    // Flashlight state comes from the camera system itself.
    val torchId = remember { QuickControls.torchCameraId(context) }
    var torchOn by remember { mutableStateOf(false) }
    DisposableEffect(torchId) {
        val manager = context.getSystemService(CameraManager::class.java)
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == torchId) torchOn = enabled
            }
        }
        try {
            manager.registerTorchCallback(callback, null)
        } catch (_: Exception) {
            // The flashlight tile just stays off.
        }
        onDispose {
            try {
                manager.unregisterTorchCallback(callback)
            } catch (_: Exception) {
                // Already gone.
            }
        }
    }

    val network = remember(refreshTick, bump) { QuickControls.network(context) }
    val airplane = remember(refreshTick, bump) { QuickControls.isAirplane(context) }
    val autoRotate = remember(refreshTick, bump) { QuickControls.isAutoRotate(context) }
    val dnd = remember(refreshTick, bump) { QuickControls.isDoNotDisturb(context) }
    val location = remember(refreshTick, bump) { QuickControls.isLocationOn(context) }
    val saver = remember(refreshTick, bump) { QuickControls.isBatterySaver(context) }
    val ringer = remember(refreshTick, bump) { QuickControls.ringerMode(context) }

    val soundGlyph = when (ringer) {
        AudioManager.RINGER_MODE_VIBRATE -> "📳"
        AudioManager.RINGER_MODE_SILENT -> "🔕"
        else -> "🔔"
    }
    val soundLabel = when (ringer) {
        AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
        AudioManager.RINGER_MODE_SILENT -> "Mute"
        else -> "Sound"
    }

    val tiles = listOf(
        Tile("Internet", "📶", network.first) {
            QuickControls.openInternet(context); close()
        },
        Tile("Mobile data", "📡", network.second) {
            QuickControls.openNetworkSettings(context); close()
        },
        Tile("Bluetooth", "🔷", false) {
            QuickControls.openBluetooth(context); close()
        },
        Tile("Airplane", "✈️", airplane) {
            QuickControls.openAirplane(context); close()
        },
        Tile("Flashlight", "🔦", torchOn) {
            if (torchId != null) QuickControls.setTorch(context, torchId, !torchOn)
        },
        Tile("Auto-rotate", "🔄", autoRotate) {
            if (QuickControls.setAutoRotate(context, !autoRotate)) {
                bump++
            } else {
                QuickControls.openWriteSettingsPermission(context)
                close()
            }
        },
        Tile("Do not disturb", "🌙", dnd) {
            if (QuickControls.toggleDoNotDisturb(context)) bump++ else close()
        },
        Tile(soundLabel, soundGlyph, ringer == AudioManager.RINGER_MODE_NORMAL) {
            QuickControls.cycleRingerMode(context)
            bump++
        },
        Tile("Location", "📍", location) {
            QuickControls.openLocation(context); close()
        },
        Tile("Battery saver", "🔋", saver) {
            QuickControls.openBatterySaver(context); close()
        },
        Tile("Settings", "⚙️", false) {
            QuickControls.openSystemSettings(context); close()
        }
    )

    val dimAlpha by animateFloatAsState(
        targetValue = if (visible) 0.45f else 0f,
        animationSpec = tween(220),
        label = "qsDim"
    )
    var dragSum by remember { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { delta -> dragSum += delta }

    val hasAccess = remember(refreshTick) { NotificationAccess.has(context) }
    val notifications = NotificationRepository.items

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = dimAlpha))
            .pointerInput(Unit) { detectTapGestures { close() } },
        contentAlignment = Alignment.TopCenter
    ) {
        val maxPanelHeight = maxHeight * 0.92f

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(240)) { -it } + fadeIn(tween(240)),
            exit = slideOutVertically(tween(220)) { -it } + fadeOut(tween(220))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxPanelHeight)
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFA0B0F4A), Color(0xFA070A33)))
                    )
                    // Taps on the panel itself must not close it.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .statusBarsPadding()
                    .padding(top = 16.dp)
            ) {
                // Everything scrolls, so a long list of notifications still fits.
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                ) {
                    PanelHeader(refreshTick)
                    Spacer(modifier = Modifier.height(20.dp))

                    BrightnessRow(refreshTick = refreshTick, onNeedPermission = {
                        QuickControls.openWriteSettingsPermission(context)
                        close()
                    })
                    Spacer(modifier = Modifier.height(8.dp))
                    VolumeRow(refreshTick)
                    Spacer(modifier = Modifier.height(20.dp))

                    tiles.chunked(4).forEach { rowTiles ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowTiles.forEach { tile ->
                                TileView(tile = tile, modifier = Modifier.weight(1f))
                            }
                            repeat(4 - rowTiles.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    NotificationSection(
                        hasAccess = hasAccess,
                        items = notifications,
                        onOpen = { item ->
                            if (NotificationRepository.open(context, item)) close()
                        },
                        onDismiss = { item -> NotificationRepository.dismiss(item.key) },
                        onClearAll = { NotificationRepository.clearAll() },
                        onTurnOnAccess = {
                            NotificationAccess.openSettings(context)
                            close()
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Handle: drag it up to close the panel.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = dragState,
                            onDragStarted = { dragSum = 0f },
                            onDragStopped = { if (dragSum < -40f) close() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 5.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.35f))
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(refreshTick: Int) {
    val time = remember(refreshTick) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
    }
    val date = remember(refreshTick) {
        SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())
    }
    Column {
        Text(
            text = time,
            style = TextStyle(color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Light)
        )
        Text(
            text = date,
            style = TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
        )
    }
}

@Composable
private fun BrightnessRow(refreshTick: Int, onNeedPermission: () -> Unit) {
    val context = LocalContext.current
    val canWrite = remember(refreshTick) { QuickControls.canWriteSettings(context) }
    var value by remember(refreshTick) {
        mutableFloatStateOf(QuickControls.brightness(context).toFloat().coerceIn(1f, 255f))
    }

    Text(
        text = "Brightness",
        style = TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
    )
    if (canWrite) {
        Slider(
            value = value,
            onValueChange = {
                value = it
                QuickControls.setBrightness(context, it.toInt())
            },
            valueRange = 1f..255f,
            colors = sliderColors()
        )
    } else {
        Text(
            text = "Allow brightness control",
            modifier = Modifier
                .padding(vertical = 10.dp)
                .clickable(onClick = onNeedPermission),
            style = TextStyle(color = Color(0xFF9FB0FF), fontSize = 15.sp)
        )
    }
}

@Composable
private fun VolumeRow(refreshTick: Int) {
    val context = LocalContext.current
    val max = remember { QuickControls.mediaVolumeMax(context).coerceAtLeast(1) }
    var value by remember(refreshTick) {
        mutableFloatStateOf(QuickControls.mediaVolume(context).toFloat())
    }

    Text(
        text = "Media volume",
        style = TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
    )
    Slider(
        value = value,
        onValueChange = {
            value = it
            QuickControls.setMediaVolume(context, it.toInt())
        },
        valueRange = 0f..max.toFloat(),
        colors = sliderColors()
    )
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = Color.White,
    activeTrackColor = Color(0xFF3D5AFE),
    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
)

@Composable
private fun TileView(tile: Tile, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clickable(onClick = tile.onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    if (tile.active) Color(0xFF3D5AFE) else Color.White.copy(alpha = 0.12f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = tile.glyph, style = TextStyle(fontSize = 22.sp))
        }
        Text(
            text = tile.label,
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = TextStyle(color = Color.White, fontSize = 11.sp)
        )
    }
}
