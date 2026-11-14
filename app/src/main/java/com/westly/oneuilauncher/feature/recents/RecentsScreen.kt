package com.westly.oneuilauncher.feature.recents

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.oneuilauncher.data.AppInfo
import com.westly.oneuilauncher.data.RecentApp
import com.westly.oneuilauncher.data.RecentApps
import com.westly.oneuilauncher.feature.home.AppIconItem
import com.westly.oneuilauncher.feature.home.LocalIconSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The launcher's own multitasking screen: cards for the apps used most recently.
 * Tap a card to open the app, swipe it up to remove it, or use "Close all".
 *
 * @param apps every launchable app (used to match usage statistics to apps).
 * @param refreshKey changes whenever the screen is opened, so the list is reloaded.
 */
@Composable
fun RecentsScreen(
    apps: List<AppInfo>,
    hasAccess: Boolean,
    refreshKey: Int,
    onGrantAccess: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var dismissed by remember(refreshKey) { mutableStateOf(setOf<String>()) }

    val recents by produceState<List<RecentApp>>(
        initialValue = emptyList(),
        apps,
        hasAccess,
        refreshKey
    ) {
        value = if (hasAccess) {
            withContext(Dispatchers.IO) { RecentApps.load(context, apps, 12) }
        } else {
            emptyList()
        }
    }
    val visible = recents.filter { it.app.packageName !in dismissed }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xF2070B3F), Color(0xF2040727))))
            .pointerInput(Unit) { detectTapGestures { onClose() } }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Text(
                text = "Recent apps",
                modifier = Modifier.padding(start = 24.dp, top = 20.dp),
                style = TextStyle(
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium
                )
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    !hasAccess -> Column(
                        modifier = Modifier.padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Android needs your permission to show recently used apps.",
                            textAlign = TextAlign.Center,
                            style = TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Pill(text = "Allow usage access", onClick = onGrantAccess)
                    }
                    visible.isEmpty() -> Text(
                        text = "No recent apps",
                        style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                    )
                    else -> LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(visible, key = { it.app.packageName }) { recent ->
                            RecentCard(
                                recent = recent,
                                onOpen = { onLaunch(recent.app) },
                                onRemove = {
                                    RecentApps.close(context, recent.app.packageName)
                                    dismissed = dismissed + recent.app.packageName
                                }
                            )
                        }
                    }
                }
            }

            if (hasAccess && visible.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Pill(
                        text = "Close all",
                        onClick = {
                            visible.forEach { RecentApps.close(context, it.app.packageName) }
                            dismissed = dismissed + visible.map { it.app.packageName }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 12.dp)
    ) {
        Text(text = text, style = TextStyle(color = Color.White, fontSize = 15.sp))
    }
}

@Composable
private fun RecentCard(recent: RecentApp, onOpen: () -> Unit, onRemove: () -> Unit) {
    var offsetY by remember { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { delta ->
        offsetY = (offsetY + delta).coerceAtMost(0f) // only upwards
    }
    val ago = remember(recent.lastUsed) {
        DateUtils.getRelativeTimeSpanString(
            recent.lastUsed,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }

    Column(
        modifier = Modifier
            .size(width = 200.dp, height = 320.dp)
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .graphicsLayer { alpha = (1f + offsetY / 600f).coerceIn(0.2f, 1f) }
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .draggable(
                orientation = Orientation.Vertical,
                state = dragState,
                onDragStopped = {
                    if (offsetY < -160f) onRemove() else offsetY = 0f
                }
            )
            .clickable(onClick = onOpen)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CompositionLocalProvider(LocalIconSize provides 84.dp) {
            AppIconItem(app = recent.app, showLabel = false, interactive = false)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = recent.app.label,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        )
        Text(
            text = ago,
            modifier = Modifier.padding(top = 4.dp),
            style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        )
    }
}
