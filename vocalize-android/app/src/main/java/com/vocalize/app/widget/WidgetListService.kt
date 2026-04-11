package com.vocalize.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.vocalize.app.R
import com.vocalize.app.data.local.AppDatabase
import com.vocalize.app.data.local.entity.MemoEntity
import com.vocalize.app.util.Constants
import com.vocalize.app.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class WidgetListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        WidgetMemoListFactory(applicationContext)
}

class WidgetMemoListFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var memos: List<MemoEntity> = emptyList()
    private var categoryColors: Map<String, String> = emptyMap()

    override fun onCreate() { loadData() }
    override fun onDataSetChanged() { loadData() }

    private fun loadData() {
        try {
            runBlocking {
                withContext(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(context)
                    // Pinned first, then by most recent — max 5 items
                    memos = db.memoDao().getWidgetMemos(5)
                    val categories = db.categoryDao().getAllCategoriesSync()
                    categoryColors = categories.associate { it.id to it.colorHex }
                    val totalCount = db.memoDao().getMemoCountSync()

                    // Cache count for the header (read synchronously by the widget provider)
                    VocalizeWidget.prefs(context)
                        .edit()
                        .putInt("widget_memo_count", totalCount)
                        .apply()
                }
            }
        } catch (e: Exception) {
            VocalizeWidget.showCrashNotification(context, "Widget list failed to load", e)
            memos = emptyList()
        }
    }

    override fun onDestroy() { memos = emptyList() }

    override fun getCount(): Int = memos.size

    override fun getViewAt(position: Int): RemoteViews {
        val memo = memos.getOrNull(position)
            ?: return RemoteViews(context.packageName, R.layout.widget_memo_item)

        return RemoteViews(context.packageName, R.layout.widget_memo_item).apply {

            // Title: prepend pin emoji for pinned memos
            val displayTitle = if (memo.isPinned) {
                "📌 ${memo.title.ifBlank { "Voice Memo" }}"
            } else {
                memo.title.ifBlank { "Voice Memo" }
            }
            setTextViewText(R.id.widget_item_title, displayTitle)

            // Subtitle: duration · relative time
            setTextViewText(
                R.id.widget_item_subtitle,
                "${Utils.formatDuration(memo.duration)} · ${Utils.formatTimestamp(memo.dateCreated)}"
            )

            // Category colour strip
            val colorHex = memo.categoryId?.let { categoryColors[it] }
            val stripColor = if (colorHex != null) {
                try { Color.parseColor(colorHex) } catch (_: Exception) { Color.parseColor("#EF4444") }
            } else {
                Color.parseColor("#EF4444")
            }
            setInt(R.id.widget_item_color_strip, "setBackgroundColor", stripColor)

            // Fill-in intent carries memoId and title for playback
            val fillIn = Intent().apply {
                putExtra(Constants.EXTRA_MEMO_ID, memo.id)
                putExtra(Constants.EXTRA_MEMO_TITLE, memo.title.ifBlank { "Voice Memo" })
            }
            setOnClickFillInIntent(R.id.widget_item_play, fillIn)
            setOnClickFillInIntent(R.id.widget_item_root, fillIn)
        }
    }

    override fun getLoadingView(): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_memo_item).apply {
            setTextViewText(R.id.widget_item_title, "Loading…")
            setTextViewText(R.id.widget_item_subtitle, "")
        }

    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long =
        memos.getOrNull(position)?.dateCreated ?: position.toLong()
    override fun hasStableIds(): Boolean = true
}
