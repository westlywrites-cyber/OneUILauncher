package com.westly.oneuilauncher.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.westly.oneuilauncher.data.AppInfo
import com.westly.oneuilauncher.data.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max

const val COLUMNS = 4
const val ROWS = 5
const val DOCK_SLOTS = 4

/**
 * Phase 1 home screen: wallpaper, swipeable pages of apps, dock and page dots.
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

    LaunchedEffect(homeTick) {
        if (homeTick > 0) pagerState.animateScrollToPage(0)
    }

    // A launcher must never close on Back: go to page 1, otherwise do nothing.
    BackHandler(enabled = true) {
        if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(0) }
        }
    }

    val launch: (AppInfo) -> Unit = { app -> AppRepository.launch(context, app) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HomeWallpaper()

        Column(
            modifier = Modifier
                .fillMaxSize()
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
                HomePage(items = items, onLaunch = launch)
            }

            Dock(apps = dock, onLaunch = launch)

            PageDots(
                count = pageCount,
                current = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp, bottom = 12.dp)
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
