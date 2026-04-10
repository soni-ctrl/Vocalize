package com.vocalize.app.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vocalize.app.data.local.entity.CategoryEntity
import com.vocalize.app.data.local.entity.MemoEntity
import com.vocalize.app.data.local.entity.PlaylistEntity
import com.vocalize.app.data.local.entity.PlaylistMemoCrossRef
import com.vocalize.app.data.local.entity.ReminderEntity
import com.vocalize.app.data.local.entity.RepeatType
import com.vocalize.app.data.local.entity.TagEntity
import com.vocalize.app.data.repository.MemoRepository
import com.vocalize.app.util.AudioPlayerManager
import com.vocalize.app.util.AudioPlaybackState
import com.vocalize.app.util.ReminderAlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class DetailUiState(
    val memo: MemoEntity? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val memoTags: List<TagEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val playbackState: AudioPlaybackState = AudioPlaybackState(),
    val isLoading: Boolean = true,
    val isEditingTitle: Boolean = false,
    val editedTitle: String = "",
    val isEditingNote: Boolean = false,
    val editedNote: String = "",
    val showReminderSheet: Boolean = false,
    val editingReminderId: String? = null,
    val showPlaylistSheet: Boolean = false,
    val showCategorySheet: Boolean = false,
    val showTagSheet: Boolean = false
)

@HiltViewModel
class MemoDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val memoRepository: MemoRepository,
    private val audioPlayerManager: AudioPlayerManager,
    private val alarmScheduler: ReminderAlarmScheduler
) : ViewModel() {

    private val memoId: String = savedStateHandle.get<String>("memoId")!!

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private var positionJob: Job? = null

    init {
        loadData()
        collectPlaybackState()
        startPositionUpdates()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                memoRepository.getMemoByIdFlow(memoId),
                memoRepository.getAllCategories(),
                memoRepository.getAllPlaylists(),
                memoRepository.getAllTags(),
                memoRepository.getTagsForMemo(memoId),
                memoRepository.getRemindersForMemo(memoId)
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val memo = values[0] as MemoEntity?
                val categories = values[1] as List<CategoryEntity>
                val playlists = values[2] as List<PlaylistEntity>
                val tags = values[3] as List<TagEntity>
                val memoTags = values[4] as List<TagEntity>
                val reminders = values[5] as List<ReminderEntity>
                
                DetailUiState(
                    memo = memo,
                    categories = categories,
                    playlists = playlists,
                    tags = tags,
                    memoTags = memoTags,
                    reminders = reminders,
                    isLoading = false,
                    editedTitle = memo?.title ?: "",
                    editedNote = memo?.textNote ?: ""
                )
            }.collect { _uiState.value = it.copy(
                playbackState = _uiState.value.playbackState,
                isEditingTitle = _uiState.value.isEditingTitle,
                isEditingNote = _uiState.value.isEditingNote,
                showReminderSheet = _uiState.value.showReminderSheet,
                showPlaylistSheet = _uiState.value.showPlaylistSheet,
                showCategorySheet = _uiState.value.showCategorySheet,
                showTagSheet = _uiState.value.showTagSheet,
                editingReminderId = _uiState.value.editingReminderId
            ) }
        }
    }

    private fun collectPlaybackState() {
        viewModelScope.launch {
            audioPlayerManager.playbackState.collect { state ->
                _uiState.update { it.copy(playbackState = state) }
            }
        }
    }

    private fun startPositionUpdates() {
        positionJob = viewModelScope.launch {
            while (isActive) {
                if (audioPlayerManager.isPlaying()) {
                    audioPlayerManager.updatePosition()
                }
                delay(300)
            }
        }
    }

    fun playPause() {
        val memo = _uiState.value.memo ?: return
        if (_uiState.value.playbackState.currentMemoId == memo.id && _uiState.value.playbackState.isPlaying) {
            audioPlayerManager.togglePlayPause()
        } else if (_uiState.value.playbackState.currentMemoId == memo.id) {
            audioPlayerManager.togglePlayPause()
        } else {
            audioPlayerManager.prepareAndPlay(memo.filePath, memo.id)
        }
    }

    fun seekTo(position: Int) = audioPlayerManager.seekTo(position)

    fun setSpeed(speed: Float) = audioPlayerManager.setSpeed(speed)

    fun stopPlayback() = audioPlayerManager.release()

    fun startEditTitle() = _uiState.update { it.copy(isEditingTitle = true, editedTitle = it.memo?.title ?: "") }
    fun updateEditedTitle(t: String) = _uiState.update { it.copy(editedTitle = t) }
    fun saveTitle() {
        val title = _uiState.value.editedTitle
        viewModelScope.launch {
            memoRepository.updateTitle(memoId, title, System.currentTimeMillis())
            _uiState.update { it.copy(isEditingTitle = false) }
        }
    }
    fun cancelEditTitle() = _uiState.update { it.copy(isEditingTitle = false) }

    fun startEditNote() = _uiState.update { it.copy(isEditingNote = true, editedNote = it.memo?.textNote ?: "") }
    fun updateEditedNote(n: String) = _uiState.update { it.copy(editedNote = n) }
    fun saveNote() {
        val note = _uiState.value.editedNote
        viewModelScope.launch {
            memoRepository.updateNote(memoId, note, System.currentTimeMillis())
            _uiState.update { it.copy(isEditingNote = false) }
        }
    }
    fun cancelEditNote() = _uiState.update { it.copy(isEditingNote = false) }

    private suspend fun refreshMemoReminderFields() {
        val reminders = memoRepository.getRemindersForMemo(memoId).first()
        if (reminders.isEmpty()) {
            memoRepository.updateReminder(memoId, false, null, RepeatType.NONE, "")
        } else {
            val nextReminder = reminders.minByOrNull { it.reminderTime }!!
            memoRepository.updateReminder(memoId, true, nextReminder.reminderTime, nextReminder.repeatType, nextReminder.customDays)
        }
    }

    fun setReminder(reminderTime: Long, repeatType: RepeatType, customDays: String, reminderId: String? = null) {
        viewModelScope.launch {
            val id = reminderId ?: UUID.randomUUID().toString()
            val reminder = ReminderEntity(
                id = id,
                memoId = memoId,
                reminderTime = reminderTime,
                repeatType = repeatType,
                customDays = customDays
            )
            memoRepository.insertReminder(reminder)
            refreshMemoReminderFields()
            _uiState.value.memo?.let { alarmScheduler.scheduleReminder(it) }
            _uiState.update { it.copy(showReminderSheet = false, editingReminderId = null) }
        }
    }

    fun deleteReminder(reminderId: String) {
        viewModelScope.launch {
            memoRepository.deleteReminderById(reminderId)
            alarmScheduler.cancelReminder(reminderId)
            refreshMemoReminderFields()
        }
    }

    fun clearAllReminders() {
        viewModelScope.launch {
            val reminders = memoRepository.getRemindersForMemo(memoId).first()
            reminders.forEach { alarmScheduler.cancelReminder(it.id) }
            memoRepository.deleteRemindersByMemo(memoId)
            memoRepository.updateReminder(memoId, false, null, RepeatType.NONE, "")
            _uiState.update { it.copy(showReminderSheet = false, editingReminderId = null) }
        }
    }

    fun toggleTag(tagId: String) {
        viewModelScope.launch {
            val selectedIds = _uiState.value.memoTags.map { it.id }.toSet()
            if (tagId in selectedIds) {
                memoRepository.removeTagFromMemo(memoId, tagId)
            } else {
                memoRepository.addTagToMemo(com.vocalize.app.data.local.entity.MemoTagCrossRef(memoId, tagId))
            }
        }
    }

    fun createTag(name: String) {
        viewModelScope.launch {
            val normalized = name.trim()
            if (normalized.isBlank()) return@launch
            val existing = memoRepository.getAllTags().first().firstOrNull { it.name.equals(normalized, ignoreCase = true) }
            val tag = existing ?: TagEntity(
                id = UUID.randomUUID().toString(),
                name = normalized,
                colorHex = "#${Integer.toHexString((0xFF shl 24) or (Math.abs(normalized.hashCode()) and 0xFFFFFF)).substring(2)}"
            )
            memoRepository.insertTag(tag)
            memoRepository.addTagToMemo(com.vocalize.app.data.local.entity.MemoTagCrossRef(memoId, tag.id))
        }
    }

    fun addToPlaylist(playlistId: String) {
        viewModelScope.launch {
            memoRepository.addMemoToPlaylist(PlaylistMemoCrossRef(playlistId, memoId))
            _uiState.update { it.copy(showPlaylistSheet = false) }
        }
    }

    fun showReminderSheet(reminderId: String? = null) = _uiState.update { it.copy(showReminderSheet = true, editingReminderId = reminderId) }
    fun hideReminderSheet() = _uiState.update { it.copy(showReminderSheet = false, editingReminderId = null) }
    fun showPlaylistSheet() = _uiState.update { it.copy(showPlaylistSheet = true) }
    fun hidePlaylistSheet() = _uiState.update { it.copy(showPlaylistSheet = false) }
    fun showTagSheet() = _uiState.update { it.copy(showTagSheet = true) }
    fun hideTagSheet() = _uiState.update { it.copy(showTagSheet = false) }

    fun showCategorySheet() = _uiState.update { it.copy(showCategorySheet = true) }
    fun hideCategorySheet() = _uiState.update { it.copy(showCategorySheet = false) }

    fun deleteMemo(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val memo = _uiState.value.memo ?: return@launch
            stopPlayback()
            alarmScheduler.cancelReminder(memoId)
            memoRepository.deleteMemo(memo)
            onDeleted()
        }
    }

    fun updateCategory(categoryId: String?) {
        viewModelScope.launch {
            val validCategoryId = if (categoryId != null && _uiState.value.categories.none { it.id == categoryId }) {
                null
            } else {
                categoryId
            }
            memoRepository.updateMemoCategory(memoId, validCategoryId, System.currentTimeMillis())
            _uiState.update { it.copy(showCategorySheet = false) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        positionJob?.cancel()
    }
}
