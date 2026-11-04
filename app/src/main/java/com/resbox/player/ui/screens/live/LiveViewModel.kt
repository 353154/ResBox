package com.resbox.player.ui.screens.live

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.resbox.player.data.model.LiveChannelGroup
import com.resbox.player.data.model.LiveChannelItem
import com.resbox.player.data.repository.EpgProgramItem
import com.resbox.player.data.repository.LiveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class LiveUiState(
    val isLoading: Boolean = true,
    val groups: List<LiveChannelGroup> = emptyList(),
    val selectedGroupIndex: Int = 0,
    val selectedChannel: LiveChannelItem? = null,
    val selectedLineIndex: Int = 0,
    val epgPrograms: List<EpgProgramItem> = emptyList(),
    val isCatchupMode: Boolean = false,
    val activeCatchupTitle: String = ""
) {
    val activeGroupChannels: List<LiveChannelItem>
        get() = groups.getOrNull(selectedGroupIndex)?.channels.orEmpty()
}

@UnstableApi
@HiltViewModel
class LiveViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val liveRepository: LiveRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveUiState())
    val uiState: StateFlow<LiveUiState> = _uiState.asStateFlow()

    private var _liveExoPlayer: ExoPlayer? = null
    val liveExoPlayer: ExoPlayer?
        get() = _liveExoPlayer

    fun initializeLiveTv() {
        if (_liveExoPlayer == null) {
            _liveExoPlayer = ExoPlayer.Builder(appContext).build().apply {
                playWhenReady = true
            }
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val loadedGroups = liveRepository.loadChannelGroups()
            val firstChannel = loadedGroups.firstOrNull()?.channels?.firstOrNull()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    groups = loadedGroups,
                    selectedGroupIndex = 0,
                    selectedChannel = firstChannel
                )
            }
            if (firstChannel != null) {
                selectChannel(firstChannel)
            }
        }
    }

    fun selectGroup(index: Int) {
        _uiState.update { it.copy(selectedGroupIndex = index) }
    }

    fun selectChannel(channel: LiveChannelItem, lineIndex: Int = 0) {
        val streamUrl = channel.urls.getOrNull(lineIndex) ?: channel.urls.firstOrNull().orEmpty()
        _uiState.update {
            it.copy(
                selectedChannel = channel,
                selectedLineIndex = lineIndex,
                isCatchupMode = false,
                activeCatchupTitle = ""
            )
        }
        if (streamUrl.isNotBlank()) {
            _liveExoPlayer?.apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)))
                prepare()
                playWhenReady = true
            }
        }
        viewModelScope.launch {
            val epg = liveRepository.loadEpgForChannel(channel)
            _uiState.update { it.copy(epgPrograms = epg) }
        }
    }

    /**
     * Triggers Catch-up (time-shift replay) playback for a selected EPG program (Requirement 4.5).
     */
    fun playCatchupProgram(program: EpgProgramItem) {
        val ch = _uiState.value.selectedChannel ?: return
        val catchupUrl = liveRepository.buildCatchupUrl(ch, program)
        _uiState.update {
            it.copy(
                isCatchupMode = !program.isLiveNow,
                activeCatchupTitle = program.title
            )
        }
        _liveExoPlayer?.apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(catchupUrl)))
            prepare()
            playWhenReady = true
        }
    }

    fun releaseLivePlayer() {
        _liveExoPlayer?.release()
        _liveExoPlayer = null
    }

    override fun onCleared() {
        releaseLivePlayer()
        super.onCleared()
    }
}
