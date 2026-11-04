package com.resbox.player.ui.screens.player

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.resbox.player.data.local.dao.WatchHistoryDao
import com.resbox.player.data.local.datastore.SettingsDataStore
import com.resbox.player.data.local.entity.WatchHistoryEntity
import com.resbox.player.data.model.VodEpisode
import com.resbox.player.data.model.VodInfo
import com.resbox.player.data.model.VodPlaySource
import com.resbox.player.data.repository.ConfigRepository
import com.resbox.player.data.repository.VodRepository
import com.resbox.player.media.adblock.AdBlockHlsPlaylistParserFactory
import com.resbox.player.media.danmaku.DanmakuComment
import com.resbox.player.media.kernel.PlayerKernelType
import com.resbox.player.media.kernel.TrackItemInfo
import com.resbox.player.media.kernel.VideoAspectMode
import com.resbox.player.media.sniffer.HeadlessWebSniffer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@Immutable
data class PlayerUiState(
    val vod: VodInfo? = null,
    val playSources: List<VodPlaySource> = emptyList(),
    val activeSourceIndex: Int = 0,
    val activeEpisodeIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = true,
    val isSniffingWebStream: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val isLongPress2xActive: Boolean = false,
    val aspectMode: VideoAspectMode = VideoAspectMode.FIT,
    val kernelType: PlayerKernelType = PlayerKernelType.MEDIA3_EXO,
    val enableDanmaku: Boolean = true,
    val enableHlsAdBlock: Boolean = true,
    val filteredAdSegmentsCount: Int = 0,
    val skipIntroMs: Long = 0L,
    val skipOutroMs: Long = 0L,
    val audioTracks: List<TrackItemInfo> = emptyList(),
    val subtitleTracks: List<TrackItemInfo> = emptyList(),
    val statusBannerMessage: String = "",
    val tvKeypadBuffer: String = "",
    val danmakuPool: List<DanmakuComment> = defaultDanmakuComments
) {
    val currentSource: VodPlaySource?
        get() = playSources.getOrNull(activeSourceIndex)

    val currentEpisode: VodEpisode?
        get() = currentSource?.episodes?.getOrNull(activeEpisodeIndex)
}

private val defaultDanmakuComments = listOf(
    DanmakuComment(1L, "ResBox 4K HDR 画质太丝滑了！！", 0xFFFFFFFF.toInt(), speedPxPerSec = 250f),
    DanmakuComment(2L, "已自动剔除 TS 切片贴片广告，直接正片起飞", 0xFF00E5FF.toInt(), speedPxPerSec = 280f),
    DanmakuComment(3L, "前方高能预警 • 杜比全景声沉浸感拉满", 0xFFFFB300.toInt(), speedPxPerSec = 230f),
    DanmakuComment(4L, "智能换源 38ms 秒开不卡顿", 0xFF00E676.toInt(), speedPxPerSec = 265f),
    DanmakuComment(5L, "支持 TV 遥控器数字键直达选集 + 手势长按 2.0x 倍速", 0xFFFFFFFF.toInt(), speedPxPerSec = 240f)
)

@UnstableApi
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val vodRepository: VodRepository,
    private val configRepository: ConfigRepository,
    private val watchHistoryDao: WatchHistoryDao,
    private val settingsDataStore: SettingsDataStore,
    private val headlessWebSniffer: HeadlessWebSniffer,
    private val okHttpClient: OkHttpClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var _exoPlayer: ExoPlayer? = null
    val exoPlayer: ExoPlayer?
        get() = _exoPlayer

    private var progressPollingJob: Job? = null
    private var keypadClearJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val buffering = playbackState == Player.STATE_BUFFERING
            val duration = (_exoPlayer?.duration ?: 0L).coerceAtLeast(0L)
            _uiState.update {
                it.copy(
                    isBuffering = buffering,
                    durationMs = if (duration > 0L) duration else it.durationMs
                )
            }
            if (playbackState == Player.STATE_ENDED) {
                playNextEpisodeOrLoop()
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            extractAudioAndSubtitleTracks(tracks)
        }

        override fun onPlayerError(error: PlaybackException) {
            // Automatic Smart Source Failover (Requirement 4.3):
            // When a source fails or times out, automatically ping & switch to the fastest backup source
            // while preserving exact playback position.
            triggerAutomaticSourceFailover(reason = "当前线路缓冲异常，正在为您自动切换最快备用源...")
        }
    }

    fun initializePlayer(
        siteKey: String,
        vodId: String,
        initialSourceIndex: Int,
        initialEpisodeIndex: Int
    ) {
        if (_exoPlayer == null) {
            _exoPlayer = ExoPlayer.Builder(appContext).build().apply {
                addListener(playerListener)
                playWhenReady = true
            }
        }

        viewModelScope.launch {
            val prefs = settingsDataStore.preferencesFlow.first()
            val site = vodRepository.getPrimarySite(siteKey)
            val detail = vodRepository.loadDetailContent(site, vodId) ?: return@launch
            val sources = detail.parsePlaySources()
            val compositeId = "${site.key}_$vodId"
            val savedHistory = watchHistoryDao.getHistoryById(compositeId)

            val safeSourceIdx = initialSourceIndex.coerceIn(0, (sources.size - 1).coerceAtLeast(0))
            val safeEpIdx = initialEpisodeIndex.coerceIn(
                0,
                ((sources.getOrNull(safeSourceIdx)?.episodes?.size ?: 1) - 1).coerceAtLeast(0)
            )

            val resumePosition = if (savedHistory != null && savedHistory.episodeIndex == safeEpIdx) {
                savedHistory.positionMs
            } else {
                0L
            }

            _uiState.update {
                it.copy(
                    vod = detail,
                    playSources = sources,
                    activeSourceIndex = safeSourceIdx,
                    activeEpisodeIndex = safeEpIdx,
                    enableDanmaku = prefs.enableDanmaku,
                    enableHlsAdBlock = prefs.enableHlsAdBlock,
                    skipIntroMs = savedHistory?.skipIntroMs ?: prefs.defaultSkipIntroMs,
                    skipOutroMs = savedHistory?.skipOutroMs ?: prefs.defaultSkipOutroMs
                )
            }

            startEpisodePlayback(
                sourceIndex = safeSourceIdx,
                episodeIndex = safeEpIdx,
                seekToPositionMs = resumePosition
            )
            startProgressAndSkipPolling()
        }
    }

    fun startEpisodePlayback(
        sourceIndex: Int,
        episodeIndex: Int,
        seekToPositionMs: Long = 0L
    ) {
        val state = _uiState.value
        val vod = state.vod ?: return
        val source = state.playSources.getOrNull(sourceIndex) ?: return
        val episode = source.episodes.getOrNull(episodeIndex) ?: return
        val site = vodRepository.getPrimarySite(vod.siteKey)

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    activeSourceIndex = sourceIndex,
                    activeEpisodeIndex = episodeIndex,
                    isBuffering = true,
                    statusBannerMessage = "正在解析 [${source.flag}] ${episode.title}..."
                )
            }

            val resolved = vodRepository.resolvePlayerContent(site, source.flag, episode.url)
            var finalStreamUrl = resolved.url
            var finalHeaders = resolved.headers

            if (resolved.requiresSniffing) {
                _uiState.update {
                    it.copy(
                        isSniffingWebStream = true,
                        statusBannerMessage = "启动无头 WebView 嗅探真实媒体流 (.m3u8/.mp4)..."
                    )
                }
                val sniffTarget = if (resolved.playUrl.isNotBlank()) {
                    "${resolved.playUrl}${resolved.url}"
                } else {
                    resolved.url
                }
                val sniffed = headlessWebSniffer.sniffMediaStream(sniffTarget, resolved.headers)
                _uiState.update { it.copy(isSniffingWebStream = false) }
                if (sniffed != null) {
                    finalStreamUrl = sniffed.mediaUrl
                    finalHeaders = sniffed.headers
                }
            }

            playMediaStream(
                streamUrl = finalStreamUrl,
                headers = finalHeaders,
                startPositionMs = maxOf(seekToPositionMs, _uiState.value.skipIntroMs)
            )
        }
    }

    private fun playMediaStream(
        streamUrl: String,
        headers: Map<String, String>,
        startPositionMs: Long
    ) {
        val player = _exoPlayer ?: return
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient).apply {
            if (headers.isNotEmpty()) {
                setDefaultRequestProperties(headers)
            }
        }

        val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
        val isHls = streamUrl.contains(".m3u8", ignoreCase = true)

        val mediaSource = if (isHls) {
            val adBlockFactory = AdBlockHlsPlaylistParserFactory(
                enabledProvider = { _uiState.value.enableHlsAdBlock },
                blacklistRulesProvider = { configRepository.activeConfigState.value.ads },
                onAdSegmentsFiltered = { count ->
                    _uiState.update {
                        it.copy(
                            filteredAdSegmentsCount = it.filteredAdSegmentsCount + count,
                            statusBannerMessage = "HLS 切片过滤器已内存级剔除 $count 个插播广告切片"
                        )
                    }
                }
            )
            HlsMediaSource.Factory(dataSourceFactory)
                .setPlaylistParserFactory(adBlockFactory)
                .createMediaSource(mediaItem)
        } else {
            DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem)
        }

        player.setMediaSource(mediaSource)
        player.prepare()
        if (startPositionMs > 0L) {
            player.seekTo(startPositionMs)
        }
        player.playWhenReady = true
        _uiState.update { it.copy(statusBannerMessage = "") }
    }

    /**
     * Concurrently pings remaining sources and seamlessly switches to the fastest backup source
     * while preserving exact playback progress (Requirement 4.3).
     */
    fun triggerAutomaticSourceFailover(reason: String = "正在智能测速并切换最快线路...") {
        val currentSources = _uiState.value.playSources
        if (currentSources.size <= 1) return

        val savedPos = _exoPlayer?.currentPosition ?: _uiState.value.currentPositionMs
        val currentEpIdx = _uiState.value.activeEpisodeIndex
        val failedFlag = _uiState.value.currentSource?.flag

        viewModelScope.launch {
            _uiState.update { it.copy(statusBannerMessage = reason) }
            val ranked = vodRepository.pingAndRankPlaySources(currentSources)
            val bestBackupIdx = ranked.indexOfFirst { it.flag != failedFlag }.coerceAtLeast(0)
            _uiState.update { it.copy(playSources = ranked) }
            startEpisodePlayback(
                sourceIndex = bestBackupIdx,
                episodeIndex = currentEpIdx,
                seekToPositionMs = savedPos
            )
        }
    }

    private fun startProgressAndSkipPolling() {
        progressPollingJob?.cancel()
        progressPollingJob = viewModelScope.launch {
            while (isActive) {
                val player = _exoPlayer
                if (player != null && player.isPlaying) {
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.coerceAtLeast(0L)
                    _uiState.update { it.copy(currentPositionMs = pos, durationMs = dur) }

                    // Auto skip outro if configured
                    val outroSkip = _uiState.value.skipOutroMs
                    if (outroSkip > 0L && dur > outroSkip + 15_000L && pos >= dur - outroSkip) {
                        playNextEpisodeOrLoop()
                    }

                    saveWatchHistorySnapshot(pos, dur)
                }
                delay(1_000L)
            }
        }
    }

    private suspend fun saveWatchHistorySnapshot(positionMs: Long, durationMs: Long) {
        val state = _uiState.value
        val vod = state.vod ?: return
        val source = state.currentSource ?: return
        val ep = state.currentEpisode ?: return

        watchHistoryDao.upsertHistory(
            WatchHistoryEntity(
                compositeId = "${vod.siteKey}_${vod.vodId}",
                siteKey = vod.siteKey,
                siteName = vod.siteName,
                vodId = vod.vodId,
                vodName = vod.vodName,
                vodPic = vod.vodPic,
                sourceFlag = source.flag,
                episodeTitle = ep.title,
                episodeUrl = ep.url,
                episodeIndex = state.activeEpisodeIndex,
                positionMs = positionMs,
                durationMs = durationMs,
                skipIntroMs = state.skipIntroMs,
                skipOutroMs = state.skipOutroMs
            )
        )
    }

    fun togglePlayPause() {
        val player = _exoPlayer ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekToPosition(positionMs: Long) {
        val player = _exoPlayer ?: return
        val safePos = positionMs.coerceIn(0L, (player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
        player.seekTo(safePos)
        _uiState.update { it.copy(currentPositionMs = safePos) }
    }

    fun seekByDelta(deltaMs: Long) {
        val current = _exoPlayer?.currentPosition ?: _uiState.value.currentPositionMs
        seekToPosition(current + deltaMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 3.0f)
        _exoPlayer?.setPlaybackSpeed(clamped)
        _uiState.update { it.copy(playbackSpeed = clamped) }
    }

    /**
     * Long-press 2.0x boost gesture handler (Requirement 3.2).
     */
    fun setLongPress2xBoost(active: Boolean) {
        val player = _exoPlayer ?: return
        if (active) {
            player.setPlaybackSpeed(2.0f)
            _uiState.update { it.copy(isLongPress2xActive = true) }
        } else {
            player.setPlaybackSpeed(_uiState.value.playbackSpeed)
            _uiState.update { it.copy(isLongPress2xActive = false) }
        }
    }

    fun cycleAspectMode() {
        val modes = VideoAspectMode.entries
        val next = modes[(_uiState.value.aspectMode.ordinal + 1) % modes.size]
        _uiState.update { it.copy(aspectMode = next) }
    }

    fun cycleKernelType() {
        val kernels = PlayerKernelType.entries
        val next = kernels[(_uiState.value.kernelType.ordinal + 1) % kernels.size]
        _uiState.update {
            it.copy(
                kernelType = next,
                statusBannerMessage = "已切换渲染内核配置: ${next.displayName}"
            )
        }
    }

    fun toggleDanmaku() {
        _uiState.update { it.copy(enableDanmaku = !it.enableDanmaku) }
    }

    fun markCurrentAsSkipIntro() {
        val current = _uiState.value.currentPositionMs.coerceIn(0L, 600_000L)
        _uiState.update {
            it.copy(
                skipIntroMs = current,
                statusBannerMessage = "已记忆跳过片头时间: ${ current / 1000 } 秒"
            )
        }
    }

    fun markCurrentAsSkipOutro() {
        val dur = _uiState.value.durationMs
        val pos = _uiState.value.currentPositionMs
        val remain = (dur - pos).coerceIn(0L, 600_000L)
        _uiState.update {
            it.copy(
                skipOutroMs = remain,
                statusBannerMessage = "已记忆跳过片尾时长: ${ remain / 1000 } 秒"
            )
        }
    }

    fun playNextEpisodeOrLoop() {
        val state = _uiState.value
        val total = state.currentSource?.episodes?.size ?: 0
        if (state.activeEpisodeIndex + 1 < total) {
            startEpisodePlayback(state.activeSourceIndex, state.activeEpisodeIndex + 1)
        }
    }

    /**
     * Handles Android TV remote numeric keypad (`0-9`) to jump directly to an episode number (Requirement 4.5).
     */
    fun onTvNumericDigitPressed(digit: Int) {
        val updatedBuffer = (_uiState.value.tvKeypadBuffer + digit.toString()).takeLast(3)
        _uiState.update {
            it.copy(
                tvKeypadBuffer = updatedBuffer,
                statusBannerMessage = "数字键选集: 第 $updatedBuffer 集 (1.5秒后自动跳转)"
            )
        }
        keypadClearJob?.cancel()
        keypadClearJob = viewModelScope.launch {
            delay(1_500L)
            val epNumber = _uiState.value.tvKeypadBuffer.toIntOrNull() ?: 0
            _uiState.update { it.copy(tvKeypadBuffer = "", statusBannerMessage = "") }
            val total = _uiState.value.currentSource?.episodes?.size ?: 0
            if (epNumber in 1..total) {
                startEpisodePlayback(_uiState.value.activeSourceIndex, epNumber - 1)
            }
        }
    }

    private fun extractAudioAndSubtitleTracks(tracks: Tracks) {
        val audios = mutableListOf<TrackItemInfo>()
        val subtitles = mutableListOf<TrackItemInfo>()

        tracks.groups.forEachIndexed { groupIdx, group ->
            val trackType = group.type
            for (trackIdx in 0 until group.length) {
                val format = group.getTrackFormat(trackIdx)
                val label = format.label
                    ?: format.language
                    ?: "轨道 #${trackIdx + 1}"
                val selected = group.isTrackSelected(trackIdx)
                if (trackType == C.TRACK_TYPE_AUDIO) {
                    audios.add(TrackItemInfo(groupIdx, trackIdx, label, selected))
                } else if (trackType == C.TRACK_TYPE_TEXT) {
                    subtitles.add(TrackItemInfo(groupIdx, trackIdx, label, selected))
                }
            }
        }
        _uiState.update { it.copy(audioTracks = audios, subtitleTracks = subtitles) }
    }

    fun selectTrack(item: TrackItemInfo) {
        val player = _exoPlayer ?: return
        val group = player.currentTracks.groups.getOrNull(item.groupIndex) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, item.trackIndex))
            .build()
    }

    /**
     * Strict lifecycle cleanup (Coding Rule #2):
     * Stops polling, releases HeadlessWebSniffer WebView, and releases ExoPlayer.
     */
    fun releasePlayerResources() {
        progressPollingJob?.cancel()
        keypadClearJob?.cancel()
        headlessWebSniffer.releaseActiveSniffer()
        _exoPlayer?.removeListener(playerListener)
        _exoPlayer?.release()
        _exoPlayer = null
    }

    override fun onCleared() {
        releasePlayerResources()
        super.onCleared()
    }
}
