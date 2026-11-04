package com.resbox.player.ui.screens.search

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.resbox.player.ui.components.FrostedGlassBar
import com.resbox.player.ui.components.ResPillBadge
import com.resbox.player.ui.components.VodPosterCard
import com.resbox.player.ui.theme.AmbientPaletteColors
import com.resbox.player.ui.theme.LocalIsTvDevice
import com.resbox.player.ui.theme.MeshGradientBackground
import com.resbox.player.ui.theme.ResColors
import com.resbox.player.ui.theme.ResShimmerBox

@Composable
fun SearchScreen(
    uiState: SearchUiState,
    hotKeywords: List<String>,
    onQueryChanged: (String) -> Unit,
    onTriggerSearch: (String?) -> Unit,
    onSelectSiteFilter: (String) -> Unit,
    onOpenVodDetail: (siteKey: String, vodId: String) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val isTv = LocalIsTvDevice.current

    MeshGradientBackground(palette = AmbientPaletteColors()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (isTv) 28.dp else 16.dp,
                    end = if (isTv) 28.dp else 16.dp,
                    top = 16.dp
                )
        ) {
            // Search Input Bar
            FrostedGlassBar(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = onQueryChanged,
                        placeholder = {
                            Text(
                                text = "输入片名、主演或导演，开启多源并发流式搜索...",
                                color = ResColors.TextSecondary
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = ResColors.FocusCyan
                            )
                        },
                        trailingIcon = {
                            if (uiState.query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChanged("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "清空",
                                        tint = ResColors.TextSecondary
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onTriggerSearch(null) }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ResColors.FocusCyan,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Box(
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(ResColors.PrimaryViolet)
                            .clickable { onTriggerSearch(null) }
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "并发搜源",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hot Search Suggestions
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(hotKeywords) { kw ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x661C253E))
                            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
                            .clickable { onTriggerSearch(kw) }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "# $kw",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ResColors.TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Streaming Site Progress & Filter Chips
            if (uiState.totalSites > 0 || uiState.siteBadges.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = ResColors.Gold4KBadge
                    )
                    Text(
                        text = if (uiState.isSearching) {
                            "正在并发嗅探站源 (${uiState.completedSites}/${uiState.totalSites}) • 已流式命中 ${uiState.allResults.size} 部"
                        } else {
                            "全网并发搜索完成 (${uiState.completedSites}/${uiState.totalSites} 站源) • 共 ${uiState.allResults.size} 部结果"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = ResColors.FocusCyan
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    item {
                        val allSelected = uiState.selectedSiteFilterKey == "all"
                        ResPillBadge(
                            text = "全部站源 (${uiState.allResults.size})",
                            backgroundColor = if (allSelected) ResColors.PrimaryViolet else Color(0x881A2238),
                            modifier = Modifier.clickable { onSelectSiteFilter("all") }
                        )
                    }
                    items(uiState.siteBadges, key = { it.siteKey }) { badge ->
                        val selected = uiState.selectedSiteFilterKey == badge.siteKey
                        ResPillBadge(
                            text = "${badge.siteName} (${badge.resultCount}) • ${badge.latencyMs}ms",
                            backgroundColor = if (selected) ResColors.PrimaryViolet else Color(0x881A2238),
                            contentColor = if (selected) Color.White else ResColors.EmeraldScoreBadge,
                            modifier = Modifier.clickable { onSelectSiteFilter(badge.siteKey) }
                        )
                    }
                }
            }

            // Results Grid + Shimmer Slots while still streaming
            val minSize = if (isTv) 185.dp else 148.dp
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = minSize),
                contentPadding = PaddingValues(bottom = if (isTv) 32.dp else 110.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = uiState.displayedResults,
                    key = { "${it.siteKey}_${it.vodId}" }
                ) { vod ->
                    VodPosterCard(
                        vod = vod,
                        onClick = { onOpenVodDetail(vod.siteKey, vod.vodId) },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }

                if (uiState.isSearching) {
                    items(4) {
                        ResShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.68f)
                        )
                    }
                }
            }
        }
    }
}
