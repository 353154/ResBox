package com.resbox.player.ui.screens.live

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.resbox.player.data.model.LiveChannelItem
import com.resbox.player.data.repository.EpgProgramItem
import com.resbox.player.ui.components.FrostedGlassBar
import com.resbox.player.ui.components.ResPillBadge
import com.resbox.player.ui.theme.AmbientPaletteColors
import com.resbox.player.ui.theme.LocalIsTvDevice
import com.resbox.player.ui.theme.MeshGradientBackground
import com.resbox.player.ui.theme.ResColors
import com.resbox.player.ui.theme.resCardSurface
import com.resbox.player.ui.theme.tvInteractiveItem

@UnstableApi
@Composable
fun LiveTvScreen(
    uiState: LiveUiState,
    liveExoPlayer: ExoPlayer?,
    onSelectGroup: (Int) -> Unit,
    onSelectChannel: (LiveChannelItem, Int) -> Unit,
    onSelectEpgCatchup: (EpgProgramItem) -> Unit,
    onDisposeLivePlayer: () -> Unit
) {
    val isTv = LocalIsTvDevice.current

    DisposableEffect(Unit) {
        onDispose { onDisposeLivePlayer() }
    }

    MeshGradientBackground(palette = AmbientPaletteColors()) {
        if (isTv) {
            // TV Dual-Pane Layout: Left Channel Drawer + Right Live Viewport & EPG Timeline
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                LiveChannelListPanel(
                    uiState = uiState,
                    onSelectGroup = onSelectGroup,
                    onSelectChannel = onSelectChannel,
                    modifier = Modifier
                        .width(340.dp)
                        .fillMaxHeight()
                )
                LiveViewportAndEpgPanel(
                    uiState = uiState,
                    liveExoPlayer = liveExoPlayer,
                    onSelectChannel = onSelectChannel,
                    onSelectEpgCatchup = onSelectEpgCatchup,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        } else {
            // Mobile Vertical Layout: Top Live Viewport + EPG Timeline + Channel List
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                LiveViewportAndEpgPanel(
                    uiState = uiState,
                    liveExoPlayer = liveExoPlayer,
                    onSelectChannel = onSelectChannel,
                    onSelectEpgCatchup = onSelectEpgCatchup,
                    modifier = Modifier.fillMaxWidth()
                )
                LiveChannelListPanel(
                    uiState = uiState,
                    onSelectGroup = onSelectGroup,
                    onSelectChannel = onSelectChannel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 95.dp)
                )
            }
        }
    }
}

@Composable
private fun LiveChannelListPanel(
    uiState: LiveUiState,
    onSelectGroup: (Int) -> Unit,
    onSelectChannel: (LiveChannelItem, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    FrostedGlassBar(modifier = modifier, shape = RoundedCornerShape(24.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "IPTV 直播频道 (M3U / TXT 源)",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            ) {
                itemsIndexed(uiState.groups) { idx, group ->
                    val selected = idx == uiState.selectedGroupIndex
                    Box(
                        modifier = Modifier
                            .tvInteractiveItem(
                                shape = RoundedCornerShape(50),
                                isSelected = selected,
                                onClick = { onSelectGroup(idx) },
                                onFocusChanged = { focused ->
                                    if (focused && !selected) {
                                        onSelectGroup(idx)
                                    }
                                }
                            )
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = group.group,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) Color.White else ResColors.TextSecondary
                        )
                    }
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.activeGroupChannels, key = { it.name }) { ch ->
                    val isCurrent = ch.name == uiState.selectedChannel?.name
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvInteractiveItem(
                                shape = RoundedCornerShape(16.dp),
                                isSelected = isCurrent,
                                onClick = { onSelectChannel(ch, 0) }
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ch.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            ResPillBadge(
                                text = "${ch.urls.size} 源",
                                backgroundColor = if (isCurrent) ResColors.FocusCyan.copy(alpha = 0.25f) else Color(0x44242E4C),
                                contentColor = if (isCurrent) ResColors.FocusCyan else ResColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@UnstableApi
@Composable
private fun LiveViewportAndEpgPanel(
    uiState: LiveUiState,
    liveExoPlayer: ExoPlayer?,
    onSelectChannel: (LiveChannelItem, Int) -> Unit,
    onSelectEpgCatchup: (EpgProgramItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Video Player Surface Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.Black)
                .border(1.5.dp, ResColors.SpecularTop, RoundedCornerShape(22.dp))
        ) {
            if (liveExoPlayer != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            player = liveExoPlayer
                        }
                    },
                    update = { it.player = liveExoPlayer },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Channel & Multi-Line Switch Badge Overlay
            val channel = uiState.selectedChannel
            if (channel != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ResPillBadge(
                        text = if (uiState.isCatchupMode) "回看: ${uiState.activeCatchupTitle}" else "LIVE • ${channel.name}",
                        backgroundColor = if (uiState.isCatchupMode) ResColors.Gold4KBadge else ResColors.CoralAlert,
                        contentColor = Color.White
                    )
                    channel.urls.forEachIndexed { lineIdx, _ ->
                        ResPillBadge(
                            text = "线路 ${lineIdx + 1}",
                            backgroundColor = if (lineIdx == uiState.selectedLineIndex) ResColors.PrimaryViolet else Color(0xAA182038),
                            modifier = Modifier.clickable { onSelectChannel(channel, lineIdx) }
                        )
                    }
                }
            }
        }

        // EPG Program Timeline & Catch-up Bar
        FrostedGlassBar(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "EPG 电子节目单时间轴 (点击已播节目触发 Catch-up 时移回看)",
                    style = MaterialTheme.typography.titleMedium,
                    color = ResColors.FocusCyan
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(uiState.epgPrograms) { prog ->
                        Box(
                            modifier = Modifier
                                .width(220.dp)
                                .resCardSurface(shape = RoundedCornerShape(14.dp))
                                .border(
                                    width = 1.dp,
                                    color = if (prog.isLiveNow) ResColors.EmeraldScoreBadge else Color.White.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { onSelectEpgCatchup(prog) }
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "${prog.startTimeFormatted} - ${prog.endTimeFormatted}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (prog.isLiveNow) ResColors.EmeraldScoreBadge else ResColors.TextSecondary
                                    )
                                    Text(
                                        text = if (prog.isLiveNow) "直播中" else "回看",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ResColors.Gold4KBadge
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = prog.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
