package com.westly.oneuilauncher.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.oneuilauncher.data.LauncherSettings

/**
 * Full-screen Launcher settings.
 *
 * @param overlayPermissionGranted whether "Display over other apps" is allowed for the launcher.
 */
@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    overlayPermissionGranted: Boolean,
    onChange: (LauncherSettings) -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onGrantOverlayPermission: () -> Unit,
    onOpenWallpaper: () -> Unit,
    onResetLayout: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF070B3F), Color(0xFF040727)))
            )
            // Swallow touches so nothing underneath reacts.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(22.dp)) {
                        val s = size.minDimension
                        val stroke = s * 0.11f
                        drawLine(
                            Color.White, Offset(s * 0.62f, s * 0.12f), Offset(s * 0.28f, s * 0.5f),
                            strokeWidth = stroke, cap = StrokeCap.Round
                        )
                        drawLine(
                            Color.White, Offset(s * 0.28f, s * 0.5f), Offset(s * 0.62f, s * 0.88f),
                            strokeWidth = stroke, cap = StrokeCap.Round
                        )
                    }
                }
                Text(
                    text = "Launcher settings",
                    modifier = Modifier.padding(start = 8.dp),
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                SectionTitle("Home screen")
                SettingsCard {
                    ChoiceRow(
                        title = "Grid columns",
                        options = listOf("4", "5"),
                        selected = settings.columns - 4,
                        onSelect = { onChange(settings.copy(columns = 4 + it)) }
                    )
                    Divider()
                    ChoiceRow(
                        title = "Icon size",
                        options = listOf("Small", "Medium", "Large"),
                        selected = settings.iconSize,
                        onSelect = { onChange(settings.copy(iconSize = it)) }
                    )
                    Divider()
                    SwitchRow(
                        title = "Show app names",
                        subtitle = null,
                        checked = settings.showLabels,
                        onChange = { onChange(settings.copy(showLabels = it)) }
                    )
                }

                SectionTitle("Look")
                SettingsCard {
                    ActionRow(
                        title = "Wallpaper",
                        subtitle = "Built-in wallpapers or a photo from your gallery",
                        onClick = onOpenWallpaper
                    )
                    Divider()
                    ActionRow(
                        title = "Reset home layout",
                        subtitle = "Put apps back in the default order",
                        onClick = onResetLayout
                    )
                }

                SectionTitle("Status bar")
                SettingsCard {
                    SwitchRow(
                        title = "Status bar overlay",
                        subtitle = "Shows the launcher's own status bar over every app. " +
                            "It covers Android's status bar, so notification icons are hidden " +
                            "until the notifications phase.",
                        checked = settings.statusBarOverlay,
                        onChange = onToggleOverlay
                    )
                    if (settings.statusBarOverlay && !overlayPermissionGranted) {
                        Divider()
                        ActionRow(
                            title = "Permission needed",
                            subtitle = "Tap to allow \"Display over other apps\" for this launcher",
                            onClick = onGrantOverlayPermission
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp),
        style = TextStyle(
            color = Color(0xFF9FB0FF),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.08f)),
        content = content
    )
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.08f))
    )
}

@Composable
private fun ChoiceRow(
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(text = title, style = TextStyle(color = Color.White, fontSize = 16.sp))
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, label ->
                val isSelected = index == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected) Color(0xFF3D5AFE) else Color.White.copy(alpha = 0.10f)
                        )
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = TextStyle(color = Color.White, fontSize = 16.sp))
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    modifier = Modifier.padding(top = 4.dp),
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 13.sp
                    )
                )
            }
        }
        Spacer(modifier = Modifier.size(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF3D5AFE),
                uncheckedThumbColor = Color.White.copy(alpha = 0.8f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.2f),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(text = title, style = TextStyle(color = Color.White, fontSize = 16.sp))
        if (subtitle != null) {
            Text(
                text = subtitle,
                modifier = Modifier.padding(top = 4.dp),
                style = TextStyle(color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
            )
        }
    }
}
