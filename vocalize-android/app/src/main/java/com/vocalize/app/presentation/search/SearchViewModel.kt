package com.vocalize.app.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vocalize.app.data.local.entity.CategoryEntity
import com.vocalize.app.data.local.entity.MemoEntity
import com.vocalize.app.data.repository.MemoRepository
import com.vocalize.app.util.AudioFileManager
import com.vocalize.app.util.ReminderAlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<MemoEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategory: String? = null,
    val filterHasReminder: Boolean? = null,
    val filterDateFrom: Long? = null,
    val filterDateTo: Long? = null,
    val isSearching: Boolean = false,
    val showFilters: Boolean = false
)

@HiltViewModel
@OptIn(FlowPreview::class)
class SearchViewModel @Inject constructor(
    private val memoRepository: MemoRepository,
    private val audioFileManager: AudioFileManager,
    private val alarmScheduler: ReminderAlarmScheduler
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _filters = MutableStateFlow(
        Triple<String?, Boolean?, Pair<Long?, Long?>>(null, null, Pair(null, null))
    )

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            memoRepository.getAllCategories().collect { cats ->
                _uiState.update { it.copy(categories = cats) }
            }
        }
        viewModelScope.launch {
            combine(_query.debounce(300), _filters) { q, filters ->
                Pair(q, filters)
            }.collectLatest { (query, filters) ->
                val (catId, hasReminder, dateRange) = filters
                _uiState.update { it.copy(isSearching = true, query = query) }
                if (query.isBlank() && catId == null && hasReminder == null && dateRange.first == null) {
                    memoRepository.getAllMemos().collect { all ->
                        _uiState.update { it.copy(results = all, isSearching = false) }
                    }
                } else if (query.isNotBlank() && catId == null && hasReminder == null) {
                    memoRepository.searchMemos(query).collect { results ->
                        _uiState.update { it.copy(results = results, isSearching = false) }
                    }
                } else {
                    val q = if (query.isBlank()) null else "%$query%"
                    memoRepository.getFilteredMemos(catId, hasReminder, dateRange.first, dateRange.second)
                        .map { list -> if (q != null) list.filter { it.title.contains(query, ignoreCase = true) || it.transcription.contains(query, ignoreCase = true) } else list }
                        .collect { results ->
                            _uiState.update { it.copy(results = results, isSearching = false) }
                        }
                }
            }
        }
    }

    fun onQueryChange(q: String) {
        _query.value = q
        _uiState.update { it.copy(query = q) }
    }

    fun toggleFilters() = _uiState.update { it.copy(showFilters = !it.showFilters) }

    fun setCategory(id: String?) {
        _uiState.update { it.copy(selectedCategory = id) }
        _filters.update { Triple(id, it.second, it.third) }
    }

    fun setHasReminder(v: Boolean?) {
        _uiState.update { it.copy(filterHasReminder = v) }
        _filters.update { Triple(it.first, v, it.third) }
    }

    fun setDateRange(from: Long?, to: Long?) {
        _uiState.update { it.copy(filterDateFrom = from, filterDateTo = to) }
        _filters.update { Triple(it.first, it.second, Pair(from, to)) }
    }

    fun clearFilters() {
        _uiState.update { it.copy(selectedCategory = null, filterHasReminder = null, filterDateFrom = null, filterDateTo = null) }
        _filters.value = Triple(null, null, Pair(null, null))
    }

    fun deleteMemo(memo: MemoEntity) {
        viewModelScope.launch {
            if (memo.hasReminder) alarmScheduler.cancelRemindersForMemo(memo.id)
            audioFileManager.deleteAudioFile(memo.filePath)
            memoRepository.deleteMemo(memo)
        }
    }
}
