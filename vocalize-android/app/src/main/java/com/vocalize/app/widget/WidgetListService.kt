package com.vocalize.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.vocalize.app.R
import com.vocalize.app.data.local.AppDatabase
import com.vocalize.app.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class WidgetListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        return WidgetMemoListFactory(applicationContext, appWidgetId)
    }
}

class WidgetMemoListFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    companion object {
        private const val TAG = "WidgetListService"
        private const val MAX_WIDGET_MEMOS = 1
    }

    private var memos: List<WidgetMemoStore.MemoSummary> = emptyList()

    override fun onCreate() {
        Log.d(TAG, "onCreate widgetId=$appWidgetId")
        loadData()
    }

    override fun onDataSetChanged() {
        Log.d(TAG, "onDataSetChanged widgetId=$appWidgetId")
        loadData()
    }

    private fun loadData() {
        try {
            Log.d(TAG, "loadData start widgetId=$appWidgetId")
            memos = WidgetMemoStore.loadCachedMemos(context)
            Log.d(TAG, "loadData read cache widgetId=$appWidgetId count=${memos.size}")
            if (memos.isEmpty()) {
                Log.d(TAG, "loadData cache empty, falling back to DB widgetId=$appWidgetId")
                runBlocking(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(context)
                    WidgetMemoStore.updateFromDatabase(context, db.memoDao())
                }
                memos = WidgetMemoStore.loadCachedMemos(context)
                Log.d(TAG, "loadData after DB fallback widgetId=$appWidgetId count=${memos.size}")
                VocalizeWidget.requestWidgetRefresh(context)
            }

            memos.forEach { memo ->
                Log.v(TAG, "loadData memo=${memo.id} title='${memo.title}' dateCreated=${memo.dateCreated}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to load widget memos for widgetId=$appWidgetId", e)
            VocalizeWidget.showCrashNotification(context, "Widget list failed to load", e)
            memos = emptyList()
        }
    }

    override fun onDestroy() { memos = emptyList() }

    override fun getCount(): Int = memos.size

    override fun getViewAt(position: Int): RemoteViews {
        val memo = memos.getOrNull(position)
        if (memo == null) {
            Log.w(TAG, "getViewAt position=$position missing memo, showing loading placeholder")
            return RemoteViews(context.packageName, R.layout.widget_memo_item).apply {
                setTextViewText(R.id.widget_item_title, "Loading…")
                setTextViewText(R.id.widget_item_subtitle, "")
            }
        }

        Log.d(TAG, "getViewAt position=$position memo=${memo.id} title='${memo.title}'")
        return RemoteViews(context.packageName, R.layout.widget_memo_item).apply {
            val displayTitle = memo.title.ifBlank { "Voice Memo" }
            setTextViewText(R.id.widget_item_title, displayTitle)
            setTextViewText(R.id.widget_item_subtitle, "")
            setInt(R.id.widget_item_color_strip, "setBackgroundColor", Color.TRANSPARENT)
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
