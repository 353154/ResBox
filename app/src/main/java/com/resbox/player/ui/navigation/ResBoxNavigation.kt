package com.resbox.player.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.resbox.player.ui.components.FrostedGlassBar
import com.resbox.player.ui.screens.detail.DetailScreen
import com.resbox.player.ui.screens.detail.DetailViewModel
import com.resbox.player.ui.screens.home.HomeScreen
import com.resbox.player.ui.screens.home.HomeViewModel
import com.resbox.player.ui.screens.live.LiveTvScreen
import com.resbox.player.ui.screens.live.LiveViewModel
import com.resbox.player.ui.screens.player.PlayerViewModel
import com.resbox.player.ui.screens.player.VideoPlayerScreen
import com.resbox.player.ui.screens.search.SearchScreen
import com.resbox.player.ui.screens.search.SearchViewModel
import com.resbox.player.ui.screens.settings.SettingsRepoScreen
import com.resbox.player.ui.screens.settings.SettingsViewModel
import com.resbox.player.ui.theme.LocalIsTvDevice
import com.resbox.player.ui.theme.ResColors

enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HOME("home", "精选首页", Icons.Default.Home),
    SEARCH("search", "聚合搜源", Icons.Default.Search),
    LIVE("live", "电视直播", Icons.Default.LiveTv),
    SETTINGS("settings", "多仓设置", Icons.Default.Settings)
}

@UnstableApi
@Composable
fun ResBoxAdaptiveRoot(
    navController: NavHostController = rememberNavController()
) {
    val isTv = LocalIsTvDevice.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route.orEmpty()
    val isTopLevelRoute = TopLevelDestination.entries.any { it.route == currentRoute }

    SharedTransitionLayout {
        if (isTv) {
            // Android TV Adaptive Layout: Left Collapsible Frosted Navigation Drawer + Main Content
            Row(modifier = Modifier.fillMaxSize()) {
                if (isTopLevelRoute) {
                    TvCollapsibleNavigationDrawer(
                        currentRoute = currentRoute,
                        onNavigate = { dest ->
                            navController.navigate(dest.route) {
                                popUpTo(TopLevelDestination.HOME.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    ResBoxNavHost(
                        navController = navController,
                        sharedTransitionScope = this@SharedTransitionLayout
                    )
                }
            }
        } else {
            // Mobile / Tablet Adaptive Layout: Fullscreen Content + Floating Frosted Pill Bottom Bar
            Box(modifier = Modifier.fillMaxSize()) {
                ResBoxNavHost(
                    navController = navController,
                    sharedTransitionScope = this@SharedTransitionLayout
                )

                AnimatedVisibility(
                    visible = isTopLevelRoute,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    MobileFloatingPillBar(
                        currentRoute = currentRoute,
                        onNavigate = { dest ->
                            navController.navigate(dest.route) {
                                popUpTo(TopLevelDestination.HOME.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    }
}

@UnstableApi
@Composable
private fun ResBoxNavHost(
    navController: NavHostController,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.HOME.route
    ) {
        composable(TopLevelDestination.HOME.route) {
            val viewModel: HomeViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()
            val history by viewModel.recentHistory.collectAsState()

            HomeScreen(
                uiState = uiState,
                recentHistory = history,
                onSelectSite = viewModel::switchHomeSite,
                onSelectCategory = viewModel::selectCategory,
                onFocusedPosterChanged = viewModel::onFocusedPosterChanged,
                onOpenVodDetail = { siteKey, vodId ->
                    navController.navigate("detail/${Uri.encode(siteKey)}/${Uri.encode(vodId)}")
                },
                onOpenSearch = {
                    navController.navigate(TopLevelDestination.SEARCH.route)
                },
                onRefresh = viewModel::refresh,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = this
            )
        }

        composable(TopLevelDestination.SEARCH.route) {
            val viewModel: SearchViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()

            SearchScreen(
                uiState = uiState,
                hotKeywords = viewModel.hotKeywords,
                onQueryChanged = viewModel::onQueryChanged,
                onTriggerSearch = viewModel::triggerConcurrentSearch,
                onSelectSiteFilter = viewModel::selectSiteFilter,
                onOpenVodDetail = { siteKey, vodId ->
                    navController.navigate("detail/${Uri.encode(siteKey)}/${Uri.encode(vodId)}")
                },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = this
            )
        }

        composable(TopLevelDestination.LIVE.route) {
            val viewModel: LiveViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()

            LaunchedEffect(Unit) {
                viewModel.initializeLiveTv()
            }

            LiveTvScreen(
                uiState = uiState,
                liveExoPlayer = viewModel.liveExoPlayer,
                onSelectGroup = viewModel::selectGroup,
                onSelectChannel = viewModel::selectChannel,
                onSelectEpgCatchup = viewModel::playCatchupProgram,
                onDisposeLivePlayer = viewModel::releaseLivePlayer
            )
        }

        composable(TopLevelDestination.SETTINGS.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()
            val prefs by viewModel.preferences.collectAsState()
            val repos by viewModel.savedRepos.collectAsState()

            SettingsRepoScreen(
                uiState = uiState,
                preferences = prefs,
                savedRepos = repos,
                onRepoUrlChanged = viewModel::onRepoUrlChanged,
                onRepoNameChanged = viewModel::onRepoNameChanged,
                onAddAndActivateRepo = viewModel::addAndActivateRepo,
                onSelectRepo = viewModel::activateExistingRepo,
                onToggleDynamicColor = viewModel::setEnableDynamicColor,
                onToggleAdBlock = viewModel::setEnableHlsAdBlock,
                onToggleDanmaku = viewModel::setEnableDanmaku,
                onToggleForceTvMode = viewModel::setForceTvMode,
                onTogglePosterScraping = viewModel::setEnablePosterScraping,
                onUpdateTmdbApiKey = viewModel::setTmdbApiKey
            )
        }

        composable(
            route = "detail/{siteKey}/{vodId}",
            arguments = listOf(
                navArgument("siteKey") { type = NavType.StringType },
                navArgument("vodId") { type = NavType.StringType }
            )
        ) { entry ->
            val siteKey = Uri.decode(entry.arguments?.getString("siteKey").orEmpty())
            val vodId = Uri.decode(entry.arguments?.getString("vodId").orEmpty())
            val viewModel: DetailViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()

            LaunchedEffect(siteKey, vodId) {
                viewModel.loadVodDetail(siteKey, vodId)
            }

            DetailScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onSelectSource = viewModel::selectSourceIndex,
                onSelectChunk = viewModel::selectChunkIndex,
                onToggleOrder = viewModel::toggleEpisodeOrder,
                onToggleFavorite = viewModel::toggleFavorite,
                onFastSpeedSwitch = { viewModel.runConcurrentSourceSpeedCheck(autoSelectFastest = true) },
                onPlayEpisode = { sourceIdx, episode ->
                    navController.navigate(
                        "player/${Uri.encode(siteKey)}/${Uri.encode(vodId)}/$sourceIdx/${episode.index}"
                    )
                },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = this
            )
        }

        composable(
            route = "player/{siteKey}/{vodId}/{sourceIndex}/{episodeIndex}",
            arguments = listOf(
                navArgument("siteKey") { type = NavType.StringType },
                navArgument("vodId") { type = NavType.StringType },
                navArgument("sourceIndex") { type = NavType.IntType },
                navArgument("episodeIndex") { type = NavType.IntType }
            )
        ) { entry ->
            val siteKey = Uri.decode(entry.arguments?.getString("siteKey").orEmpty())
            val vodId = Uri.decode(entry.arguments?.getString("vodId").orEmpty())
            val sourceIdx = entry.arguments?.getInt("sourceIndex") ?: 0
            val episodeIdx = entry.arguments?.getInt("episodeIndex") ?: 0
            val viewModel: PlayerViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()

            LaunchedEffect(siteKey, vodId, sourceIdx, episodeIdx) {
                viewModel.initializePlayer(siteKey, vodId, sourceIdx, episodeIdx)
            }

            VideoPlayerScreen(
                uiState = uiState,
                exoPlayer = viewModel.exoPlayer,
                onBack = { navController.popBackStack() },
                onTogglePlayPause = viewModel::togglePlayPause,
                onSeekTo = viewModel::seekToPosition,
                onSeekByDelta = viewModel::seekByDelta,
                onSetSpeed = viewModel::setPlaybackSpeed,
                onSetLongPress2x = viewModel::setLongPress2xBoost,
                onCycleAspectMode = viewModel::cycleAspectMode,
                onCycleKernelType = viewModel::cycleKernelType,
                onToggleDanmaku = viewModel::toggleDanmaku,
                onMarkSkipIntro = viewModel::markCurrentAsSkipIntro,
                onMarkSkipOutro = viewModel::markCurrentAsSkipOutro,
                onPlayNextEpisode = viewModel::playNextEpisodeOrLoop,
                onTriggerFastFailover = { viewModel.triggerAutomaticSourceFailover() },
                onSelectSourceAndEpisode = { sIdx, eIdx ->
                    viewModel.startEpisodePlayback(sIdx, eIdx)
                },
                onTvNumericDigit = viewModel::onTvNumericDigitPressed,
                onDisposePlayer = viewModel::releasePlayerResources
            )
        }
    }
}

@Composable
private fun MobileFloatingPillBar(
    currentRoute: String,
    onNavigate: (TopLevelDestination) -> Unit
) {
    FrostedGlassBar(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopLevelDestination.entries.forEach { dest ->
                val selected = dest.route == currentRoute
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (selected) ResColors.PrimaryViolet
                            else Color.Transparent
                        )
                        .clickable { onNavigate(dest) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = dest.icon,
                        contentDescription = dest.label,
                        tint = if (selected) Color.White else ResColors.TextSecondary
                    )
                    if (selected) {
                        Text(
                            text = dest.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvCollapsibleNavigationDrawer(
    currentRoute: String,
    onNavigate: (TopLevelDestination) -> Unit
) {
    var isDrawerFocused by remember { mutableStateOf(false) }
    val drawerWidth by animateDpAsState(
        targetValue = if (isDrawerFocused) 215.dp else 84.dp,
        label = "TvDrawerWidth"
    )

    FrostedGlassBar(
        modifier = Modifier
            .width(drawerWidth)
            .fillMaxHeight()
            .padding(vertical = 16.dp, horizontal = 8.dp)
            .onFocusChanged { isDrawerFocused = it.hasFocus },
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 24.dp, horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isDrawerFocused) "RESBOX TV" else "RES",
                style = MaterialTheme.typography.titleLarge,
                color = ResColors.FocusCyan
            )
            Spacer(modifier = Modifier.height(12.dp))

            TopLevelDestination.entries.forEach { dest ->
                val selected = dest.route == currentRoute
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (selected) ResColors.PrimaryViolet.copy(alpha = 0.45f)
                            else Color.Transparent
                        )
                        .border(
                            width = if (selected) 2.dp else 0.dp,
                            color = if (selected) ResColors.FocusCyan else Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { onNavigate(dest) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = dest.icon,
                        contentDescription = dest.label,
                        tint = if (selected) ResColors.FocusCyan else ResColors.TextSecondary
                    )
                    if (isDrawerFocused) {
                        Text(
                            text = dest.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected) Color.White else ResColors.TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
