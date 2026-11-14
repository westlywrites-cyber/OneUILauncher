package com.westly.oneuilauncher.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** One line in a long-press menu. */
data class MenuEntry(val label: String, val onClick: () -> Unit)

private val MenuWidth = 210.dp
private val MenuRowHeight = 48.dp
private val MenuVerticalPadding = 8.dp

/**
 * Small rounded menu that appears next to [anchor] (the pressed icon, in screen coordinates).
 * Tapping outside closes it.
 */
@Composable
fun ContextMenu(
    anchor: Rect,
    entries: List<MenuEntry>,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
    ) {
        val widthPx = with(density) { MenuWidth.toPx() }
        val heightPx = with(density) {
            (MenuRowHeight * entries.size + MenuVerticalPadding * 2).toPx()
        }
        val margin = with(density) { 12.dp.toPx() }
        val maxWidth = constraints.maxWidth.toFloat()
        val maxHeight = constraints.maxHeight.toFloat()

        val x = (anchor.center.x - widthPx / 2f)
            .coerceIn(margin, maxOf(margin, maxWidth - widthPx - margin))
        val above = anchor.top - heightPx - margin
        val y = if (above >= margin * 4f) {
            above
        } else {
            (anchor.bottom + margin).coerceAtMost(maxOf(margin, maxHeight - heightPx - margin))
        }

        Column(
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .width(MenuWidth)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xF21C1F4F))
                // Taps on the menu itself must not count as "tap outside".
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(vertical = MenuVerticalPadding)
        ) {
            entries.forEach { entry ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MenuRowHeight)
                        .clickable {
                            onDismiss()
                            entry.onClick()
                        }
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = entry.label,
                        style = TextStyle(color = Color.White, fontSize = 15.sp)
                    )
                }
            }
        }
    }
}
