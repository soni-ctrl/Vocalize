package com.vocalize.app.presentation.home

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vocalize.app.data.local.entity.CategoryEntity
import com.vocalize.app.data.local.entity.MemoEntity
import com.vocalize.app.data.local.entity.PlaylistEntity
import com.vocalize.app.data.local.entity.PlaylistMemoCrossRef
import com.vocalize.app.data.repository.MemoRepository
import com.vocalize.app.util.AudioFileManager
import com.vocalize.app.util.Constants
import com.vocalize.app.util.ReminderAlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class HomeUiState(
    val recentMemos: List<MemoEntity> = emptyList(),
    val allMemos: List<MemoEntity> = emptyList(),
    val pinnedMemos: List<MemoEntity> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategoryFilter: String? = null,
    val isLoading: Boolean = true,
    val totalMemos: Int = 0,
    val totalDurationMs: Long = 0L,
    val selectedMemoIds: Set<String> = emptySet(),
    val isBatchMode: Boolean = false,
    val snackbarMessage: String? = null,
    val memoCategories: Map<String, List<CategoryEntity>> = emptyMap()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val memoRepository: MemoRepository,
    private val audioFileManager: AudioFileManager,
    private val alarmScheduler: ReminderAlarmScheduler,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
        seedDefaultCategories()
    }

    private fun loadData() {
        viewModelScope.launch {
            val filter = _uiState.value.selectedCategoryFilter
            val allMemosFlow = if (filter != null) memoRepository.getMemosByCategory(filter) else memoRepository.getAllMemos()

            combine(
                allMemosFlow,
                memoRepository.getAllPlaylists(),
                memoRepository.getAllCategories()
            ) { all, playlists, categories ->
                Triple(all, playlists, categories)
            }.collect { (all, playlists, categories) ->
                val pinned = all.filter { it.isPinned }
                val recent = all.sortedByDescending { it.dateCreated }.take(10)

                val memoCategories = all.associate { memo ->
                    memo.id to memoRepository.getCategoriesForMemo(memo.id).first()
                }

                _uiState.value = _uiState.value.copy(
                    recentMemos = recent,
                    allMemos = all,
                    pinnedMemos = pinned,
                    playlists = playlists,
                    categories = categories,
                    memoCategories = memoCategories,
                    isLoading = false,
                    totalMemos = all.size,
                    totalDurationMs = all.sumOf { it.duration }
                )
            }
        }
    }

    private fun seedDefaultCategories() {
        viewModelScope.launch {
            val existing = memoRepository.getAllCategories().first()
            if (existing.isEmpty()) {
                Constants.DEFAULT_CATEGORIES.forEach { (id, name, color) ->
                    memoRepository.insertCategory(
                        CategoryEntity(
                            id = id,
                            name = name,
                            colorHex = color,
                            iconName = name.lowercase(),
                            isDefault = true
                        )
                    )
                }
            }
        }
    }

    fun deleteMemo(memo: MemoEntity) {
        viewModelScope.launch {
            if (memo.hasReminder) alarmScheduler.cancelRemindersForMemo(memo.id)
            audioFileManager.deleteAudioFile(memo.filePath)
            memoRepository.deleteMemo(memo)
            showSnackbar("Memo deleted")
        }
    }

    fun togglePin(memo: MemoEntity) {
        viewModelScope.launch {
            memoRepository.updatePinned(memo.id, !memo.isPinned)
            showSnackbar(if (memo.isPinned) "Unpinned" else "Pinned to top")
        }
    }

    fun enterBatchMode(memoId: String) {
        _uiState.update { it.copy(isBatchMode = true, selectedMemoIds = setOf(memoId)) }
    }

    fun toggleMemoSelection(memoId: String) {
        _uiState.update { state ->
            val updated = if (memoId in state.selectedMemoIds)
                state.selectedMemoIds - memoId
            else
                state.selectedMemoIds + memoId
            state.copy(
                selectedMemoIds = updated,
                isBatchMode = updated.isNotEmpty()
            )
        }
    }

    fun selectAll() {
        val allIds = _uiState.value.allMemos.map { it.id }.toSet()
        _uiState.update { it.copy(selectedMemoIds = allIds, isBatchMode = true) }
    }

    fun cancelBatchMode() {
        _uiState.update { it.copy(isBatchMode = false, selectedMemoIds = emptySet()) }
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val ids = _uiState.value.selectedMemoIds.toList()
            val memos = ids.mapNotNull { memoRepository.getMemoById(it) }
            memos.forEach { memo ->
                if (memo.hasReminder) alarmScheduler.cancelRemindersForMemo(memo.id)
                audioFileManager.deleteAudioFile(memo.filePath)
                memoRepository.deleteMemo(memo)
            }
            cancelBatchMode()
            showSnackbar("${memos.size} memo${if (memos.size != 1) "s" else ""} deleted")
        }
    }

    fun deleteAllMemos() {
        viewModelScope.launch {
            val all = memoRepository.getAllMemos().first()
            all.forEach { memo ->
                if (memo.hasReminder) alarmScheduler.cancelRemindersForMemo(memo.id)
                audioFileManager.deleteAudioFile(memo.filePath)
            }
            memoRepository.deleteAllMemos()
            cancelBatchMode()
            showSnackbar("All memos deleted")
        }
    }

    fun importAudio(uri: Uri) {
        viewModelScope.launch {
            try {
                val fileName = getFileName(uri) ?: "Import_${System.currentTimeMillis()}"
                val dest = File(audioFileManager.getRecordingsDir(), fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                val memoId = UUID.randomUUID().toString()
                val durationMs = audioFileManager.getAudioDuration(dest.absolutePath)
                val now = System.currentTimeMillis()
                memoRepository.insertMemo(
                    MemoEntity(
                        id = memoId,
                        title = fileName.substringBeforeLast("."),
                        filePath = dest.absolutePath,
                        duration = durationMs,
                        dateCreated = now,
                        dateModified = now
                    )
                )
                showSnackbar("Audio imported successfully")
            } catch (e: Exception) {
                showSnackbar("Import failed: ${e.message}")
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx)
            }
        }
        return name
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            memoRepository.insertPlaylist(
                PlaylistEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch { memoRepository.deletePlaylist(playlist) }
    }

    fun addMemoToPlaylist(memoId: String, playlistId: String) {
        viewModelScope.launch {
            memoRepository.addMemoToPlaylist(PlaylistMemoCrossRef(playlistId, memoId))
        }
    }

    fun setCategoryFilter(categoryId: String?) {
        _uiState.update { it.copy(selectedCategoryFilter = categoryId) }
        loadData()
    }

    fun updateMemoTitle(memoId: String, title: String) {
        viewModelScope.launch {
            memoRepository.updateTitle(memoId, title, System.currentTimeMillis())
        }
    }

    fun showSnackbar(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
