package com.resbox.player.ui.screens.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resbox.player.data.local.datastore.AppPreferences
import com.resbox.player.data.local.datastore.SettingsDataStore
import com.resbox.player.data.local.entity.RepoSourceEntity
import com.resbox.player.data.repository.ConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class SettingsUiState(
    val inputRepoUrl: String = "",
    val inputRepoName: String = "",
    val isSyncingRepo: Boolean = false,
    val statusMessage: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val preferences: StateFlow<AppPreferences> = settingsDataStore.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences())

    val savedRepos: StateFlow<List<RepoSourceEntity>> = configRepository.savedReposFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onRepoUrlChanged(url: String) {
        _uiState.update { it.copy(inputRepoUrl = url) }
    }

    fun onRepoNameChanged(name: String) {
        _uiState.update { it.copy(inputRepoName = name) }
    }

    fun addAndActivateRepo() {
        val url = _uiState.value.inputRepoUrl.trim()
        if (url.isEmpty()) return
        val name = _uiState.value.inputRepoName.trim().ifBlank { "自定义 TVBox 仓" }

        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingRepo = true, statusMessage = "正在解密并解析配置 (支持 Base64 / AES-2423 / 图片隐写)...") }
            val result = configRepository.loadAndActivateConfigUrl(url, name)
            val sitesCount = result.getOrNull()?.sites?.size ?: 0
            _uiState.update {
                it.copy(
                    isSyncingRepo = false,
                    inputRepoUrl = "",
                    inputRepoName = "",
                    statusMessage = "成功加载仓库 [$name]，共解析 $sitesCount 个影视站源"
                )
            }
        }
    }

    fun activateExistingRepo(repo: RepoSourceEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingRepo = true, statusMessage = "正在切换至 ${repo.name}...") }
            val result = configRepository.loadAndActivateConfigUrl(repo.url, repo.name)
            val sitesCount = result.getOrNull()?.sites?.size ?: 0
            _uiState.update {
                it.copy(
                    isSyncingRepo = false,
                    statusMessage = "已激活 [${repo.name}] ($sitesCount 个站源)"
                )
            }
        }
    }

    fun setEnableDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setEnableDynamicColor(enabled) }
    }

    fun setEnableHlsAdBlock(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setEnableHlsAdBlock(enabled) }
    }

    fun setEnableDanmaku(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setEnableDanmaku(enabled) }
    }

    fun setForceTvMode(forceTv: Boolean) {
        viewModelScope.launch { settingsDataStore.setForceTvMode(forceTv) }
    }

    fun setEnablePosterScraping(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setEnablePosterScraping(enabled) }
    }

    fun setTmdbApiKey(apiKey: String) {
        viewModelScope.launch { settingsDataStore.setTmdbApiKey(apiKey) }
    }
}
