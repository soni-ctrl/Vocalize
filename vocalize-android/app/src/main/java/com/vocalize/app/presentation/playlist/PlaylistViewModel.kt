package com.vocalize.app.presentation.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vocalize.app.data.local.entity.MemoEntity
import com.vocalize.app.data.local.entity.PlaylistEntity
import com.vocalize.app.data.local.entity.PlaylistMemoCrossRef
import com.vocalize.app.data.repository.MemoRepository
import com.vocalize.app.util.AudioFileManager
import com.vocalize.app.util.AudioPlayerManager
import com.vocalize.app.util.ReminderAlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistUiState(
    val playlist: PlaylistEntity? = null,
    val memos: List<MemoEntity> = emptyList(),
    val currentPlayingMemoId: String? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = true,
    val currentPlaybackIndex: Int = -1
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val memoRepository: MemoRepository,
    private val audioPlayerManager: AudioPlayerManager,
    private val audioFileManager: AudioFileManager,
    private val alarmScheduler: ReminderAlarmScheduler
) : ViewModel() {

    private val playlistId: String = savedStateHandle.get<String>("playlistId")!!

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    init {
        loadData()
        collectPlaybackState()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                memoRepository.getAllPlaylists().map { it.find { p -> p.id == playlistId } },
                memoRepository.getMemosByPlaylist(playlistId)
            ) { playlist, memos -> Pair(playlist, memos) }
                .collect { (playlist, memos) ->
                    _uiState.update { it.copy(playlist = playlist, memos = memos, isLoading = false) }
                }
        }
    }

    private fun collectPlaybackState() {
        viewModelScope.launch {
            audioPlayerManager.playbackState.collect { state ->
                _uiState.update {
                    it.copy(
                        currentPlayingMemoId = state.currentMemoId,
                        isPlaying = state.isPlaying
                    )
                }
            }
        }
    }

    val allMemos: StateFlow<List<MemoEntity>> = memoRepository.getAllMemos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addMemosToPlaylist(memoIds: List<String>) {
        viewModelScope.launch {
            memoIds.forEach { memoId ->
                memoRepository.addMemoToPlaylist(PlaylistMemoCrossRef(playlistId, memoId))
            }
        }
    }

    fun playMemo(memo: MemoEntity) {
        val idx = _uiState.value.memos.indexOf(memo)
        audioPlayerManager.prepareAndPlay(memo.filePath, memo.id)
        _uiState.update { it.copy(currentPlaybackIndex = idx) }
    }

    fun playAll() {
        val memos = _uiState.value.memos
        if (memos.isEmpty()) return
        audioPlayerManager.prepareAndPlay(memos[0].filePath, memos[0].id)
        _uiState.update { it.copy(currentPlaybackIndex = 0) }
        // Auto-advance to next track with crossfade when current track completes
        audioPlayerManager.onTrackCompleted = { playNext() }
    }

    fun togglePlayPause() {
        audioPlayerManager.togglePlayPause()
    }

    fun playNext() {
        val state = _uiState.value
        val nextIdx = state.currentPlaybackIndex + 1
        if (nextIdx < state.memos.size) {
            val memo = state.memos[nextIdx]
            // Use crossfade for smooth transition between playlist items
            audioPlayerManager.prepareAndPlayWithCrossfade(memo.filePath, memo.id)
            _uiState.update { it.copy(currentPlaybackIndex = nextIdx) }
        } else {
            // End of playlist — stop
            audioPlayerManager.onTrackCompleted = null
        }
    }

    fun playPrevious() {
        val state = _uiState.value
        val prevIdx = state.currentPlaybackIndex - 1
        if (prevIdx >= 0) {
            val memo = state.memos[prevIdx]
            audioPlayerManager.prepareAndPlayWithCrossfade(memo.filePath, memo.id)
            _uiState.update { it.copy(currentPlaybackIndex = prevIdx) }
        }
    }

    fun removeMemoFromPlaylist(memoId: String) {
        viewModelScope.launch {
            memoRepository.removeMemoFromPlaylist(playlistId, memoId)
        }
    }

    fun deleteMemo(memo: MemoEntity) {
        viewModelScope.launch {
            if (memo.hasReminder) alarmScheduler.cancelReminder(memo.id)
            audioFileManager.deleteAudioFile(memo.filePath)
            memoRepository.deleteMemo(memo)
        }
    }

    fun renamePlaylist(name: String) {
        viewModelScope.launch {
            val current = _uiState.value.playlist ?: return@launch
            memoRepository.updatePlaylist(current.copy(name = name))
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayerManager.release()
    }
}
