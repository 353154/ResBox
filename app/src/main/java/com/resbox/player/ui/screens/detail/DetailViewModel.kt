package com.resbox.player.ui.screens.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resbox.player.data.local.dao.FavoriteVodDao
import com.resbox.player.data.local.dao.WatchHistoryDao
import com.resbox.player.data.local.entity.FavoriteVodEntity
import com.resbox.player.data.local.entity.WatchHistoryEntity
import com.resbox.player.data.model.VodEpisode
import com.resbox.player.data.model.VodInfo
import com.resbox.player.data.model.VodPlaySource
import com.resbox.player.data.repository.VodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class DetailUiState(
    val isLoading: Boolean = true,
    val isSpeedTesting: Boolean = false,
    val vod: VodInfo? = null,
    val playSources: List<VodPlaySource> = emptyList(),
    val selectedSourceIndex: Int = 0,
    val selectedEpisodeIndex: Int = 0,
    val selectedChunkIndex: Int = 0,
    val isAscendingOrder: Boolean = true,
    val isFavorite: Boolean = false,
    val savedHistory: WatchHistoryEntity? = null
) {
    val activeSource: VodPlaySource?
        get() = playSources.getOrNull(selectedSourceIndex)

    val episodeChunks: List<IntRange>
        get() {
            val total = activeSource?.episodes?.size ?: 0
            if (total == 0) return emptyList()
            val chunkSize = 50
            return (0 until total step chunkSize).map { start ->
                start until minOf(start + chunkSize, total)
            }
        }

    val displayedEpisodes: List<VodEpisode>
        get() {
            val all = activeSource?.episodes.orEmpty()
            if (all.isEmpty()) return emptyList()
            val range = episodeChunks.getOrNull(selectedChunkIndex) ?: all.indices
            val sliced = all.slice(range)
            return if (isAscendingOrder) sliced else sliced.reversed()
        }
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val vodRepository: VodRepository,
    private val watchHistoryDao: WatchHistoryDao,
    private val favoriteVodDao: FavoriteVodDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun loadVodDetail(siteKey: String, vodId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val site = vodRepository.getPrimarySite(siteKey)
            val detail = vodRepository.loadDetailContent(site, vodId)
            val parsedSources = detail?.parsePlaySources().orEmpty()
            val compositeId = "${site.key}_$vodId"
            val history = watchHistoryDao.getHistoryById(compositeId)

            val initialSourceIdx = if (history != null) {
                parsedSources.indexOfFirst { it.flag == history.sourceFlag }.coerceAtLeast(0)
            } else 0
            val initialEpIdx = history?.episodeIndex ?: 0

            _uiState.update {
                it.copy(
                    isLoading = false,
                    vod = detail,
                    playSources = parsedSources,
                    selectedSourceIndex = initialSourceIdx,
                    selectedEpisodeIndex = initialEpIdx,
                    selectedChunkIndex = initialEpIdx / 50,
                    savedHistory = history
                )
            }

            // Observe favorite status
            launch {
                favoriteVodDao.observeIsFavorite(compositeId).collectLatest { fav ->
                    _uiState.update { it.copy(isFavorite = fav) }
                }
            }

            // Concurrently ping all PlayFrom sources in the background to display real latency badges
            if (parsedSources.isNotEmpty()) {
                runConcurrentSourceSpeedCheck()
            }
        }
    }

    /**
     * Concurrently pings all `PlayFrom` sources and ranks them by real HTTP latency (Requirement 4.3).
     */
    fun runConcurrentSourceSpeedCheck(autoSelectFastest: Boolean = false) {
        val currentSources = _uiState.value.playSources
        if (currentSources.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSpeedTesting = true) }
            val ranked = vodRepository.pingAndRankPlaySources(currentSources)
            _uiState.update { state ->
                val activeFlag = state.activeSource?.flag
                val newIndex = if (autoSelectFastest) {
                    0
                } else {
                    ranked.indexOfFirst { it.flag == activeFlag }.coerceAtLeast(0)
                }
                state.copy(
                    isSpeedTesting = false,
                    playSources = ranked,
                    selectedSourceIndex = newIndex
                )
            }
        }
    }

    fun selectSourceIndex(index: Int) {
        _uiState.update {
            it.copy(
                selectedSourceIndex = index,
                selectedChunkIndex = 0
            )
        }
    }

    fun selectChunkIndex(chunkIdx: Int) {
        _uiState.update { it.copy(selectedChunkIndex = chunkIdx) }
    }

    fun toggleEpisodeOrder() {
        _uiState.update { it.copy(isAscendingOrder = !it.isAscendingOrder) }
    }

    fun toggleFavorite() {
        val vod = _uiState.value.vod ?: return
        val compositeId = "${vod.siteKey}_${vod.vodId}"
        viewModelScope.launch {
            if (_uiState.value.isFavorite) {
                favoriteVodDao.removeFavorite(compositeId)
            } else {
                favoriteVodDao.insertFavorite(
                    FavoriteVodEntity(
                        compositeId = compositeId,
                        siteKey = vod.siteKey,
                        siteName = vod.siteName,
                        vodId = vod.vodId,
                        vodName = vod.vodName,
                        vodPic = vod.vodPic,
                        vodRemarks = vod.vodRemarks,
                        vodScore = vod.vodScore
                    )
                )
            }
        }
    }
}
