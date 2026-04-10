package com.vocalize.app.data.repository

import com.vocalize.app.data.local.dao.CategoryDao
import com.vocalize.app.data.local.dao.MemoDao
import com.vocalize.app.data.local.dao.PlaylistDao
import com.vocalize.app.data.local.entity.CategoryEntity
import com.vocalize.app.data.local.entity.MemoEntity
import com.vocalize.app.data.local.entity.PlaylistEntity
import com.vocalize.app.data.local.entity.PlaylistMemoCrossRef
import com.vocalize.app.data.local.entity.RepeatType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoRepository @Inject constructor(
    private val memoDao: MemoDao,
    private val categoryDao: CategoryDao,
    private val playlistDao: PlaylistDao
) {
    // ─── Memos ───────────────────────────────────────────
    fun getAllMemos(): Flow<List<MemoEntity>> = memoDao.getAllMemos()
    fun getPinnedMemos(): Flow<List<MemoEntity>> = memoDao.getPinnedMemos()
    fun getRecentMemos(): Flow<List<MemoEntity>> = memoDao.getRecentMemos()
    fun getMemoByIdFlow(id: String): Flow<MemoEntity?> = memoDao.getMemoByIdFlow(id)
    suspend fun getMemoById(id: String): MemoEntity? = memoDao.getMemoById(id)
    fun getMemosByCategory(categoryId: String): Flow<List<MemoEntity>> = memoDao.getMemosByCategory(categoryId)
    fun getUpcomingReminders(now: Long): Flow<List<MemoEntity>> = memoDao.getUpcomingReminders(now)
    fun getMemosByDate(dayStart: Long, dayEnd: Long): Flow<List<MemoEntity>> = memoDao.getMemosByDate(dayStart, dayEnd)
    fun getMemosByReminderDate(start: Long, end: Long): Flow<List<MemoEntity>> = memoDao.getMemosByReminderDate(start, end)
    fun searchMemos(query: String): Flow<List<MemoEntity>> = memoDao.searchMemos(query)
    fun getFilteredMemos(
        categoryId: String?,
        hasReminder: Boolean?,
        dateFrom: Long?,
        dateTo: Long?
    ): Flow<List<MemoEntity>> = memoDao.getFilteredMemos(categoryId, hasReminder, dateFrom, dateTo)
    fun getMemosByPlaylist(playlistId: String): Flow<List<MemoEntity>> = memoDao.getMemosByPlaylist(playlistId)

    suspend fun insertMemo(memo: MemoEntity) = memoDao.insertMemo(memo)
    suspend fun updateMemo(memo: MemoEntity) = memoDao.updateMemo(memo)
    suspend fun deleteMemo(memo: MemoEntity) = memoDao.deleteMemo(memo)
    suspend fun deleteMemoById(id: String) = memoDao.deleteMemoById(id)
    suspend fun updateTranscription(id: String, transcription: String) = memoDao.updateTranscription(id, transcription)
    suspend fun updateTitle(id: String, title: String, now: Long) = memoDao.updateTitle(id, title, now)
    suspend fun updateReminder(
        id: String,
        hasReminder: Boolean,
        reminderTime: Long?,
        repeatType: RepeatType,
        customDays: String
    ) = memoDao.updateReminder(id, hasReminder, reminderTime, repeatType.name, customDays)
    suspend fun updateCategory(id: String, categoryId: String?, now: Long) = memoDao.updateCategory(id, categoryId, now)
    suspend fun updateNote(id: String, note: String, now: Long) = memoDao.updateNote(id, note, now)
    suspend fun getAllMemosWithReminders(): List<MemoEntity> = memoDao.getAllMemosWithReminders()
    suspend fun getMemoCount(): Int = memoDao.getMemoCount()
    suspend fun getTotalDuration(): Long = memoDao.getTotalDuration() ?: 0L
    suspend fun updatePinned(id: String, pinned: Boolean) = memoDao.updatePinned(id, pinned)
    suspend fun updatePlaybackPosition(id: String, positionMs: Long) = memoDao.updatePlaybackPosition(id, positionMs)
    suspend fun deleteAllMemos() = memoDao.deleteAllMemos()

    // ─── Categories ───────────────────────────────────────────
    fun getAllCategories(): Flow<List<CategoryEntity>> = categoryDao.getAllCategories()
    suspend fun getCategoryById(id: String): CategoryEntity? = categoryDao.getCategoryById(id)
    suspend fun insertCategory(category: CategoryEntity) = categoryDao.insertCategory(category)
    suspend fun updateCategory(category: CategoryEntity) = categoryDao.updateCategory(category)
    suspend fun deleteCategory(category: CategoryEntity) = categoryDao.deleteCategory(category)

    // ─── Playlists ───────────────────────────────────────────
    fun getAllPlaylists(): Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()
    suspend fun getPlaylistById(id: String): PlaylistEntity? = playlistDao.getPlaylistById(id)
    suspend fun insertPlaylist(playlist: PlaylistEntity) = playlistDao.insertPlaylist(playlist)
    suspend fun updatePlaylist(playlist: PlaylistEntity) = playlistDao.updatePlaylist(playlist)
    suspend fun deletePlaylist(playlist: PlaylistEntity) = playlistDao.deletePlaylist(playlist)
    suspend fun addMemoToPlaylist(crossRef: PlaylistMemoCrossRef) = playlistDao.addMemoToPlaylist(crossRef)
    suspend fun removeMemoFromPlaylist(playlistId: String, memoId: String) = playlistDao.removeMemoFromPlaylistById(playlistId, memoId)
    fun getMemoCountForPlaylist(playlistId: String): Flow<Int> = playlistDao.getMemoCountForPlaylist(playlistId)
    suspend fun isMemoInPlaylist(playlistId: String, memoId: String): Boolean = playlistDao.isMemoInPlaylist(playlistId, memoId)
}
