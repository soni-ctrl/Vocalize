package com.vocalize.app.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vocalize.app.data.local.entity.MemoEntity
import com.vocalize.app.data.repository.MemoRepository
import com.vocalize.app.util.ReminderAlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class CalendarUiState(
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH),
    val selectedDay: Int = Calendar.getInstance().get(Calendar.DAY_OF_MONTH),
    val memosOnSelectedDay: List<MemoEntity> = emptyList(),
    val remindersOnSelectedDay: List<MemoEntity> = emptyList(),
    val selectedTab: Int = 0, // 0 for memos, 1 for reminders
    val daysWithMemos: Set<Int> = emptySet(),
    val daysWithReminders: Set<Int> = emptySet(),
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val memoRepository: MemoRepository,
    private val alarmScheduler: ReminderAlarmScheduler
) : ViewModel() {

    private val _selectedYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    private val _selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH))
    private val _selectedDay = MutableStateFlow(Calendar.getInstance().get(Calendar.DAY_OF_MONTH))

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        // Observe month changes and load memos and reminders for the month
        viewModelScope.launch {
            combine(_selectedYear, _selectedMonth) { y, m -> Pair(y, m) }
                .flatMapLatest { (year, month) ->
                    val (start, end) = getMonthBounds(year, month)
                    combine(
                        memoRepository.getMemosByDate(start, end),
                        memoRepository.getRemindersByDate(start, end)
                    ) { memos, reminders -> Pair(memos, reminders) }
                }
                .collect { (memos, reminders) ->
                    val year = _selectedYear.value
                    val month = _selectedMonth.value
                    val daysWithMemos = mutableSetOf<Int>()
                    val daysWithReminders = mutableSetOf<Int>()
                    memos.forEach { memo ->
                        val cal = Calendar.getInstance().apply { timeInMillis = memo.dateCreated }
                        if (cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month) {
                            daysWithMemos.add(cal.get(Calendar.DAY_OF_MONTH))
                        }
                    }
                    reminders.forEach { reminder ->
                        val rc = Calendar.getInstance().apply { timeInMillis = reminder.reminderTime }
                        daysWithReminders.add(rc.get(Calendar.DAY_OF_MONTH))
                    }
                    _uiState.update { it.copy(daysWithMemos = daysWithMemos, daysWithReminders = daysWithReminders, isLoading = false) }
                }
        }

        // Observe day changes and load memos and reminders for that day
        viewModelScope.launch {
            combine(_selectedYear, _selectedMonth, _selectedDay) { y, m, d -> Triple(y, m, d) }
                .flatMapLatest { (year, month, day) ->
                    val (start, end) = getDayBounds(year, month, day)
                    combine(
                        memoRepository.getMemosByDate(start, end), // memos created on day
                        memoRepository.getMemosByReminderDate(start, end) // memos with reminder on day
                    ) { created, reminded -> Pair(created, reminded) }
                }
                .collect { (created, reminded) ->
                    _uiState.update { state ->
                        state.copy(
                            selectedYear = _selectedYear.value,
                            selectedMonth = _selectedMonth.value,
                            selectedDay = _selectedDay.value,
                            memosOnSelectedDay = created,
                            remindersOnSelectedDay = reminded
                        )
                    }
                }
        }
    }

    fun selectTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun selectDay(day: Int) {
        _selectedDay.value = day
        _uiState.update { it.copy(selectedDay = day, selectedTab = 0) }
    }

    fun changeMonth(delta: Int) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, _selectedYear.value)
            set(Calendar.MONTH, _selectedMonth.value)
            add(Calendar.MONTH, delta)
        }
        _selectedYear.value = cal.get(Calendar.YEAR)
        _selectedMonth.value = cal.get(Calendar.MONTH)
        _selectedDay.value = 1
        _uiState.update {
            it.copy(
                selectedYear = cal.get(Calendar.YEAR),
                selectedMonth = cal.get(Calendar.MONTH),
                selectedDay = 1,
                selectedTab = 0,
                memosOnSelectedDay = emptyList(),
                remindersOnSelectedDay = emptyList(),
                isLoading = true
            )
        }
    }

    fun deleteMemo(memo: MemoEntity) {
        viewModelScope.launch {
            if (memo.hasReminder) alarmScheduler.cancelRemindersForMemo(memo.id)
            memoRepository.deleteMemo(memo)
        }
    }

    private fun getMonthBounds(year: Int, month: Int): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            set(year, month, 1, 23, 59, 59); set(Calendar.MILLISECOND, 999)
            add(Calendar.MONTH, 1); add(Calendar.DAY_OF_MONTH, -1)
        }.timeInMillis
        return Pair(start, end)
    }

    private fun getDayBounds(year: Int, month: Int, day: Int): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(year, month, day, 0, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            set(year, month, day, 23, 59, 59); set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        return Pair(start, end)
    }
}
