package com.vocalize.app.data.local.dao

import androidx.room.*
import com.vocalize.app.data.local.entity.CategoryEntity
import com.vocalize.app.data.local.entity.MemoCategoryCrossRef
import com.vocalize.app.data.local.entity.MemoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoDao {

    @Query("SELECT * FROM memos ORDER BY isPinned DESC, dateCreated DESC")
    fun getAllMemos(): Flow<List<MemoEntity>>

    @Query("SELECT * FROM memos WHERE isPinned = 1 ORDER BY dateModified DESC")
    fun getPinnedMemos(): Flow<List<MemoEntity>>

    @Query("SELECT * FROM memos ORDER BY dateCreated DESC LIMIT 10")
    fun getRecentMemos(): Flow<List<MemoEntity>>

    @Query("SELECT * FROM memos WHERE id = :id")
    suspend fun getMemoById(id: String): MemoEntity?

    @Query("SELECT * FROM memos WHERE id = :id")
    fun getMemoByIdFlow(id: String): Flow<MemoEntity?>

    @Query("SELECT m.* FROM memos m INNER JOIN memo_category_cross_ref mc ON m.id = mc.memoId WHERE mc.categoryId = :categoryId ORDER BY m.dateCreated DESC")
    fun getMemosByCategory(categoryId: String): Flow<List<MemoEntity>>

    @Query("SELECT c.* FROM categories c INNER JOIN memo_category_cross_ref mc ON c.id = mc.categoryId WHERE mc.memoId = :memoId ORDER BY c.name ASC")
    fun getCategoriesForMemo(memoId: String): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemoCategoryCrossRef(crossRef: MemoCategoryCrossRef)

    @Query("SELECT * FROM memo_category_cross_ref")
    suspend fun getAllMemoCategoryCrossRefs(): List<MemoCategoryCrossRef>

    @Query("DELETE FROM memo_category_cross_ref WHERE memoId = :memoId")
    suspend fun deleteCategoriesForMemo(memoId: String)

    @Query("DELETE FROM memo_category_cross_ref WHERE memoId = :memoId AND categoryId = :categoryId")
    suspend fun deleteMemoCategory(memoId: String, categoryId: String)

    @Transaction
    suspend fun replaceMemoCategories(memoId: String, categoryIds: List<String>) {
        deleteCategoriesForMemo(memoId)
        categoryIds.forEach { insertMemoCategoryCrossRef(MemoCategoryCrossRef(memoId, it)) }
    }

    @Query("SELECT * FROM memos WHERE hasReminder = 1 AND reminderTime >= :now ORDER BY reminderTime ASC")
    fun getUpcomingReminders(now: Long): Flow<List<MemoEntity>>

    @Query("SELECT * FROM memos WHERE dateCreated BETWEEN :dayStart AND :dayEnd")
    fun getMemosByDate(dayStart: Long, dayEnd: Long): Flow<List<MemoEntity>>

    @Query("SELECT DISTINCT m.* FROM memos m INNER JOIN reminders r ON m.id = r.memoId WHERE r.reminderTime BETWEEN :start AND :end ORDER BY r.reminderTime ASC")
    fun getMemosByReminderDate(start: Long, end: Long): Flow<List<MemoEntity>>

    @Query("""
        SELECT * FROM memos 
        WHERE title LIKE '%' || :query || '%' 
        OR textNote LIKE '%' || :query || '%' 
        OR transcription LIKE '%' || :query || '%'
        ORDER BY dateCreated DESC
    """)
    fun searchMemos(query: String): Flow<List<MemoEntity>>

    @Query("""
        SELECT DISTINCT m.* FROM memos m
        LEFT JOIN memo_category_cross_ref mc ON m.id = mc.memoId
        WHERE (:categoryId IS NULL OR mc.categoryId = :categoryId)
        AND (:hasReminder IS NULL OR m.hasReminder = :hasReminder)
        AND (:dateFrom IS NULL OR m.dateCreated >= :dateFrom)
        AND (:dateTo IS NULL OR m.dateCreated <= :dateTo)
        ORDER BY m.dateCreated DESC
    """)
    fun getFilteredMemos(
        categoryId: String?,
        hasReminder: Boolean?,
        dateFrom: Long?,
        dateTo: Long?
    ): Flow<List<MemoEntity>>

    @Query("SELECT COUNT(*) FROM memos")
    suspend fun getMemoCount(): Int

    @Query("SELECT SUM(duration) FROM memos")
    suspend fun getTotalDuration(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemo(memo: MemoEntity)

    @Update
    suspend fun updateMemo(memo: MemoEntity)

    @Delete
    suspend fun deleteMemo(memo: MemoEntity)

    @Query("DELETE FROM memos WHERE id = :id")
    suspend fun deleteMemoById(id: String)

    @Query("UPDATE memos SET transcription = :transcription, isTranscribing = 0 WHERE id = :id")
    suspend fun updateTranscription(id: String, transcription: String)

    @Query("UPDATE memos SET title = :title, dateModified = :now WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, now: Long)

    @Query("UPDATE memos SET hasReminder = :hasReminder, reminderTime = :reminderTime, repeatType = :repeatType, customDays = :customDays WHERE id = :id")
    suspend fun updateReminder(
        id: String,
        hasReminder: Boolean,
        reminderTime: Long?,
        repeatType: String,
        customDays: String
    )

    @Query("UPDATE memos SET categoryId = :categoryId, dateModified = :now WHERE id = :id")
    suspend fun updateCategory(id: String, categoryId: String?, now: Long)

    @Query("UPDATE memos SET textNote = :note, dateModified = :now WHERE id = :id")
    suspend fun updateNote(id: String, note: String, now: Long)

    @Query("SELECT * FROM memos WHERE hasReminder = 1")
    suspend fun getAllMemosWithReminders(): List<MemoEntity>

    @Query("SELECT * FROM memos WHERE id IN (SELECT memoId FROM playlist_memo_cross_ref WHERE playlistId = :playlistId ORDER BY position ASC)")
    fun getMemosByPlaylist(playlistId: String): Flow<List<MemoEntity>>

    @Query("SELECT m.* FROM memos m INNER JOIN memo_tag_cross_ref mt ON m.id = mt.memoId WHERE mt.tagId = :tagId ORDER BY m.dateCreated DESC")
    fun getMemosByTag(tagId: String): Flow<List<MemoEntity>>

    @Query("UPDATE memos SET isPinned = :pinned WHERE id = :id")
    suspend fun updatePinned(id: String, pinned: Boolean)

    @Query("UPDATE memos SET lastPlaybackPositionMs = :positionMs WHERE id = :id")
    suspend fun updatePlaybackPosition(id: String, positionMs: Long)

    @Query("DELETE FROM memos")
    suspend fun deleteAllMemos()

    @Query("SELECT * FROM memos ORDER BY dateCreated DESC LIMIT :limit")
    fun getRecentMemosSync(limit: Int): List<MemoEntity>

    @Query("SELECT * FROM memos ORDER BY dateCreated DESC LIMIT :limit")
    fun getWidgetMemos(limit: Int): List<MemoEntity>

    @Query("SELECT COUNT(*) FROM memos")
    fun getMemoCountSync(): Int
}
