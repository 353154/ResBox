package com.resbox.player.ui.screens.detail

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.resbox.player.data.model.VodEpisode
import com.resbox.player.ui.components.FrostedGlassBar
import com.resbox.player.ui.components.ResPillBadge
import com.resbox.player.ui.theme.LocalIsTvDevice
import com.resbox.player.ui.theme.MeshGradientBackground
import com.resbox.player.ui.theme.ResColors
import com.resbox.player.ui.theme.ResShimmerBox
import com.resbox.player.ui.theme.rememberPosterAmbientPalette
import com.resbox.player.ui.theme.resCardSurface

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    uiState: DetailUiState,
    onBack: () -> Unit,
    onSelectSource: (Int) -> Unit,
    onSelectChunk: (Int) -> Unit,
    onToggleOrder: () -> Unit,
    onToggleFavorite: () -> Unit,
    onFastSpeedSwitch: () -> Unit,
    onPlayEpisode: (sourceIndex: Int, episode: VodEpisode) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val vod = uiState.vod
    val ambientPalette = rememberPosterAmbientPalette(vod?.displayPoster.orEmpty())
    val isTv = LocalIsTvDevice.current

    MeshGradientBackground(palette = ambientPalette) {
        if (uiState.isLoading || vod == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                ResShimmerBox(modifier = Modifier.fillMaxWidth().height(260.dp))
                ResShimmerBox(modifier = Modifier.fillMaxWidth().height(64.dp))
                ResShimmerBox(modifier = Modifier.fillMaxWidth().height(220.dp))
            }
            return@MeshGradientBackground
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = if (isTv) 36.dp else 16.dp,
                end = if (isTv) 36.dp else 16.dp,
                top = 16.dp,
                bottom = 48.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Top Navigation & Smart Speed-Switch Action Bar
            item {
                FrostedGlassBar(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回",
                                    tint = Color.White
                                )
                            }
                            Text(
                                text = vod.vodName,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Fast-Switch Speed Check Button
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(ResColors.PrimaryViolet.copy(alpha = 0.28f))
                                    .border(1.dp, ResColors.FocusCyan, RoundedCornerShape(50))
                                    .clickable { onFastSpeedSwitch() }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = ResColors.Gold4KBadge
                                )
                                Text(
                                    text = if (uiState.isSpeedTesting) "并发测速中..." else "智能测速优选最快源",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                            }

                            IconButton(onClick = onToggleFavorite) {
                                Icon(
                                    imageVector = if (uiState.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "收藏",
                                    tint = if (uiState.isFavorite) ResColors.CoralAlert else Color.White
                                )
                            }
                        }
                    }
                }
            }

            // 2. Hero Metadata Card (Poster with SharedElementTransition + Synopsis + Play Resume)
            item {
                FrostedGlassBar(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        val posterModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                            with(sharedTransitionScope) {
                                Modifier.sharedElement(
                                    state = rememberSharedContentState(key = "poster_${vod.siteKey}_${vod.vodId}"),
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            }
                        } else {
                            Modifier
                        }

                        AsyncImage(
                            model = vod.displayPoster,
                            contentDescription = vod.vodName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(if (isTv) 180.dp else 125.dp)
                                .aspectRatio(0.68f)
                                .then(posterModifier)
                                .clip(RoundedCornerShape(20.dp))
                                .border(1.dp, ResColors.SpecularTop, RoundedCornerShape(20.dp))
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = vod.vodName,
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ResPillBadge(
                                    text = vod.qualityBadge,
                                    backgroundColor = ResColors.Gold4KBadge,
                                    contentColor = Color(0xFF101218)
                                )
                                if (vod.scrapedPoster.isNotBlank()) {
                                    ResPillBadge(
                                        text = "TMDB 原画刮削",
                                        backgroundColor = ResColors.PrimaryViolet.copy(alpha = 0.85f),
                                        contentColor = Color.White
                                    )
                                }
                                if (vod.vodScore.isNotBlank()) {
                                    ResPillBadge(
                                        text = "★ ${vod.vodScore}",
                                        backgroundColor = ResColors.EmeraldScoreBadge,
                                        contentColor = Color(0xFF101218)
                                    )
                                }
                                if (vod.vodYear.isNotBlank()) {
                                    ResPillBadge(text = vod.vodYear, backgroundColor = Color(0x9925304D))
                                }
                                if (vod.vodArea.isNotBlank()) {
                                    ResPillBadge(text = vod.vodArea, backgroundColor = Color(0x9925304D))
                                }
                                if (vod.siteName.isNotBlank()) {
                                    ResPillBadge(
                                        text = "站源: ${vod.siteName}",
                                        backgroundColor = Color(0x991C3448),
                                        contentColor = ResColors.FocusCyan
                                    )
                                }
                            }

                            if (vod.vodDirector.isNotBlank() || vod.vodActor.isNotBlank()) {
                                Text(
                                    text = "导演: ${vod.vodDirector.ifBlank { "未知" }}  |  主演: ${vod.vodActor}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ResColors.TextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = vod.vodContent.ifBlank { "暂无剧情简介" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.86f),
                                maxLines = if (isTv) 4 else 3,
                                overflow = TextOverflow.Ellipsis
                            )

                            val firstEpisode = uiState.activeSource?.episodes?.getOrNull(uiState.selectedEpisodeIndex)
                                ?: uiState.activeSource?.episodes?.firstOrNull()
                            if (firstEpisode != null) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(ResColors.PrimaryViolet)
                                        .resCardSurface(shape = RoundedCornerShape(50))
                                        .clickable {
                                            onPlayEpisode(uiState.selectedSourceIndex, firstEpisode)
                                        }
                                        .padding(horizontal = 22.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                    val resumeLabel = uiState.savedHistory?.let {
                                        "续播 ${it.episodeTitle} (${it.sourceFlag})"
                                    } ?: "立即播放 ${firstEpisode.title}"
                                    Text(
                                        text = resumeLabel,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Multi-Source PlayFrom Selector Row with Real-Time Ping Latency
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "播放线路 (PlayFrom • 播放卡顿或失败将自动无缝切换最快同名源)",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(uiState.playSources) { idx, source ->
                            val isSelected = idx == uiState.selectedSourceIndex
                            val latencyLabel = if (source.latencyMs > 0L) " • ${source.latencyMs}ms" else ""
                            Box(
                                modifier = Modifier
                                    .resCardSurface(shape = RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) ResColors.PrimaryViolet.copy(alpha = 0.45f)
                                        else ResColors.ElevatedCardSurface
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) ResColors.FocusCyan else Color.White.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { onSelectSource(idx) }
                                    .padding(horizontal = 18.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = source.flag,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White
                                    )
                                    if (latencyLabel.isNotBlank()) {
                                        Text(
                                            text = latencyLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (idx == 0) ResColors.EmeraldScoreBadge else ResColors.FocusCyan
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Smart Episode Range Chunks (`1-50`, `51-100`) & Ascending/Descending Toggle
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "智能选集 (共 ${uiState.activeSource?.episodes?.size ?: 0} 集)",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0x881A2238))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                                .clickable(onClick = onToggleOrder)
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = "正倒序切换",
                                tint = ResColors.FocusCyan
                            )
                            Text(
                                text = if (uiState.isAscendingOrder) "正序排列" else "倒序排列",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }

                    // Range Chunk Tabs (`1-50`, `51-100`...) when episode count > 50
                    if (uiState.episodeChunks.size > 1) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(uiState.episodeChunks) { chunkIdx, range ->
                                val active = chunkIdx == uiState.selectedChunkIndex
                                ResPillBadge(
                                    text = "${range.first + 1} - ${range.last + 1} 集",
                                    backgroundColor = if (active) ResColors.PrimaryViolet else Color(0x881A2238),
                                    contentColor = Color.White,
                                    modifier = Modifier.clickable { onSelectChunk(chunkIdx) }
                                )
                            }
                        }
                    }

                    // Episode Grid Buttons
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        uiState.displayedEpisodes.forEach { episode ->
                            val isCurrentEp = episode.index == uiState.selectedEpisodeIndex
                            Box(
                                modifier = Modifier
                                    .resCardSurface(shape = RoundedCornerShape(14.dp))
                                    .background(
                                        if (isCurrentEp) ResColors.PrimaryViolet
                                        else ResColors.ElevatedCardSurface
                                    )
                                    .clickable {
                                        onPlayEpisode(uiState.selectedSourceIndex, episode)
                                    }
                                    .padding(horizontal = 18.dp, vertical = 11.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = episode.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
