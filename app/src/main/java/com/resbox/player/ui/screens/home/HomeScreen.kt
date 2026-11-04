package com.resbox.player.ui.screens.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.resbox.player.data.local.entity.WatchHistoryEntity
import com.resbox.player.data.model.SiteBean
import com.resbox.player.data.model.VodInfo
import com.resbox.player.ui.components.FrostedGlassBar
import com.resbox.player.ui.components.ResPillBadge
import com.resbox.player.ui.components.VodPosterCard
import com.resbox.player.ui.theme.LocalIsTvDevice
import com.resbox.player.ui.theme.MeshGradientBackground
import com.resbox.player.ui.theme.ResColors
import com.resbox.player.ui.theme.ResShimmerBox
import com.resbox.player.ui.theme.rememberPosterAmbientPalette
import com.resbox.player.ui.theme.resCardSurface

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    recentHistory: List<WatchHistoryEntity>,
    onSelectSite: (SiteBean) -> Unit,
    onSelectCategory: (String) -> Unit,
    onFocusedPosterChanged: (String) -> Unit,
    onOpenVodDetail: (siteKey: String, vodId: String) -> Unit,
    onOpenSearch: () -> Unit,
    onRefresh: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val isTv = LocalIsTvDevice.current
    val ambientPalette = rememberPosterAmbientPalette(uiState.focusedPosterUrl)
    val minCardWidth = if (isTv) 185.dp else 148.dp

    MeshGradientBackground(palette = ambientPalette) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = minCardWidth),
            contentPadding = PaddingValues(
                start = if (isTv) 28.dp else 16.dp,
                end = if (isTv) 28.dp else 16.dp,
                top = 16.dp,
                bottom = if (isTv) 36.dp else 110.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Frosted Top Bar: Active Site Switcher + Quick Search Trigger
            item(span = { GridItemSpan(maxLineSpan) }) {
                HomeTopFrostedHeader(
                    currentSite = uiState.currentSite,
                    availableSites = uiState.availableSites,
                    onSelectSite = onSelectSite,
                    onOpenSearch = onOpenSearch,
                    onRefresh = onRefresh
                )
            }

            // 2. Category Pill Filter Row
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(uiState.categories, key = { it.typeId }) { category ->
                        val selected = category.typeId == uiState.selectedCategoryId
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (selected) ResColors.PrimaryViolet
                                    else Color(0x881A2238)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) ResColors.FocusCyan else Color.White.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(50)
                                )
                                .clickable { onSelectCategory(category.typeId) }
                                .padding(horizontal = 18.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = category.typeName,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selected) Color.White else ResColors.TextSecondary
                            )
                        }
                    }
                }
            }

            // 3. Hero Banner Card
            uiState.heroBannerVod?.let { hero ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HeroShowcaseBanner(
                        hero = hero,
                        onPlayNow = { onOpenVodDetail(hero.siteKey, hero.vodId) },
                        onFocusChanged = { focused ->
                            if (focused) onFocusedPosterChanged(hero.vodPic)
                        }
                    )
                }
            }

            // 4. Continue Watching Row (if history exists)
            if (recentHistory.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ContinueWatchingSection(
                        historyList = recentHistory,
                        onResumeVod = { item -> onOpenVodDetail(item.siteKey, item.vodId) }
                    )
                }
            }

            // 5. Shimmer Skeleton Loading State or Poster Grid
            if (uiState.isLoading) {
                items(12) {
                    ResShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.68f)
                    )
                }
            } else {
                items(
                    items = uiState.vodItems,
                    key = { "${it.siteKey}_${it.vodId}" }
                ) { vod ->
                    VodPosterCard(
                        vod = vod,
                        onClick = { onOpenVodDetail(vod.siteKey, vod.vodId) },
                        onFocusedPosterChanged = onFocusedPosterChanged,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTopFrostedHeader(
    currentSite: SiteBean,
    availableSites: List<SiteBean>,
    onSelectSite: (SiteBean) -> Unit,
    onOpenSearch: () -> Unit,
    onRefresh: () -> Unit
) {
    var showSiteMenu by remember { mutableStateOf(false) }

    FrostedGlassBar(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Active Spider Site Switcher Pill
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(ResColors.PrimaryViolet.copy(alpha = 0.25f))
                        .border(1.dp, ResColors.PrimaryViolet.copy(alpha = 0.7f), RoundedCornerShape(50))
                        .clickable { showSiteMenu = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "切换首页主源",
                        tint = ResColors.FocusCyan
                    )
                    Text(
                        text = currentSite.name.ifBlank { "选择主站源" },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }

                DropdownMenu(
                    expanded = showSiteMenu,
                    onDismissRequest = { showSiteMenu = false }
                ) {
                    availableSites.forEach { site ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${site.name} (${if (site.isSpiderSite) "Spider" else "CMS"})",
                                    color = if (site.key == currentSite.key) ResColors.PrimaryViolet else Color.Unspecified
                                )
                            },
                            onClick = {
                                showSiteMenu = false
                                onSelectSite(site)
                            }
                        )
                    }
                }
            }

            // Middle: Search Pill Bar
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x66101524))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                    .clickable(onClick = onOpenSearch)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "聚合搜索",
                    tint = ResColors.TextSecondary
                )
                Text(
                    text = "并发搜索全网 4K 源 (channelFlow 极速流式返回)...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ResColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right: Refresh Button
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "刷新首页",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun HeroShowcaseBanner(
    hero: VodInfo,
    onPlayNow: () -> Unit,
    onFocusChanged: (Boolean) -> Unit
) {
    val bannerShape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(235.dp)
            .resCardSurface(shape = bannerShape, onFocusAccentChanged = onFocusChanged)
            .clickable(onClick = onPlayNow)
    ) {
        AsyncImage(
            model = hero.displayBackdrop,
            contentDescription = hero.vodName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xF2090C15),
                            Color(0xBF090C15),
                            Color(0x33090C15)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.76f)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ResPillBadge(
                    text = hero.qualityBadge,
                    backgroundColor = ResColors.Gold4KBadge,
                    contentColor = Color(0xFF101218)
                )
                if (hero.vodScore.isNotBlank()) {
                    ResPillBadge(
                        text = "豆瓣 ${hero.vodScore}",
                        backgroundColor = ResColors.EmeraldScoreBadge,
                        contentColor = Color(0xFF101218)
                    )
                }
                if (hero.typeName.isNotBlank()) {
                    ResPillBadge(
                        text = hero.typeName,
                        backgroundColor = Color(0x99263252),
                        contentColor = Color.White
                    )
                }
            }

            Text(
                text = hero.vodName,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = hero.vodContent.ifBlank { hero.vodActor },
                style = MaterialTheme.typography.bodyMedium,
                color = ResColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(ResColors.PrimaryViolet)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White
                )
                Text(
                    text = "立即智能选源播放",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingSection(
    historyList: List<WatchHistoryEntity>,
    onResumeVod: (WatchHistoryEntity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "继续观看 (跨源记忆进度)",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(historyList, key = { it.compositeId }) { item ->
                FrostedGlassBar(
                    modifier = Modifier
                        .width(260.dp)
                        .resCardSurface(shape = RoundedCornerShape(18.dp))
                        .clickable { onResumeVod(item) },
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = item.vodName,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${item.sourceFlag} • ${item.episodeTitle}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ResColors.FocusCyan,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
