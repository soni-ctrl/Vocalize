package com.vocalize.app.widget

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.vocalize.app.data.local.dao.MemoDao
import com.vocalize.app.data.local.entity.MemoEntity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object WidgetMemoStore {
    private const val TAG = "WidgetMemoStore"
    private const val PREFS_NAME = "vocalize_widget_cache"
    private const val KEY_MEMOS = "widget_memos_json"
    private const val KEY_COUNT = "widget_memo_count"
    private const val MAX_CACHED_MEMOS = 5

    data class MemoSummary(
        val id: String,
        val title: String,
        val dateCreated: Long
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadCachedMemos(context: Context): List<MemoSummary> {
        val raw = prefs(context).getString(KEY_MEMOS, null)
        if (raw.isNullOrBlank()) {
            Log.d(TAG, "loadCachedMemos: no cache stored")
            return emptyList()
        }

        return try {
            val array = JSONArray(raw)
            val memos = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").takeIf(String::isNotBlank) ?: continue
                    val title = item.optString("title", "Voice Memo")
                    val dateCreated = item.optLong("dateCreated", 0L)
                    add(MemoSummary(id, title, dateCreated))
                }
            }.sortedByDescending { it.dateCreated }

            Log.d(TAG, "loadCachedMemos: loaded ${memos.size} memo(s) from cache")
            memos.forEach { Log.v(TAG, "cached memo=${it.id} title='${it.title}' dateCreated=${it.dateCreated}") }
            memos
        } catch (error: JSONException) {
            Log.e(TAG, "loadCachedMemos: invalid JSON cache", error)
            emptyList()
        }
    }

    fun getCachedMemoCount(context: Context): Int {
        val count = prefs(context).getInt(KEY_COUNT, 0)
        Log.d(TAG, "getCachedMemoCount: $count")
        return count
    }

    fun getLatestMemo(context: Context): MemoSummary? {
        val latest = loadCachedMemos(context).firstOrNull()
        val latestId = latest?.id ?: "none"
        val latestTitle = latest?.title ?: "null"
        Log.d(TAG, "getLatestMemo: $latestId title='$latestTitle'")
        return latest
    }

    fun saveMemos(context: Context, memos: List<MemoSummary>) {
        val array = JSONArray()
        memos.forEach { memo ->
            array.put(
                JSONObject().apply {
                    put("id", memo.id)
                    put("title", memo.title)
                    put("dateCreated", memo.dateCreated)
                }
            )
        }

        prefs(context)
            .edit()
            .putString(KEY_MEMOS, array.toString())
            .apply()

        Log.d(TAG, "saveMemos: stored ${memos.size} memo(s)")
        memos.forEach { Log.v(TAG, "saved memo=${it.id} title='${it.title}' dateCreated=${it.dateCreated}") }
    }

    fun saveMemoCount(context: Context, count: Int) {
        prefs(context)
            .edit()
            .putInt(KEY_COUNT, count)
            .apply()
        Log.d(TAG, "saveMemoCount: $count")
    }

    fun updateFromDatabase(context: Context, memoDao: MemoDao) {
        Log.d(TAG, "updateFromDatabase: querying latest $MAX_CACHED_MEMOS memo(s) from DB")
        val rawMemos = memoDao.getWidgetMemos(MAX_CACHED_MEMOS)
        val memos = rawMemos.map { memo ->
            MemoSummary(
                id = memo.id,
                title = memo.title.ifBlank { "Voice Memo" },
                dateCreated = memo.dateCreated
            )
        }
        Log.d(TAG, "updateFromDatabase: loaded ${memos.size} memo(s) from DB")
        rawMemos.forEach { Log.v(TAG, "db memo=${it.id} title='${it.title}' dateCreated=${it.dateCreated}") }
        saveMemos(context, memos)
        saveMemoCount(context, memoDao.getMemoCountSync())
    }

    fun updateMemo(context: Context, memo: MemoEntity) {
        Log.d(TAG, "updateMemo: memo=${memo.id} title='${memo.title}' dateCreated=${memo.dateCreated}")
        val cached = loadCachedMemos(context)
            .filterNot { it.id == memo.id }
            .toMutableList()

        cached.add(
            MemoSummary(
                id = memo.id,
                title = memo.title.ifBlank { "Voice Memo" },
                dateCreated = memo.dateCreated
            )
        )

        saveMemos(context, cached
            .sortedByDescending { it.dateCreated }
            .take(MAX_CACHED_MEMOS)
        )
    }

    fun removeMemo(context: Context, memoId: String) {
        Log.d(TAG, "removeMemo: memoId=$memoId")
        val remaining = loadCachedMemos(context).filterNot { it.id == memoId }
        saveMemos(context, remaining.take(MAX_CACHED_MEMOS))
    }
}
