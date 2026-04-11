package com.vocalize.app.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.vocalize.app.R
import com.vocalize.app.service.PlaybackService
import com.vocalize.app.util.Constants

class VocalizeWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            try {
                updateAppWidget(context, appWidgetManager, id)
            } catch (e: Exception) {
                showCrashNotification(context, "Widget failed to update", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> {
                Log.d(TAG, "onReceive ACTION_REFRESH")
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, VocalizeWidget::class.java))
                Log.d(TAG, "onReceive ACTION_REFRESH widgetIds=${ids.joinToString()}")
                ids.forEach { id ->
                    try {
                        manager.notifyAppWidgetViewDataChanged(id, R.id.widget_memo_list)
                        updateAppWidget(context, manager, id)
                        Log.d(TAG, "onReceive ACTION_REFRESH updated widgetId=$id")
                    } catch (e: Exception) {
                        showCrashNotification(context, "Widget refresh failed", e)
                    }
                }
            }

            ACTION_PLAY_MEMO -> {
                // Play memo directly without opening the app — starts PlaybackService
                val memoId = intent.getStringExtra(Constants.EXTRA_MEMO_ID) ?: return
                val memoTitle = intent.getStringExtra(Constants.EXTRA_MEMO_TITLE) ?: "Voice Memo"
                try {
                    val serviceIntent = Intent(context, PlaybackService::class.java).apply {
                        action = Constants.ACTION_PLAY_AUDIO
                        putExtra(Constants.EXTRA_MEMO_ID, memoId)
                        putExtra(Constants.EXTRA_MEMO_TITLE, memoTitle)
                        putExtra(Constants.EXTRA_NOTIFICATION_ID, memoId.hashCode())
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    showCrashNotification(context, "Could not play memo", e)
                }
            }

            ACTION_OPEN_RECORDER -> {
                try {
                    val overlayIntent = Intent(context, WidgetRecorderActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(overlayIntent)
                } catch (e: Exception) {
                    showCrashNotification(context, "Could not open recorder", e)
                }
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.vocalize.app.widget.ACTION_REFRESH"
        const val ACTION_PLAY_MEMO = "com.vocalize.app.widget.ACTION_PLAY_MEMO"
        const val ACTION_OPEN_RECORDER = "com.vocalize.app.widget.ACTION_OPEN_RECORDER"

        private const val TAG = "VocalizeWidget"
        private const val PREFS_NAME = "vocalize_widget_prefs"
        private const val CRASH_CHANNEL_ID = "vocalize_widget_crash"
        private const val CRASH_NOTIF_ID = 9900

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_vocalize)

            // ── Header: memo count from cached widget store ──
            val count = WidgetMemoStore.getCachedMemoCount(context)
            val countLabel = if (count == 1) "1 memo" else "$count memos"
            views.setTextViewText(R.id.widget_memo_count, countLabel)

            // ── Record button → open WidgetRecorderActivity overlay ──
            val recordIntent = Intent(context, VocalizeWidget::class.java).apply {
                action = ACTION_OPEN_RECORDER
            }
            val recordPending = PendingIntent.getBroadcast(
                context, 0, recordIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_record_button, recordPending)

            // ── Refresh button ──
            val refreshIntent = Intent(context, VocalizeWidget::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPending = PendingIntent.getBroadcast(
                context, 1, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPending)

            // ── Latest memo title display ──
            val latestMemo = WidgetMemoStore.getLatestMemo(context)
            if (latestMemo != null) {
                views.setTextViewText(R.id.widget_latest_memo_title, latestMemo.title.ifBlank { "Voice Memo" })
                views.setTextViewText(R.id.widget_latest_memo_subtitle, "Latest memo")
            } else {
                views.setTextViewText(R.id.widget_latest_memo_title, "No voice memos yet")
                views.setTextViewText(R.id.widget_latest_memo_subtitle, "Tap record to add one")
            }

            val latestMemoId = latestMemo?.id ?: "none"
            val latestMemoTitle = latestMemo?.title ?: "none"
            Log.d(TAG, "updateAppWidget widgetId=$appWidgetId count=$count latestMemo=$latestMemoId title='$latestMemoTitle'")
            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_memo_list)
        }

        fun requestWidgetRefresh(context: Context) {
            Log.d(TAG, "requestWidgetRefresh")
            val intent = Intent(context, VocalizeWidget::class.java).apply {
                action = ACTION_REFRESH
            }
            context.sendBroadcast(intent)
        }

        fun showCrashNotification(context: Context, title: String, error: Throwable) {
            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CRASH_CHANNEL_ID,
                        "Widget Errors",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Notifications when the Vocalize widget has an error"
                    }
                    manager.createNotificationChannel(channel)
                }
                val details = buildString {
                    appendLine("Error: ${error.javaClass.simpleName}")
                    appendLine("Message: ${error.message ?: "No message"}")
                    appendLine()
                    appendLine("Stack trace:")
                    appendLine(error.stackTraceToString().take(800))
                }
                val notif = NotificationCompat.Builder(context, CRASH_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_mic)
                    .setContentTitle("Vocalize Widget — $title")
                    .setContentText(error.message ?: error.javaClass.simpleName)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(details))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()
                manager.notify(CRASH_NOTIF_ID, notif)
            } catch (_: Exception) {
                // If showing notification also fails, there's nothing else we can do
            }
        }
    }
}
