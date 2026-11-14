package com.westly.oneuilauncher.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.westly.oneuilauncher.data.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Midnight blue wallpaper drawn in code, matching the HTML preview. */
@Composable
fun HomeWallpaper(modifier: Modifier = Modifier) {
    val c0 = Color(0xFF060A3A)
    val c1 = Color(0xFF1C2FE0)
    val c3 = Color(0xFFA35CFF)
    val c4 = Color(0xFF0A0F66)

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            Brush.linearGradient(
                colors = listOf(c0, c4),
                start = Offset.Zero,
                end = Offset(w, h)
            )
        )

        val swoosh = Path().apply {
            moveTo(-w * 0.15f, h * 0.14f)
            cubicTo(w * 0.22f, h * 0.17f, w * 0.52f, h * 0.38f, w * 0.65f, h * 0.65f)
            cubicTo(w * 0.78f, h * 0.90f, w * 1.00f, h * 1.00f, w * 1.20f, h * 1.05f)
            lineTo(-w * 0.15f, h * 1.05f)
            close()
        }
        drawPath(
            path = swoosh,
            brush = Brush.linearGradient(
                colors = listOf(c1, c0),
                start = Offset(0f, h * 0.2f),
                end = Offset(w, h)
            ),
            alpha = 0.95f
        )

        drawRect(
            Brush.radialGradient(
                colors = listOf(c3.copy(alpha = 0.8f), Color.Transparent),
                center = Offset(w * 0.85f, h * 0.92f),
                radius = w * 0.9f
            )
        )

        // Soft dark scrim at the top and bottom so icons and the status bar stay readable.
        drawRect(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.30f),
                0.22f to Color.Transparent,
                0.68f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.32f)
            )
        )
    }
}

/**
 * One app icon with its name underneath.
 *
 * @param onLongClick called on long press with the icon's position on screen (used for the menu).
 * @param interactive false for icons whose taps and long presses are handled by a parent
 * (the home grid, which also needs drag to rearrange).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIconItem(
    app: AppInfo,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    interactive: Boolean = true,
    onLongClick: ((Rect) -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val iconSize = LocalIconSize.current
    val labelsOn = LocalShowLabels.current
    val sizePx = with(density) { iconSize.roundToPx() }

    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = app.key) {
        value = withContext(Dispatchers.IO) {
            try {
                context.packageManager
                    .getActivityIcon(app.component)
                    .toBitmap(width = sizePx, height = sizePx)
                    .asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        label = "iconPressScale"
    )

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .then(
                if (interactive) {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                        onLongClick = onLongClick?.let { handler -> { handler(bounds) } }
                    )
                } else {
                    Modifier
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(iconSize * 0.31f))
        ) {
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = app.label,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
        if (showLabel && labelsOn) {
            Text(
                text = app.label,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 12.sp,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.5f),
                        offset = Offset(0f, 1f),
                        blurRadius = 6f
                    )
                )
            )
        }
    }
}

/** Row of up to four dock apps, without names. */
@Composable
fun Dock(
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
    onAppLongClick: (AppInfo, Rect) -> Unit = { _, _ -> }
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        for (index in 0 until DOCK_SLOTS) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val app = apps.getOrNull(index)
                if (app != null) {
                    AppIconItem(
                        app = app,
                        showLabel = false,
                        onLongClick = { rect -> onAppLongClick(app, rect) },
                        onClick = { onLaunch(app) }
                    )
                }
            }
        }
    }
}

/** Page indicator dots. */
@Composable
fun PageDots(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier
) {
    if (count < 2) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (index in 0 until count) {
            val selected = index == current
            val dotSize by animateDpAsState(
                targetValue = if (selected) 8.dp else 6.dp,
                label = "dotSize"
            )
            val dotColor by animateColorAsState(
                targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.4f),
                label = "dotColor"
            )
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .background(dotColor, CircleShape)
            )
        }
    }
}
