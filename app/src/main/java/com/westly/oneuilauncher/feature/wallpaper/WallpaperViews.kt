package com.westly.oneuilauncher.feature.wallpaper

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.oneuilauncher.data.WallpaperStore
import com.westly.oneuilauncher.feature.home.HomeWallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class Preset(val name: String, val colors: List<Color>, val glow: Color)

/** Built-in wallpapers. Number 0 is the original midnight blue drawn by [HomeWallpaper]. */
private val presets = listOf(
    Preset("Midnight", emptyList(), Color.Transparent),
    Preset(
        "Aurora",
        listOf(Color(0xFF04202E), Color(0xFF0B6E6B), Color(0xFF5C3CC4)),
        Color(0xFF3DF2C0)
    ),
    Preset(
        "Sunset",
        listOf(Color(0xFF2B0A3D), Color(0xFFB43A6B), Color(0xFFFF9A4D)),
        Color(0xFFFFC27A)
    ),
    Preset(
        "Graphite",
        listOf(Color(0xFF0E0F13), Color(0xFF23262F), Color(0xFF3C4150)),
        Color(0xFF7B8399)
    ),
    Preset(
        "Forest",
        listOf(Color(0xFF04170F), Color(0xFF0F5A3A), Color(0xFF7DBE5A)),
        Color(0xFFB5F06A)
    )
)

/** Draws one built-in wallpaper. */
@Composable
private fun PresetBackground(index: Int, modifier: Modifier = Modifier) {
    val safe = index.coerceIn(0, presets.lastIndex)
    if (safe == 0) {
        HomeWallpaper(modifier)
        return
    }
    val preset = presets[safe]
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.linearGradient(
                colors = preset.colors,
                start = Offset.Zero,
                end = Offset(w, h)
            )
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(preset.glow.copy(alpha = 0.55f), Color.Transparent),
                center = Offset(w * 0.8f, h * 0.85f),
                radius = w * 0.9f
            )
        )
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

/** The photo the user picked from the gallery, filling the screen. */
@Composable
private fun CustomWallpaper(stamp: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = stamp) {
        value = withContext(Dispatchers.IO) {
            try {
                BitmapFactory.decodeFile(WallpaperStore.customFile(context).path)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        // Soft dark scrim so icons and the status bar stay readable on bright photos.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.30f),
                        0.22f to Color.Black.copy(alpha = 0.08f),
                        0.68f to Color.Black.copy(alpha = 0.08f),
                        1f to Color.Black.copy(alpha = 0.34f)
                    )
                )
        )
    }
}

/**
 * The wallpaper behind everything.
 *
 * @param choice a preset number, or [WallpaperStore.CUSTOM] for the picked photo.
 * @param stamp changes every time a new photo is picked, so the photo reloads.
 */
@Composable
fun LauncherWallpaper(choice: Int, stamp: Int, modifier: Modifier = Modifier) {
    if (choice == WallpaperStore.CUSTOM) {
        CustomWallpaper(stamp = stamp, modifier = modifier)
    } else {
        PresetBackground(index = choice, modifier = modifier)
    }
}

/** Bottom sheet for choosing a wallpaper: built-in ones plus a button to pick from the gallery. */
@Composable
fun WallpaperPicker(
    selected: Int,
    onSelectPreset: (Int) -> Unit,
    onChooseFromGallery: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(Color(0xF21C1F4F))
                .pointerInput(Unit) { detectTapGestures { } }
                .navigationBarsPadding()
                .padding(top = 20.dp, bottom = 20.dp)
        ) {
            Text(
                text = "Wallpaper",
                modifier = Modifier.padding(horizontal = 24.dp),
                style = TextStyle(
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    GalleryTile(
                        selected = selected == WallpaperStore.CUSTOM,
                        onClick = onChooseFromGallery
                    )
                }
                itemsIndexed(presets) { index, preset ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(width = 84.dp, height = 150.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    width = if (selected == index) 3.dp else 0.dp,
                                    color = Color.White,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { onSelectPreset(index) }
                        ) {
                            PresetBackground(index = index, modifier = Modifier.fillMaxSize())
                        }
                        Text(
                            text = preset.name,
                            modifier = Modifier.padding(top = 6.dp),
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

/** First tile in the picker: "pick a photo from the gallery". */
@Composable
private fun GalleryTile(selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 84.dp, height = 150.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.14f))
                .border(
                    width = if (selected) 3.dp else 0.dp,
                    color = Color.White,
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(28.dp)) {
                val s = size.minDimension
                val stroke = s * 0.12f
                drawLine(
                    color = Color.White,
                    start = Offset(s / 2f, s * 0.1f),
                    end = Offset(s / 2f, s * 0.9f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White,
                    start = Offset(s * 0.1f, s / 2f),
                    end = Offset(s * 0.9f, s / 2f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }
        Text(
            text = "Gallery",
            modifier = Modifier.padding(top = 6.dp),
            style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        )
    }
}
