package com.westly.oneuilauncher.feature.drawer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.oneuilauncher.data.AppInfo
import com.westly.oneuilauncher.feature.home.AppIconItem
import com.westly.oneuilauncher.feature.home.COLUMNS

/**
 * Phase 2 app drawer: search bar plus an A to Z grid of every app.
 *
 * It slides up over the home screen. [progress] is 0 (closed) to 1 (fully open).
 * Pulling down while the grid is at the top slides the drawer back down.
 *
 * @param onDrag moves the drawer by the given pixels (positive = down) and returns the pixels used.
 * @param onRelease called when a pull-down ends, with the vertical velocity in pixels per second.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppDrawer(
    apps: List<AppInfo>,
    progress: () -> Float,
    onLaunch: (AppInfo) -> Unit,
    onDrag: (Float) -> Float,
    onRelease: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onAppLongClick: (AppInfo, Rect) -> Unit = { _, _ -> }
) {
    val keyboard = LocalSoftwareKeyboardController.current
    DisposableEffect(Unit) {
        onDispose { keyboard?.hide() }
    }

    var query by remember { mutableStateOf("") }
    val results = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
    }
    val gridState = rememberLazyGridState()

    val currentProgress by rememberUpdatedState(progress)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnRelease by rememberUpdatedState(onRelease)

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Drawer is partly pulled down and the finger moves up: slide it back up first.
                if (source == NestedScrollSource.UserInput && available.y < 0f && currentProgress() < 1f) {
                    return Offset(0f, currentOnDrag(available.y))
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Grid is already at the top and the finger keeps moving down: pull the drawer down.
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    return Offset(0f, currentOnDrag(available.y))
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (currentProgress() < 1f) {
                    currentOnRelease(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = (1f - currentProgress()) * size.height
            }
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xF2070B3F), Color(0xF2040727))
                )
            )
            // Swallow taps on empty space so they never reach the home screen underneath.
            .pointerInput(Unit) { detectTapGestures { } }
            .nestedScroll(connection)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {
            DrawerSearchBar(
                query = query,
                onQueryChange = { query = it }
            )

            if (results.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = "No apps found",
                        style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(COLUMNS),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 12.dp,
                        bottom = 12.dp + navBottom
                    ),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(results, key = { it.key }) { app ->
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
}

/** Rounded "Search apps" field with a magnifier icon. */
@Composable
private fun DrawerSearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val s = size.minDimension
            val stroke = s * 0.11f
            val color = Color.White.copy(alpha = 0.85f)
            drawCircle(
                color = color,
                radius = s * 0.32f,
                center = Offset(s * 0.42f, s * 0.42f),
                style = Stroke(width = stroke)
            )
            drawLine(
                color = color,
                start = Offset(s * 0.66f, s * 0.66f),
                end = Offset(s * 0.92f, s * 0.92f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search apps",
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 16.sp
                            )
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}
