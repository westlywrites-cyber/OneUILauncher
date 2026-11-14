package com.westly.oneuilauncher.qs

import androidx.compose.animation.core.animate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.westly.oneuilauncher.notifications.NotifItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The notifications part of the panel.
 *
 * @param hasAccess whether Notification access is allowed for the launcher.
 */
@Composable
fun NotificationSection(
    hasAccess: Boolean,
    items: List<NotifItem>,
    onOpen: (NotifItem) -> Unit,
    onDismiss: (NotifItem) -> Unit,
    onClearAll: () -> Unit,
    onTurnOnAccess: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Notifications",
            modifier = Modifier.weight(1f),
            style = TextStyle(
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        )
        if (hasAccess && items.any { it.clearable }) {
            Text(
                text = "Clear all",
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onClearAll)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                style = TextStyle(color = Color(0xFF9FB0FF), fontSize = 14.sp)
            )
        }
    }
    Spacer(modifier = Modifier.height(10.dp))

    when {
        !hasAccess -> InfoCard(
            title = "Turn on notification access",
            body = "Needed to show your notifications here. If the switch is greyed out, " +
                "open Launcher settings, then Notifications, and follow step 1.",
            onClick = onTurnOnAccess
        )
        items.isEmpty() -> Text(
            text = "No notifications",
            modifier = Modifier.padding(vertical = 8.dp),
            style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        )
        else -> items.forEach { item ->
            key(item.key) {
                SwipeToDismissCard(
                    enabled = item.clearable,
                    onDismiss = { onDismiss(item) }
                ) {
                    NotificationCard(item = item, onClick = { onOpen(item) })
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Text(text = title, style = TextStyle(color = Color.White, fontSize = 15.sp))
        Text(
            text = body,
            modifier = Modifier.padding(top = 4.dp),
            style = TextStyle(color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
        )
    }
}

@Composable
private fun NotificationCard(item: NotifItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { 28.dp.roundToPx() }

    val icon by produceState<ImageBitmap?>(initialValue = null, key1 = item.packageName) {
        value = withContext(Dispatchers.IO) {
            try {
                context.packageManager.getApplicationIcon(item.packageName)
                    .toBitmap(width = sizePx, height = sizePx)
                    .asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }
    val time = remember(item.time) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(item.time))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))) {
            icon?.let {
                Image(bitmap = it, contentDescription = item.appLabel, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.appLabel,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
                )
                Text(
                    text = time,
                    style = TextStyle(color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                )
            }
            if (item.title.isNotEmpty()) {
                Text(
                    text = item.title,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
            if (item.text.isNotEmpty()) {
                Text(
                    text = item.text,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                )
            }
        }
    }
}

/** Lets a card be flicked away to the left or right. */
@Composable
private fun SwipeToDismissCard(
    enabled: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    val latestDismiss by rememberUpdatedState(onDismiss)

    fun snapBack() {
        scope.launch {
            animate(initialValue = offsetX, targetValue = 0f) { value, _ -> offsetX = value }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .graphicsLayer { alpha = (1f - abs(offsetX) / widthPx).coerceIn(0f, 1f) }
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (abs(offsetX) > widthPx * 0.4f) latestDismiss() else snapBack()
                            },
                            onDragCancel = { snapBack() },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        content()
    }
}
