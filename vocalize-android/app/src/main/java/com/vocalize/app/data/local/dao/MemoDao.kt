package com.vocalize.app.data.local.dao

import androidx.room.*
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

    @Query("SELECT * FROM memos WHERE categoryId = :categoryId ORDER BY dateCreated DESC")
    fun getMemosByCategory(categoryId: String): Flow<List<MemoEntity>>

    @Query("SELECT * FROM memos WHERE hasReminder = 1 AND reminderTime >= :now ORDER BY reminderTime ASC")
    fun getUpcomingReminders(now: Long): Flow<List<MemoEntity>>

    @Query("SELECT * FROM memos WHERE reminderTime BETWEEN :dayStart AND :dayEnd")
    fun getMemosByDate(dayStart: Long, dayEnd: Long): Flow<List<MemoEntity>>

    @Query("""
        SELECT * FROM memos 
        WHERE title LIKE '%' || :query || '%' 
        OR textNote LIKE '%' || :query || '%' 
        OR transcription LIKE '%' || :query || '%'
        ORDER BY dateCreated DESC
    """)
    fun searchMemos(query: String): Flow<List<MemoEntity>>

    @Query("""
        SELECT * FROM memos 
        WHERE (:categoryId IS NULL OR categoryId = :categoryId)
        AND (:hasReminder IS NULL OR hasReminder = :hasReminder)
        AND (:dateFrom IS NULL OR dateCreated >= :dateFrom)
        AND (:dateTo IS NULL OR dateCreated <= :dateTo)
        ORDER BY dateCreated DESC
    """)
    fun getFilteredMemos(
        categoryId: String?,
        hasReminder: Boolean?,
        dateFrom: Long?,
        dateTo: Long?
    ): Flow<List<MemoEntity>>

    @Query("SELECT COUNT(*) FROM memos")
    suspend fun getMemoCount(): Int

    @Query("SELECT * FROM memos WHERE hasReminder = 1 AND reminderTime >= :start AND reminderTime < :end ORDER BY reminderTime ASC")
    fun getMemosByReminderDate(start: Long, end: Long): Flow<List<MemoEntity>>

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

    @Query("UPDATE memos SET isPinned = :pinned WHERE id = :id")
    suspend fun updatePinned(id: String, pinned: Boolean)

    @Query("UPDATE memos SET lastPlaybackPositionMs = :positionMs WHERE id = :id")
    suspend fun updatePlaybackPosition(id: String, positionMs: Long)

    @Query("DELETE FROM memos")
    suspend fun deleteAllMemos()

    @Query("SELECT * FROM memos ORDER BY dateCreated DESC LIMIT :limit")
    fun getRecentMemosSync(limit: Int): List<MemoEntity>
}
