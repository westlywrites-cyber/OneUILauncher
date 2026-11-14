package com.westly.oneuilauncher.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.westly.oneuilauncher.data.HomeItem
import com.westly.oneuilauncher.data.HomeItems
import com.westly.oneuilauncher.feature.drawer.AppDrawer
import com.westly.oneuilauncher.feature.widgets.CalendarWidget
import com.westly.oneuilauncher.feature.widgets.ClockWidget
import com.westly.oneuilauncher.feature.widgets.GoogleSearchWidget
import com.westly.oneuilauncher.feature.widgets.WeatherWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val COLUMNS = 4
const val ROWS = 5
const val DOCK_SLOTS = 4

/** Page 1 and page 2 always exist, because they hold the widgets. */
private const val MIN_PAGES = 2

/** How many grid rows the widgets use up on each page. */
private fun widgetRowsFor(page: Int): Int = when (page) {
    0 -> 1 // weather + Google search
    1 -> 2 // clock + calendar
    else -> 0
}

private fun capacityFor(page: Int): Int = COLUMNS * (ROWS - widgetRowsFor(page))

/** Keeps the running drawer animation so it can be cancelled when a finger touches the screen. */
private class JobHolder {
    var job: Job? = null
}

/**
 * Home screen: wallpaper, swipeable pages with widgets, apps and folders, dock, page dots,
 * and the app drawer that slides up when you swipe up.
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
    val homeItems = remember(apps, dock) {
        HomeItems.build(apps.filter { app -> dock.none { it.key == app.key } })
    }

    // Split the items over pages. Pages with widgets hold fewer rows of apps.
    val pages = remember(homeItems) {
        val result = mutableListOf<List<HomeItem>>()
        var index = 0
        while (index < homeItems.size || result.size < MIN_PAGES) {
            val capacity = capacityFor(result.size)
            result.add(homeItems.drop(index).take(capacity))
            index += capacity
        }
        result.toList()
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })

    // ---- Folder popup state ----
    var folderShown by remember { mutableStateOf<HomeItem.Folder?>(null) }
    var folderOpen by remember { mutableStateOf(false) }

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
            if (folderOpen) {
                folderOpen = false
            } else if (drawerProgress > 0f) {
                settleDrawer(0f)
            } else {
                pagerState.animateScrollToPage(0)
            }
        }
    }

    // A launcher must never close on Back: close the folder, then the drawer,
    // then go back to page 1, otherwise do nothing.
    BackHandler(enabled = true) {
        if (folderOpen) {
            folderOpen = false
        } else if (drawerProgress > 0f) {
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
    val launchFromFolder: (AppInfo) -> Unit = { app ->
        AppRepository.launch(context, app)
        folderOpen = false
    }
    val openFolder: (HomeItem.Folder) -> Unit = { folder ->
        folderShown = folder
        folderOpen = true
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
                HomePage(
                    pageIndex = page,
                    items = pages.getOrElse(page) { emptyList() },
                    refreshKey = resumeTick,
                    onLaunch = launchApp,
                    onOpenFolder = openFolder
                )
            }

            Dock(apps = dock, onLaunch = launchApp)

            PageDots(
                count = pages.size,
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

        AnimatedVisibility(
            visible = folderOpen,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.9f, animationSpec = tween(220)),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.9f, animationSpec = tween(150))
        ) {
            folderShown?.let { folder ->
                FolderPopup(
                    folder = folder,
                    onLaunch = launchFromFolder,
                    onDismiss = { folderOpen = false }
                )
            }
        }
    }
}

/**
 * One page: optional widgets at the top, then a fixed grid of apps and folders.
 * Every grid row has the same height on every page, so icons line up from page to page.
 */
@Composable
private fun HomePage(
    pageIndex: Int,
    items: List<HomeItem>,
    refreshKey: Int,
    onLaunch: (AppInfo) -> Unit,
    onOpenFolder: (HomeItem.Folder) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        val rowHeight = maxHeight / ROWS
        val gridRows = ROWS - widgetRowsFor(pageIndex)

        Column(modifier = Modifier.fillMaxSize()) {
            if (pageIndex == 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WeatherWidget(
                        refreshKey = refreshKey,
                        modifier = Modifier.weight(0.9f)
                    )
                    GoogleSearchWidget(modifier = Modifier.weight(1.1f))
                }
            } else if (pageIndex == 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight * 2)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ClockWidget(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    CalendarWidget(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }

            for (row in 0 until gridRows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                ) {
                    for (col in 0 until COLUMNS) {
                        val item = items.getOrNull(row * COLUMNS + col)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            when (item) {
                                is HomeItem.App -> AppIconItem(
                                    app = item.app,
                                    onClick = { onLaunch(item.app) }
                                )
                                is HomeItem.Folder -> FolderIconItem(
                                    folder = item,
                                    onClick = { onOpenFolder(item) }
                                )
                                null -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}
