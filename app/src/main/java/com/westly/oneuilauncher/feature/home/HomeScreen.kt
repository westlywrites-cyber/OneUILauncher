package com.westly.oneuilauncher.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.westly.oneuilauncher.data.AppInfo
import com.westly.oneuilauncher.data.AppRepository
import com.westly.oneuilauncher.feature.drawer.AppDrawer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max

const val COLUMNS = 4
const val ROWS = 5
const val DOCK_SLOTS = 4

/** Keeps the running drawer animation so it can be cancelled when a finger touches the screen. */
private class JobHolder {
    var job: Job? = null
}

/**
 * Home screen: wallpaper, swipeable pages of apps, dock and page dots,
 * plus the app drawer that slides up when you swipe up.
 *
 * @param resumeTick changes every time the launcher comes to the front, so the app list refreshes.
 * @param homeTick changes when Home is pressed while the launcher is already open.
 */
@Composable
fun HomeScreen(resumeTick: Int, homeTick: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    LaunchedEffect(resumeTick) {
        apps = withContext(Dispatchers.IO) { AppRepository.loadLaunchableApps(context) }
    }

    val dock = remember(apps) { AppRepository.resolveDock(context, apps) }
    val pageApps = remember(apps, dock) {
        apps.filter { app -> dock.none { it.key == app.key } }
    }

    val perPage = COLUMNS * ROWS
    val pageCount = max(1, ceil(pageApps.size / perPage.toDouble()).toInt())
    val pagerState = rememberPagerState(pageCount = { pageCount })

    // ---- App drawer state: 0 = closed, 1 = fully open ----
    var drawerProgress by remember { mutableFloatStateOf(0f) }
    var heightPx by remember { mutableFloatStateOf(1f) }
    val jobHolder = remember { JobHolder() }
    val drawerVisible by remember { derivedStateOf { drawerProgress > 0f } }

    fun settleDrawer(target: Float, velocity: Float = 0f) {
        jobHolder.job?.cancel()
        jobHolder.job = scope.launch {
            animate(
                initialValue = drawerProgress,
                targetValue = target,
                initialVelocity = velocity,
                animationSpec = spring(dampingRatio = 1f, stiffness = 380f)
            ) { value, _ ->
                drawerProgress = value.coerceIn(0f, 1f)
            }
        }
    }

    /** Moves the drawer with the finger. dy is in pixels (positive = down). Returns pixels used. */
    fun dragDrawer(dy: Float): Float {
        jobHolder.job?.cancel()
        val old = drawerProgress
        val new = (old - dy / heightPx).coerceIn(0f, 1f)
        drawerProgress = new
        return (old - new) * heightPx
    }

    /** Finger lifted: finish opening or closing, depending on speed and how far it moved. */
    fun releaseDrawer(velocityY: Float) {
        val v = -velocityY / heightPx // screens per second, positive = upward
        val target = when {
            v > 0.8f -> 1f
            v < -0.8f -> 0f
            drawerProgress > 0.4f -> 1f
            else -> 0f
        }
        settleDrawer(target, v)
    }

    LaunchedEffect(homeTick) {
        if (homeTick > 0) {
            if (drawerProgress > 0f) {
                settleDrawer(0f)
            } else {
                pagerState.animateScrollToPage(0)
            }
        }
    }

    // A launcher must never close on Back: close the drawer, else go to page 1, else do nothing.
    BackHandler(enabled = true) {
        if (drawerProgress > 0f) {
            settleDrawer(0f)
        } else if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(0) }
        }
    }

    val launchApp: (AppInfo) -> Unit = { app -> AppRepository.launch(context, app) }
    val launchFromDrawer: (AppInfo) -> Unit = { app ->
        AppRepository.launch(context, app)
        jobHolder.job?.cancel()
        drawerProgress = 0f
    }

    val draggableState = rememberDraggableState { dy -> dragDrawer(dy) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
    ) {
        HomeWallpaper()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Home fades and shrinks slightly as the drawer slides over it.
                    val s = 1f - 0.06f * drawerProgress
                    alpha = 1f - 0.5f * drawerProgress
                    scaleX = s
                    scaleY = s
                }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = draggableState,
                    onDragStopped = { velocity -> releaseDrawer(velocity) }
                )
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                val items = remember(pageApps, page) {
                    pageApps.drop(page * perPage).take(perPage)
                }
                HomePage(items = items, onLaunch = launchApp)
            }

            Dock(apps = dock, onLaunch = launchApp)

            PageDots(
                count = pageCount,
                current = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp, bottom = 12.dp)
            )
        }

        if (drawerVisible) {
            AppDrawer(
                apps = apps,
                progress = { drawerProgress },
                onLaunch = launchFromDrawer,
                onDrag = { dy -> dragDrawer(dy) },
                onRelease = { velocityY -> releaseDrawer(velocityY) }
            )
        }
    }
}

/** One page: a fixed grid of COLUMNS x ROWS cells. */
@Composable
private fun HomePage(items: List<AppInfo>, onLaunch: (AppInfo) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        for (row in 0 until ROWS) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                for (col in 0 until COLUMNS) {
                    val app = items.getOrNull(row * COLUMNS + col)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (app != null) {
                            AppIconItem(
                                app = app,
                                onClick = { onLaunch(app) }
                            )
                        }
                    }
                }
            }
        }
    }
}
