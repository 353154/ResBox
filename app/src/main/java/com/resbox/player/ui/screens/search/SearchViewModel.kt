package com.resbox.player.ui.screens.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resbox.player.data.local.datastore.SettingsDataStore
import com.resbox.player.data.model.VodInfo
import com.resbox.player.data.repository.VodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class SiteSearchBadge(
    val siteKey: String,
    val siteName: String,
    val resultCount: Int,
    val latencyMs: Long
)

@Immutable
data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val completedSites: Int = 0,
    val totalSites: Int = 0,
    val selectedSiteFilterKey: String = "all",
    val siteBadges: List<SiteSearchBadge> = emptyList(),
    val allResults: List<VodInfo> = emptyList()
) {
    val displayedResults: List<VodInfo>
        get() = if (selectedSiteFilterKey == "all") {
            allResults
        } else {
            allResults.filter { it.siteKey == selectedSiteFilterKey }
        }
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val vodRepository: VodRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var activeSearchJob: Job? = null

    val hotKeywords = listOf("沙丘2", "漫长的季节", "奥本海默", "星际穿越", "繁花", "灵笼", "脉动地球")

    fun onQueryChanged(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
    }

    /**
     * Launches the concurrent `channelFlow` + `Semaphore` search pool and streams each site's
     * results directly into `_uiState` as soon as that site responds (Requirement 4.3).
     */
    fun triggerConcurrentSearch(keywordOverride: String? = null) {
        val targetKeyword = (keywordOverride ?: _uiState.value.query).trim()
        if (targetKeyword.isEmpty()) return

        activeSearchJob?.cancel()
        _uiState.update {
            it.copy(
                query = targetKeyword,
                isSearching = true,
                completedSites = 0,
                totalSites = 0,
                selectedSiteFilterKey = "all",
                siteBadges = emptyList(),
                allResults = emptyList()
            )
        }

        activeSearchJob = viewModelScope.launch {
            val concurrency = settingsDataStore.preferencesFlow.first().concurrentSearchLimit
            vodRepository.streamConcurrentSearch(targetKeyword, concurrency).collect { batch ->
                _uiState.update { state ->
                    val updatedBadges = if (batch.items.isNotEmpty()) {
                        state.siteBadges + SiteSearchBadge(
                            siteKey = batch.site.key,
                            siteName = batch.site.name,
                            resultCount = batch.items.size,
                            latencyMs = batch.elapsedMs
                        )
                    } else {
                        state.siteBadges
                    }
                    state.copy(
                        completedSites = batch.completedCount,
                        totalSites = batch.totalSitesCount,
                        isSearching = batch.completedCount < batch.totalSitesCount,
                        siteBadges = updatedBadges.sortedBy { it.latencyMs },
                        allResults = state.allResults + batch.items
                    )
                }
            }
            _uiState.update { it.copy(isSearching = false) }
        }
    }

    fun selectSiteFilter(siteKey: String) {
        _uiState.update { it.copy(selectedSiteFilterKey = siteKey) }
    }
}
