package com.vocalize.app.data.repository

import android.content.Context
import com.vocalize.app.data.local.dao.CategoryDao
import com.vocalize.app.data.local.dao.MemoDao
import com.vocalize.app.data.local.dao.PlaylistDao
import com.vocalize.app.data.local.dao.ReminderDao
import com.vocalize.app.data.local.dao.ReminderLogDao
import com.vocalize.app.data.local.dao.TagDao
import com.vocalize.app.data.local.entity.CategoryEntity
import com.vocalize.app.data.local.entity.MemoCategoryCrossRef
import com.vocalize.app.data.local.entity.MemoEntity
import com.vocalize.app.data.local.entity.PlaylistEntity
import com.vocalize.app.data.local.entity.PlaylistMemoCrossRef
import com.vocalize.app.data.local.entity.ReminderEntity
import com.vocalize.app.data.local.entity.ReminderLogEntity
import com.vocalize.app.data.local.entity.TagEntity
import com.vocalize.app.data.local.entity.MemoTagCrossRef
import com.vocalize.app.data.local.entity.RepeatType
import com.vocalize.app.widget.VocalizeWidget
import com.vocalize.app.widget.WidgetMemoStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoDao: MemoDao,
    private val categoryDao: CategoryDao,
    private val playlistDao: PlaylistDao,
    private val tagDao: TagDao,
    private val reminderDao: ReminderDao,
    private val reminderLogDao: ReminderLogDao
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
    fun getRemindersByDate(start: Long, end: Long): Flow<List<ReminderEntity>> = reminderDao.getRemindersByDate(start, end)
    fun searchMemos(query: String): Flow<List<MemoEntity>> = memoDao.searchMemos(query)
    fun getFilteredMemos(
        categoryId: String?,
        hasReminder: Boolean?,
        dateFrom: Long?,
        dateTo: Long?
    ): Flow<List<MemoEntity>> = memoDao.getFilteredMemos(categoryId, hasReminder, dateFrom, dateTo)
    fun getMemosByPlaylist(playlistId: String): Flow<List<MemoEntity>> = memoDao.getMemosByPlaylist(playlistId)
    fun getMemosByTag(tagId: String): Flow<List<MemoEntity>> = memoDao.getMemosByTag(tagId)

    suspend fun insertMemo(memo: MemoEntity) {
        memoDao.insertMemo(memo)
        refreshWidgetCache()
    }

    suspend fun updateMemo(memo: MemoEntity) {
        memoDao.updateMemo(memo)
        refreshWidgetCache()
    }

    suspend fun deleteMemo(memo: MemoEntity) {
        memoDao.deleteMemo(memo)
        refreshWidgetCache()
    }

    suspend fun deleteMemoById(id: String) {
        memoDao.deleteMemoById(id)
        refreshWidgetCache()
    }

    suspend fun updateTranscription(id: String, transcription: String) = memoDao.updateTranscription(id, transcription)

    suspend fun updateTitle(id: String, title: String, now: Long) {
        memoDao.updateTitle(id, title, now)
        refreshWidgetCache()
    }
    suspend fun updateReminder(
        id: String,
        hasReminder: Boolean,
        reminderTime: Long?,
        repeatType: RepeatType,
        customDays: String
    ) = memoDao.updateReminder(id, hasReminder, reminderTime, repeatType.name, customDays)
    fun getTagsForMemo(memoId: String) = tagDao.getTagsForMemo(memoId)
    fun getAllTags(): Flow<List<TagEntity>> = tagDao.getAllTags()
    suspend fun getTagById(id: String): TagEntity? = tagDao.getTagById(id)
    suspend fun insertTag(tag: TagEntity) = tagDao.insertTag(tag)
    suspend fun addTagToMemo(crossRef: MemoTagCrossRef) = tagDao.addTagToMemo(crossRef)
    suspend fun getAllMemoTagCrossRefs(): List<MemoTagCrossRef> = tagDao.getAllMemoTagCrossRefs()
    suspend fun removeTagFromMemo(memoId: String, tagId: String) = tagDao.removeTagFromMemo(memoId, tagId)
    fun getMemoIdsByTag(tagId: String): Flow<List<String>> = tagDao.getMemoIdsByTag(tagId)

    fun getRemindersForMemo(memoId: String) = reminderDao.getRemindersForMemo(memoId)
    fun getUpcomingRemindersForDate(start: Long, end: Long) = reminderDao.getRemindersByDate(start, end)
    fun getAllUpcomingReminders(now: Long) = reminderDao.getUpcomingReminders(now)
    suspend fun getAllReminders(): List<ReminderEntity> = reminderDao.getAllReminders()
    suspend fun getReminderById(id: String): ReminderEntity? = reminderDao.getReminderById(id)
    suspend fun insertReminder(reminder: ReminderEntity) = reminderDao.insertReminder(reminder)
    suspend fun updateReminderEntry(id: String, reminderTime: Long, repeatType: RepeatType, customDays: String) = reminderDao.updateReminder(id, reminderTime, repeatType.name, customDays)
    suspend fun deleteReminderById(id: String) = reminderDao.deleteReminderById(id)
    suspend fun deleteRemindersByMemo(memoId: String) = reminderDao.deleteRemindersByMemo(memoId)
    suspend fun deleteReminder(reminder: ReminderEntity) = reminderDao.deleteReminderById(reminder.id)
    suspend fun updateReminderTime(id: String, reminderTime: Long) = reminderDao.updateReminder(id, reminderTime, RepeatType.NONE.name, "")
    suspend fun updateReminderFields(id: String, reminderTime: Long, repeatType: RepeatType, customDays: String) = reminderDao.updateReminder(id, reminderTime, repeatType.name, customDays)
    suspend fun getMemoTags(memoId: String): List<TagEntity> = getTagsForMemo(memoId).first()
    suspend fun getMemoReminders(memoId: String): List<ReminderEntity> = getRemindersForMemo(memoId).first()
    suspend fun updateNote(id: String, note: String, now: Long) = memoDao.updateNote(id, note, now)
    suspend fun getAllMemosWithReminders(): List<MemoEntity> = memoDao.getAllMemosWithReminders()
    suspend fun getMemoCount(): Int = memoDao.getMemoCount()
    suspend fun getTotalDuration(): Long = memoDao.getTotalDuration() ?: 0L
    suspend fun updatePinned(id: String, pinned: Boolean) = memoDao.updatePinned(id, pinned)
    suspend fun updatePlaybackPosition(id: String, positionMs: Long) = memoDao.updatePlaybackPosition(id, positionMs)
    suspend fun deleteAllMemos() {
        memoDao.deleteAllMemos()
        refreshWidgetCache()
    }

    private suspend fun refreshWidgetCache() = withContext(Dispatchers.IO) {
        WidgetMemoStore.updateFromDatabase(context, memoDao)
        VocalizeWidget.requestWidgetRefresh(context)
    }

    // ─── Reminder Logs ───────────────────────────────────────────
    suspend fun insertReminderLog(log: ReminderLogEntity) = reminderLogDao.insertLog(log)
    suspend fun getLatestLogForReminder(reminderId: String): ReminderLogEntity? = reminderLogDao.getLatestLogForReminder(reminderId)
    fun getAllReminderLogs(): Flow<List<ReminderLogEntity>> = reminderLogDao.getAllLogs()
    suspend fun pruneOldReminderLogs(beforeMs: Long) = reminderLogDao.deleteOldLogs(beforeMs)

    // ─── Categories ───────────────────────────────────────────
    fun getAllCategories(): Flow<List<CategoryEntity>> = categoryDao.getAllCategories()
    suspend fun getCategoryById(id: String): CategoryEntity? = categoryDao.getCategoryById(id)
    suspend fun insertCategory(category: CategoryEntity) = categoryDao.insertCategory(category)
    suspend fun updateCategory(category: CategoryEntity) = categoryDao.updateCategory(category)
    suspend fun updateMemoCategory(id: String, categoryId: String?, now: Long) = memoDao.updateCategory(id, categoryId, now)
    suspend fun replaceMemoCategories(memoId: String, categoryIds: List<String>) = memoDao.replaceMemoCategories(memoId, categoryIds)
    suspend fun addMemoCategoryCrossRef(crossRef: MemoCategoryCrossRef) = memoDao.insertMemoCategoryCrossRef(crossRef)
    suspend fun getAllMemoCategoryCrossRefs(): List<MemoCategoryCrossRef> = memoDao.getAllMemoCategoryCrossRefs()
    fun getCategoriesForMemo(memoId: String) = memoDao.getCategoriesForMemo(memoId)
    suspend fun deleteCategory(category: CategoryEntity) = categoryDao.deleteCategory(category)

    // ─── Reminder Flows (for UI observation) ─────────────────────────
    fun getAllRemindersFlow(): Flow<List<ReminderEntity>> = reminderDao.getAllRemindersFlow()

    // ─── Playlists ───────────────────────────────────────────
    fun getAllPlaylists(): Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()
    suspend fun getAllPlaylistMemoCrossRefs(): List<PlaylistMemoCrossRef> = playlistDao.getAllPlaylistMemoCrossRefs()
    suspend fun getPlaylistById(id: String): PlaylistEntity? = playlistDao.getPlaylistById(id)
    suspend fun insertPlaylist(playlist: PlaylistEntity) = playlistDao.insertPlaylist(playlist)
    suspend fun updatePlaylist(playlist: PlaylistEntity) = playlistDao.updatePlaylist(playlist)
    suspend fun deletePlaylist(playlist: PlaylistEntity) = playlistDao.deletePlaylist(playlist)
    suspend fun addMemoToPlaylist(crossRef: PlaylistMemoCrossRef) = playlistDao.addMemoToPlaylist(crossRef)
    suspend fun removeMemoFromPlaylist(playlistId: String, memoId: String) = playlistDao.removeMemoFromPlaylistById(playlistId, memoId)
    fun getMemoCountForPlaylist(playlistId: String): Flow<Int> = playlistDao.getMemoCountForPlaylist(playlistId)
    suspend fun isMemoInPlaylist(playlistId: String, memoId: String): Boolean = playlistDao.isMemoInPlaylist(playlistId, memoId)
}
