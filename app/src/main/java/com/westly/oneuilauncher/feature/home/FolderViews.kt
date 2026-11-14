package com.westly.oneuilauncher.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.westly.oneuilauncher.data.AppInfo
import com.westly.oneuilauncher.data.HomeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A small rounded app icon with no name, used inside the folder tile. */
@Composable
private fun MiniAppIcon(app: AppInfo, size: Dp) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }

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

    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.31f))
        )
    }
}

/** Folder on the home screen: a translucent tile showing up to 9 mini icons, with its name. */
@Composable
fun FolderIconItem(
    folder: HomeItem.Folder,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    onClick: () -> Unit = {}
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        label = "folderPressScale"
    )
    val minis = folder.apps.take(9)
    val tileSize = LocalIconSize.current
    val labelsOn = LocalShowLabels.current
    val miniSize = tileSize * (13f / 56f)
    val tilePadding = tileSize * (6f / 56f)

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (interactive) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .size(tileSize)
                .clip(RoundedCornerShape(tileSize * 0.31f))
                .background(Color.White.copy(alpha = 0.26f))
                .padding(tilePadding),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            for (row in 0 until 3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (col in 0 until 3) {
                        Box(modifier = Modifier.size(miniSize)) {
                            val app = minis.getOrNull(row * 3 + col)
                            if (app != null) {
                                MiniAppIcon(app = app, size = miniSize)
                            }
                        }
                    }
                }
            }
        }
        if (labelsOn) {
            Text(
                text = folder.name,
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

/** Opened folder: dimmed background and a rounded card with the folder's apps. */
@Composable
fun FolderPopup(
    folder: HomeItem.Folder,
    onLaunch: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
    onAppLongClick: (AppInfo, Rect) -> Unit = { _, _ -> }
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xE61C1F4F))
                // Taps inside the card must not close the folder.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = 12.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = folder.name,
                style = TextStyle(color = Color.White, fontSize = 18.sp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(COLUMNS),
                modifier = Modifier.heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(folder.apps, key = { it.key }) { app ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        AppIconItem(
                            app = app,
                            onLongClick = { rect -> onAppLongClick(app, rect) },
                            onClick = { onLaunch(app) }
                        )
                    }
                }
            }
        }
    }
}
