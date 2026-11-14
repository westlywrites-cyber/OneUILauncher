package com.westly.oneuilauncher.feature.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.westly.oneuilauncher.data.AppActions
import com.westly.oneuilauncher.data.AppInfo
import com.westly.oneuilauncher.data.AppRepository
import com.westly.oneuilauncher.data.HomeItem
import com.westly.oneuilauncher.data.HomeItems
import com.westly.oneuilauncher.data.LayoutStore
import com.westly.oneuilauncher.data.WallpaperStore
import com.westly.oneuilauncher.feature.drawer.AppDrawer
import com.westly.oneuilauncher.feature.wallpaper.LauncherWallpaper
import com.westly.oneuilauncher.feature.wallpaper.WallpaperPicker
import com.westly.oneuilauncher.feature.widgets.CalendarWidget
import com.westly.oneuilauncher.feature.widgets.ClockWidget
import com.westly.oneuilauncher.feature.widgets.GoogleSearchWidget
import com.westly.oneuilauncher.feature.widgets.WeatherWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

const val COLUMNS = 4
const val ROWS = 5
const val DOCK_SLOTS = 4

/** Page 1 and page 2 always exist, because they hold the widgets. */
private const val MIN_PAGES = 2

/** Space around the grid on every page. */
private const val PAGE_PAD_H = 12f
private const val PAGE_PAD_V = 8f

/** How close to the left or right edge (in dp) a dragged icon must be to flip the page. */
private const val EDGE_ZONE_DP = 40f

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

/** Which long-press menu is showing. */
private sealed interface MenuState {
    data class ForApp(val app: AppInfo, val anchor: Rect) : MenuState
    data class ForHome(val anchor: Rect) : MenuState
}

/**
 * Works out which grid cell a finger is over. Positions are relative to the box that holds the
 * pager, and the maths matches how [HomePage] lays the rows out.
 */
private class GridGeometry(
    private val width: Float,
    private val height: Float,
    density: Float
) {
    private val padX = PAGE_PAD_H * density
    private val padY = PAGE_PAD_V * density
    private val contentW = width - 2f * padX
    private val contentH = height - 2f * padY
    private val rowH = contentH / ROWS
    private val colW = contentW / COLUMNS

    fun cellAt(x: Float, y: Float, page: Int): Int? {
        if (contentW <= 0f || contentH <= 0f) return null
        val gx = x - padX
        val gy = y - padY - widgetRowsFor(page) * rowH
        if (gx < 0f || gy < 0f || gx >= contentW) return null
        val row = (gy / rowH).toInt()
        val col = (gx / colW).toInt()
        if (row >= ROWS - widgetRowsFor(page)) return null
        return row * COLUMNS + col
    }

    fun cellRect(page: Int, cell: Int): Rect {
        val row = cell / COLUMNS
        val col = cell % COLUMNS
        val left = padX + col * colW
        val top = padY + (widgetRowsFor(page) + row) * rowH
        return Rect(left, top, left + colW, top + rowH)
    }
}

/**
 * Finger handling for the home grid, in one place so that tap, long press and drag never
 * fight each other:
 * - quick tap: [onTap]
 * - long press: [onLongPress]; if it returns true the finger is followed with [onDrag]
 *   until it lifts, then [onDragEnd] says whether the finger really moved.
 */
private suspend fun PointerInputScope.detectHomeGestures(
    onTap: (Offset) -> Unit,
    onLongPress: (Offset) -> Boolean,
    onDrag: (Offset) -> Unit,
    onDragEnd: (Boolean) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val longPress = awaitLongPressOrCancellation(down.id)
        if (longPress == null) {
            val change = currentEvent.changes.firstOrNull { it.id == down.id }
            if (change != null && !change.pressed && !change.isConsumed) {
                onTap(down.position)
            }
            return@awaitEachGesture
        }
        if (!onLongPress(down.position)) return@awaitEachGesture

        var moved = false
        val slop = viewConfiguration.touchSlop
        drag(longPress.id) { change ->
            change.consume()
            if ((change.position - down.position).getDistance() > slop) moved = true
            onDrag(change.position)
        }
        onDragEnd(moved)
    }
}

/**
 * Home screen: wallpaper, swipeable pages with widgets, apps and folders, dock, page dots,
 * and the app drawer that slides up when you swipe up.
 *
 * Long press an app for its menu, long press and drag to rearrange, long press empty space
 * for wallpaper options.
 *
 * @param resumeTick changes every time the launcher comes to the front, so the app list refreshes.
 * @param homeTick changes when Home is pressed while the launcher is already open.
 */
@Composable
fun HomeScreen(resumeTick: Int, homeTick: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    LaunchedEffect(resumeTick) {
        apps = withContext(Dispatchers.IO) { AppRepository.loadLaunchableApps(context) }
    }

    val dock = remember(apps) { AppRepository.resolveDock(context, apps) }

    // The order the user saved by dragging (empty = default order).
    var savedOrder by remember { mutableStateOf(LayoutStore.loadOrder(context)) }
    val baseItems = remember(apps, dock, savedOrder) {
        HomeItems.applyOrder(
            HomeItems.build(apps.filter { app -> dock.none { it.key == app.key } }),
            savedOrder
        )
    }
    // While dragging, items are rearranged live here; the order is saved when the finger lifts.
    var liveItems by remember(baseItems) { mutableStateOf(baseItems) }

    // Split the items over pages. Pages with widgets hold fewer rows of apps.
    val pages = remember(liveItems) {
        val result = mutableListOf<List<HomeItem>>()
        var index = 0
        while (index < liveItems.size || result.size < MIN_PAGES) {
            val capacity = capacityFor(result.size)
            result.add(liveItems.drop(index).take(capacity))
            index += capacity
        }
        result.toList()
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })

    // ---- Wallpaper ----
    var wallpaperChoice by remember { mutableIntStateOf(WallpaperStore.load(context)) }
    var wallpaperStamp by remember { mutableIntStateOf(0) }
    var pickerOpen by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { WallpaperStore.importFromUri(context, uri) }
                if (ok) {
                    WallpaperStore.save(context, WallpaperStore.CUSTOM)
                    wallpaperChoice = WallpaperStore.CUSTOM
                    wallpaperStamp += 1
                }
            }
        }
    }

    // ---- Long-press menu ----
    var menu by remember { mutableStateOf<MenuState?>(null) }
    val showAppMenu: (AppInfo, Rect) -> Unit = { app, rect -> menu = MenuState.ForApp(app, rect) }

    // ---- Dragging an icon to rearrange ----
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var pressAnchor by remember { mutableStateOf(Rect.Zero) }
    var edgeDir by remember { mutableIntStateOf(0) }
    var gridSize by remember { mutableStateOf(IntSize.Zero) }
    var gridOrigin by remember { mutableStateOf(Offset.Zero) }

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

    /** Closes whatever is on top. Returns true if something was closed. */
    fun closeTopLayer(): Boolean {
        return when {
            menu != null -> { menu = null; true }
            pickerOpen -> { pickerOpen = false; true }
            folderOpen -> { folderOpen = false; true }
            drawerProgress > 0f -> { settleDrawer(0f); true }
            else -> false
        }
    }

    LaunchedEffect(homeTick) {
        if (homeTick > 0 && !closeTopLayer()) {
            pagerState.animateScrollToPage(0)
        }
    }

    // A launcher must never close on Back: close the top layer, then go back to page 1,
    // otherwise do nothing.
    BackHandler(enabled = true) {
        if (!closeTopLayer() && pagerState.currentPage > 0) {
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

    fun geometry() = GridGeometry(
        gridSize.width.toFloat(),
        gridSize.height.toFloat(),
        density.density
    )

    /** While dragging: move the dragged item to the cell under the finger. */
    fun moveDragged(pos: Offset) {
        val id = dragId ?: return
        val page = pagerState.currentPage
        val cell = geometry().cellAt(pos.x, pos.y, page) ?: return
        var start = 0
        for (p in 0 until page) start += capacityFor(p)
        val list = liveItems
        val from = list.indexOfFirst { it.id == id }
        if (from < 0) return
        val target = (start + cell).coerceIn(0, list.lastIndex)
        if (target == from) return
        val moved = list.toMutableList()
        val item = moved.removeAt(from)
        moved.add(target, item)
        liveItems = moved
    }

    fun handleTap(pos: Offset) {
        if (dragId != null) return
        val page = pagerState.currentPage
        val cell = geometry().cellAt(pos.x, pos.y, page) ?: return
        when (val item = pages.getOrNull(page)?.getOrNull(cell)) {
            is HomeItem.App -> launchApp(item.app)
            is HomeItem.Folder -> openFolder(item)
            null -> Unit
        }
    }

    /** Returns true when an icon was picked up and should follow the finger. */
    fun handleLongPress(pos: Offset): Boolean {
        val page = pagerState.currentPage
        val geo = geometry()
        val cell = geo.cellAt(pos.x, pos.y, page) ?: return false
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

        val item = pages.getOrNull(page)?.getOrNull(cell)
        if (item == null) {
            menu = MenuState.ForHome(Rect(center = gridOrigin + pos, radius = 1f))
            return false
        }
        val local = geo.cellRect(page, cell)
        pressAnchor = local.translate(gridOrigin)
        dragPos = pos
        edgeDir = 0
        dragId = item.id
        return true
    }

    fun handleDrag(pos: Offset) {
        dragPos = pos
        val edge = EDGE_ZONE_DP * density.density
        edgeDir = when {
            pos.x < edge -> -1
            pos.x > gridSize.width - edge -> 1
            else -> 0
        }
        moveDragged(pos)
    }

    fun handleDragEnd(moved: Boolean) {
        val id = dragId ?: return
        dragId = null
        edgeDir = 0
        val ids = liveItems.map { it.id }
        if (moved) {
            if (ids != baseItems.map { it.id }) {
                LayoutStore.saveOrder(context, ids)
                savedOrder = ids
            }
        } else {
            val item = liveItems.firstOrNull { it.id == id }
            if (item is HomeItem.App) {
                menu = MenuState.ForApp(item.app, pressAnchor)
            }
        }
    }

    val tapHandler = rememberUpdatedState<(Offset) -> Unit> { handleTap(it) }
    val longPressHandler = rememberUpdatedState<(Offset) -> Boolean> { handleLongPress(it) }
    val dragHandler = rememberUpdatedState<(Offset) -> Unit> { handleDrag(it) }
    val dragEndHandler = rememberUpdatedState<(Boolean) -> Unit> { handleDragEnd(it) }

    // Holding an icon near the screen edge flips to the next page.
    LaunchedEffect(edgeDir, pagerState.currentPage, dragId) {
        if (dragId != null && edgeDir != 0) {
            delay(650L)
            val target = pagerState.currentPage + edgeDir
            if (target in 0 until pagerState.pageCount) {
                pagerState.animateScrollToPage(target)
                moveDragged(dragPos)
            }
        }
    }

    val draggableState = rememberDraggableState { dy -> dragDrawer(dy) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
    ) {
        LauncherWallpaper(choice = wallpaperChoice, stamp = wallpaperStamp)

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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { gridSize = it }
                    .onGloballyPositioned { gridOrigin = it.positionInRoot() }
                    .pointerInput(Unit) {
                        detectHomeGestures(
                            onTap = { tapHandler.value(it) },
                            onLongPress = { longPressHandler.value(it) },
                            onDrag = { dragHandler.value(it) },
                            onDragEnd = { dragEndHandler.value(it) }
                        )
                    }
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = dragId == null
                ) { page ->
                    HomePage(
                        pageIndex = page,
                        items = pages.getOrElse(page) { emptyList() },
                        hiddenId = dragId,
                        refreshKey = resumeTick
                    )
                }
            }

            Dock(
                apps = dock,
                onLaunch = launchApp,
                onAppLongClick = showAppMenu
            )

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
                onRelease = { velocityY -> releaseDrawer(velocityY) },
                onAppLongClick = showAppMenu
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
                    onDismiss = { folderOpen = false },
                    onAppLongClick = showAppMenu
                )
            }
        }

        // The icon that follows the finger while rearranging.
        val draggedItem = dragId?.let { id -> liveItems.firstOrNull { it.id == id } }
        if (draggedItem != null) {
            val halfPx = with(density) { 40.dp.toPx() }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (gridOrigin.x + dragPos.x - halfPx).roundToInt(),
                            (gridOrigin.y + dragPos.y - halfPx).roundToInt()
                        )
                    }
                    .width(80.dp)
                    .graphicsLayer {
                        scaleX = 1.15f
                        scaleY = 1.15f
                        alpha = 0.95f
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                when (draggedItem) {
                    is HomeItem.App -> AppIconItem(app = draggedItem.app, interactive = false)
                    is HomeItem.Folder -> FolderIconItem(folder = draggedItem, interactive = false)
                }
            }
        }

        // Wallpaper sheet.
        AnimatedVisibility(
            visible = pickerOpen,
            enter = fadeIn(tween(180)) + slideInVertically(tween(260)) { it / 4 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 4 }
        ) {
            WallpaperPicker(
                selected = wallpaperChoice,
                onSelectPreset = { index ->
                    WallpaperStore.save(context, index)
                    wallpaperChoice = index
                },
                onChooseFromGallery = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onDismiss = { pickerOpen = false }
            )
        }

        // Long-press menu (always on top).
        when (val state = menu) {
            is MenuState.ForApp -> ContextMenu(
                anchor = state.anchor,
                entries = listOf(
                    MenuEntry("App info") { AppActions.openAppInfo(context, state.app.packageName) },
                    MenuEntry("Uninstall") { AppActions.uninstall(context, state.app.packageName) }
                ),
                onDismiss = { menu = null }
            )
            is MenuState.ForHome -> ContextMenu(
                anchor = state.anchor,
                entries = listOf(
                    MenuEntry("Wallpaper") { pickerOpen = true },
                    MenuEntry("Reset home layout") {
                        LayoutStore.clear(context)
                        savedOrder = emptyList()
                    }
                ),
                onDismiss = { menu = null }
            )
            null -> Unit
        }
    }
}

/**
 * One page: optional widgets at the top, then a fixed grid of apps and folders.
 * Every grid row has the same height on every page, so icons line up from page to page.
 * Taps, long presses and dragging are handled by the parent, so icons here are not clickable.
 */
@Composable
private fun HomePage(
    pageIndex: Int,
    items: List<HomeItem>,
    hiddenId: String?,
    refreshKey: Int
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PAGE_PAD_H.dp, vertical = PAGE_PAD_V.dp)
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
                                .fillMaxHeight()
                                .graphicsLayer {
                                    // The picked-up icon is shown by the floating copy instead.
                                    alpha = if (item != null && item.id == hiddenId) 0f else 1f
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            when (item) {
                                is HomeItem.App -> AppIconItem(
                                    app = item.app,
                                    interactive = false
                                )
                                is HomeItem.Folder -> FolderIconItem(
                                    folder = item,
                                    interactive = false
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
