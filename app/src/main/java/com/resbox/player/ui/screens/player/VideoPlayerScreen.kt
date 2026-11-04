package com.resbox.player.ui.screens.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.resbox.player.media.danmaku.CoroutineDanmakuOverlay
import com.resbox.player.media.kernel.VideoAspectMode
import com.resbox.player.ui.components.FrostedGlassBar
import com.resbox.player.ui.components.ResPillBadge
import com.resbox.player.ui.theme.ResColors
import java.util.Locale

@UnstableApi
@Composable
fun VideoPlayerScreen(
    uiState: PlayerUiState,
    exoPlayer: ExoPlayer?,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekByDelta: (Long) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetLongPress2x: (Boolean) -> Unit,
    onCycleAspectMode: () -> Unit,
    onCycleKernelType: () -> Unit,
    onToggleDanmaku: () -> Unit,
    onMarkSkipIntro: () -> Unit,
    onMarkSkipOutro: () -> Unit,
    onPlayNextEpisode: () -> Unit,
    onTriggerFastFailover: () -> Unit,
    onSelectSourceAndEpisode: (sourceIdx: Int, epIdx: Int) -> Unit,
    onTvNumericDigit: (Int) -> Unit,
    onDisposePlayer: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var showOsdControls by remember { mutableStateOf(true) }
    var gestureHudText by remember { mutableStateOf("") }
    var brightnessLevel by remember { mutableFloatStateOf(0.6f) }

    // Ensure complete teardown of ExoPlayer and WebView Sniffer when leaving screen (Coding Rule #2)
    DisposableEffect(Unit) {
        onDispose {
            onDisposePlayer()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // D-Pad & TV Remote Numeric Keypad Handler
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (val keyCode = event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            showOsdControls = true
                            onTogglePlayPause()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            showOsdControls = true
                            onSeekByDelta(10_000L)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            showOsdControls = true
                            onSeekByDelta(-10_000L)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                            showOsdControls = !showOsdControls
                            true
                        }
                        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                            onTvNumericDigit(keyCode - KeyEvent.KEYCODE_0)
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .focusable()
            // Mobile Touch Gestures: Tap OSD, Double-Tap Play/Pause, Long-Press 2.0x Speed
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showOsdControls = !showOsdControls },
                    onDoubleTap = { onTogglePlayPause() },
                    onLongPress = { onSetLongPress2x(true) },
                    onPress = {
                        tryAwaitRelease()
                        onSetLongPress2x(false)
                    }
                )
            }
            // Horizontal Swipe Gesture: Seek Video Progress
            .pointerInput(uiState.durationMs) {
                detectHorizontalDragGestures(
                    onDragEnd = { gestureHudText = "" },
                    onHorizontalDrag = { _, dragAmount ->
                        val deltaMs = (dragAmount * 120f).toLong()
                        onSeekByDelta(deltaMs)
                        gestureHudText = "进度调节: ${formatMs(uiState.currentPositionMs)}"
                    }
                )
            }
            // Vertical Swipe Gesture: Left Half = Brightness, Right Half = Volume
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { gestureHudText = "" },
                    onVerticalDrag = { change, dragAmount ->
                        val isLeftSide = change.position.x < size.width / 2f
                        if (isLeftSide) {
                            brightnessLevel = (brightnessLevel - dragAmount / 600f).coerceIn(0.08f, 1.0f)
                            activity?.window?.let { win ->
                                val params = win.attributes
                                params.screenBrightness = brightnessLevel
                                win.attributes = params
                            }
                            gestureHudText = "屏幕亮度: ${(brightnessLevel * 100).toInt()}%"
                        } else {
                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val step = if (dragAmount < -8f) 1 else if (dragAmount > 8f) -1 else 0
                            val targetVol = (curVol + step).coerceIn(0, maxVol)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                            gestureHudText = "媒体音量: ${(targetVol * 100) / maxVol}%"
                        }
                    }
                )
            }
    ) {
        // 1. Media3 ExoPlayer Surface
        if (exoPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        player = exoPlayer
                    }
                },
                update = { view ->
                    view.player = exoPlayer
                    view.resizeMode = when (uiState.aspectMode) {
                        VideoAspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        VideoAspectMode.FILL_CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        VideoAspectMode.STRETCH_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        VideoAspectMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. 60fps Coroutine Canvas Danmaku Layer
        CoroutineDanmakuOverlay(
            enabled = uiState.enableDanmaku,
            isPlaying = uiState.isPlaying,
            commentsPool = uiState.danmakuPool
        )

        // 3. Long-Press 2.0x Speed Boost Floating Pill
        AnimatedVisibility(
            visible = uiState.isLongPress2xActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        ) {
            FrostedGlassBar(shape = RoundedCornerShape(50)) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = null,
                        tint = ResColors.Gold4KBadge
                    )
                    Text(
                        text = "2.0x 极速快进中 (松手恢复)",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
        }

        // 4. Center Gesture / Sniffer / AdBlock Status HUD
        val centerMessage = gestureHudText.ifBlank { uiState.statusBannerMessage }
        if (centerMessage.isNotBlank()) {
            FrostedGlassBar(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = centerMessage,
                    style = MaterialTheme.typography.titleMedium,
                    color = ResColors.FocusCyan,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp)
                )
            }
        }

        // 5. Top & Bottom Frosted Glass OSD Controls
        AnimatedVisibility(
            visible = showOsdControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top OSD Bar
                FrostedGlassBar(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回",
                                    tint = Color.White
                                )
                            }
                            Column {
                                Text(
                                    text = "${uiState.vod?.vodName.orEmpty()} • ${uiState.currentEpisode?.title.orEmpty()}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = "当前线路: ${uiState.currentSource?.flag.orEmpty()} | ${uiState.kernelType.displayName}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ResColors.FocusCyan
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (uiState.filteredAdSegmentsCount > 0) {
                                ResPillBadge(
                                    text = "已净网过滤 ${uiState.filteredAdSegmentsCount} 广告切片",
                                    backgroundColor = ResColors.EmeraldScoreBadge,
                                    contentColor = Color(0xFF0A1214)
                                )
                            }
                            ResPillBadge(
                                text = "⚡ 秒换最快源",
                                backgroundColor = ResColors.PrimaryViolet,
                                modifier = Modifier.clickable { onTriggerFastFailover() }
                            )
                        }
                    }
                }

                // Bottom OSD Control Panel
                FrostedGlassBar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Progress Slider Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = formatMs(uiState.currentPositionMs),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                            Slider(
                                value = uiState.currentPositionMs.toFloat(),
                                onValueChange = { onSeekTo(it.toLong()) },
                                valueRange = 0f..(uiState.durationMs.coerceAtLeast(1L).toFloat()),
                                colors = SliderDefaults.colors(
                                    thumbColor = ResColors.FocusCyan,
                                    activeTrackColor = ResColors.PrimaryViolet
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatMs(uiState.durationMs),
                                style = MaterialTheme.typography.bodyMedium,
                                color = ResColors.TextSecondary
                            )
                        }

                        // Action Controls & Feature Pills Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(onClick = onTogglePlayPause) {
                                    Icon(
                                        imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "播放/暂停",
                                        tint = Color.White
                                    )
                                }
                                IconButton(onClick = onPlayNextEpisode) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "下一集",
                                        tint = Color.White
                                    )
                                }

                                // Speed cycle pill (0.5x - 3.0x)
                                val speeds = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)
                                ResPillBadge(
                                    text = "倍速 ${uiState.playbackSpeed}x",
                                    backgroundColor = Color(0x99242E4C),
                                    modifier = Modifier.clickable {
                                        val nextIdx = (speeds.indexOf(uiState.playbackSpeed) + 1) % speeds.size
                                        onSetSpeed(speeds[nextIdx])
                                    }
                                )

                                ResPillBadge(
                                    text = if (uiState.enableDanmaku) "弹幕: 开" else "弹幕: 关",
                                    backgroundColor = if (uiState.enableDanmaku) ResColors.PrimaryViolet else Color(0x88242E4C),
                                    modifier = Modifier.clickable { onToggleDanmaku() }
                                )

                                ResPillBadge(
                                    text = "画面: ${uiState.aspectMode.label}",
                                    backgroundColor = Color(0x99242E4C),
                                    modifier = Modifier.clickable { onCycleAspectMode() }
                                )

                                ResPillBadge(
                                    text = "记片头",
                                    backgroundColor = Color(0x99242E4C),
                                    modifier = Modifier.clickable { onMarkSkipIntro() }
                                )

                                ResPillBadge(
                                    text = "记片尾",
                                    backgroundColor = Color(0x99242E4C),
                                    modifier = Modifier.clickable { onMarkSkipOutro() }
                                )

                                ResPillBadge(
                                    text = "切换内核",
                                    backgroundColor = Color(0x99242E4C),
                                    modifier = Modifier.clickable { onCycleKernelType() }
                                )
                            }
                        }

                        // Quick Episode Switcher Strip inside OSD
                        val episodes = uiState.currentSource?.episodes.orEmpty()
                        if (episodes.size > 1) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(episodes) { epIdx, ep ->
                                    val active = epIdx == uiState.activeEpisodeIndex
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (active) ResColors.PrimaryViolet
                                                else Color(0x881A2238)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (active) ResColors.FocusCyan else Color.White.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                onSelectSourceAndEpisode(uiState.activeSourceIndex, epIdx)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = ep.title,
                                            style = MaterialTheme.typography.labelMedium,
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
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000
    val hrs = totalSec / 3600
    val mins = (totalSec % 3600) / 60
    val secs = totalSec % 60
    return if (hrs > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}
