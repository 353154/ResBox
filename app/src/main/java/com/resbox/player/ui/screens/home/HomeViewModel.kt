package com.resbox.player.ui.screens.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resbox.player.data.local.dao.WatchHistoryDao
import com.resbox.player.data.local.datastore.SettingsDataStore
import com.resbox.player.data.local.entity.WatchHistoryEntity
import com.resbox.player.data.model.CategoryBean
import com.resbox.player.data.model.SiteBean
import com.resbox.player.data.model.VodInfo
import com.resbox.player.data.repository.ConfigRepository
import com.resbox.player.data.repository.VodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class HomeUiState(
    val isLoading: Boolean = true,
    val availableSites: List<SiteBean> = emptyList(),
    val currentSite: SiteBean = SiteBean(),
    val categories: List<CategoryBean> = emptyList(),
    val selectedCategoryId: String = "all",
    val heroBannerVod: VodInfo? = null,
    val vodItems: List<VodInfo> = emptyList(),
    val focusedPosterUrl: String = ""
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val vodRepository: VodRepository,
    private val settingsDataStore: SettingsDataStore,
    watchHistoryDao: WatchHistoryDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val recentHistory: StateFlow<List<WatchHistoryEntity>> = watchHistoryDao
        .observeRecentHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            configRepository.initializeConfig()
            configRepository.activeConfigState.collectLatest { config ->
                val prefs = settingsDataStore.preferencesFlow.first()
                val activeSite = vodRepository.getPrimarySite(prefs.activeHomeSiteKey)
                _uiState.update {
                    it.copy(
                        availableSites = config.sites,
                        currentSite = activeSite
                    )
                }
                loadSiteHome(activeSite)
            }
        }
    }

    fun switchHomeSite(site: SiteBean) {
        viewModelScope.launch {
            settingsDataStore.updateActiveHomeSiteKey(site.key)
            _uiState.update { it.copy(currentSite = site, selectedCategoryId = "all") }
            loadSiteHome(site)
        }
    }

    fun selectCategory(typeId: String) {
        val currentSite = _uiState.value.currentSite
        _uiState.update { it.copy(selectedCategoryId = typeId, isLoading = true) }
        viewModelScope.launch {
            val payload = if (typeId == "all") {
                vodRepository.loadHomeContent(currentSite)
            } else {
                vodRepository.loadCategoryContent(currentSite, typeId, page = 1)
            }
            val firstPoster = payload.list.firstOrNull()?.vodPic.orEmpty()
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    vodItems = payload.list,
                    heroBannerVod = payload.list.firstOrNull() ?: state.heroBannerVod,
                    focusedPosterUrl = firstPoster.ifBlank { state.focusedPosterUrl }
                )
            }
        }
    }

    fun onFocusedPosterChanged(posterUrl: String) {
        if (posterUrl.isNotBlank()) {
            _uiState.update { it.copy(focusedPosterUrl = posterUrl) }
        }
    }

    fun refresh() {
        loadSiteHome(_uiState.value.currentSite)
    }

    private fun loadSiteHome(site: SiteBean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val payload = vodRepository.loadHomeContent(site)
            val allCats = buildList {
                if (payload.categories.none { it.typeId == "all" }) {
                    add(CategoryBean(typeId = "all", typeName = "精选推荐"))
                }
                addAll(payload.categories)
            }
            val hero = payload.list.firstOrNull()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    categories = allCats,
                    selectedCategoryId = "all",
                    heroBannerVod = hero,
                    vodItems = payload.list,
                    focusedPosterUrl = hero?.displayPoster.orEmpty()
                )
            }

            // Asynchronously enrich top showcase items with TMDB high-res posters in background
            launch {
                val currentItems = payload.list.toMutableList()
                for (item in payload.list.take(8)) {
                    val enriched = vodRepository.enrichVodWithScraper(item)
                    val idx = currentItems.indexOfFirst { it.vodId == item.vodId }
                    if (idx >= 0) {
                        currentItems[idx] = enriched
                        _uiState.update { state ->
                            state.copy(
                                vodItems = currentItems.toList(),
                                heroBannerVod = if (state.heroBannerVod?.vodId == enriched.vodId) enriched else state.heroBannerVod
                            )
                        }
                    }
                }
            }
        }
    }
}
