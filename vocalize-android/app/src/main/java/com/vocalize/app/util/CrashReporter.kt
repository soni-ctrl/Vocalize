package com.vocalize.app.util

import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vocalize.app.CrashReportActivity
import com.vocalize.app.VocalizeApplication
import com.vocalize.app.R
import java.io.PrintWriter
import java.io.StringWriter

object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val PREFS_NAME = "crash_reporter"
    private const val KEY_LAST_CRASH = "last_crash_log"
    private const val NOTIF_ID_CRASH = 99999
    private const val ACTION_COPY_CRASH = "com.vocalize.app.ACTION_COPY_CRASH"
    private const val ACTION_SHOW_CRASH = "com.vocalize.app.ACTION_SHOW_CRASH"

    fun init(application: Application) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashText = formatCrash(thread, throwable)
                saveCrash(application, crashText)
                sendCrashNotification(application, crashText)
                launchCrashActivity(application, crashText)
            } catch (error: Throwable) {
                Log.e(TAG, "Crash reporter failed", error)
            } finally {
                if (thread == Looper.getMainLooper().thread) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        Process.killProcess(Process.myPid())
                        System.exit(2)
                    }, 1500)
                } else {
                    defaultHandler?.uncaughtException(thread, throwable)
                        ?: run {
                            Process.killProcess(Process.myPid())
                            System.exit(2)
                        }
                }
            }
        }
    }

    private fun formatCrash(thread: Thread, throwable: Throwable): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        return buildString {
            appendLine("Thread: ${thread.name} (${thread.id})")
            appendLine("Exception: ${throwable::class.java.name}")
            appendLine("Message: ${throwable.message}")
            appendLine()
            append(writer.toString())
            throwable.cause?.let { cause ->
                appendLine()
                appendLine("Caused by: ${cause::class.java.name}")
                appendLine("Cause message: ${cause.message}")
                val causeWriter = StringWriter()
                cause.printStackTrace(PrintWriter(causeWriter))
                append(causeWriter.toString())
            }
        }
    }

    private fun saveCrash(context: Context, crashText: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_CRASH, crashText)
            .apply()
    }

    private fun launchCrashActivity(context: Context, crashText: String) {
        val intent = Intent(context, CrashReportActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(CrashReportActivity.EXTRA_CRASH_LOG, crashText)
        }
        context.startActivity(intent)
    }

    private fun sendCrashNotification(context: Context, crashText: String) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val viewIntent = Intent(context, CrashReportActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(CrashReportActivity.EXTRA_CRASH_LOG, crashText)
        }
        val viewPending = PendingIntent.getActivity(
            context,
            0,
            viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val copyIntent = Intent(context, CrashNotificationReceiver::class.java).apply {
            action = ACTION_COPY_CRASH
            putExtra(CrashReportActivity.EXTRA_CRASH_LOG, crashText)
        }
        val copyPending = PendingIntent.getBroadcast(
            context,
            1,
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bigText = crashText.take(1024)
        val notification = NotificationCompat.Builder(context, VocalizeApplication.CHANNEL_CRASH)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(context.getString(R.string.crash_notification_title))
            .setContentText(context.getString(R.string.crash_notification_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(viewPending)
            .addAction(R.drawable.ic_share, context.getString(R.string.crash_notification_action_copy), copyPending)
            .addAction(R.drawable.ic_mic, context.getString(R.string.crash_notification_action_view), viewPending)
            .build()

        notificationManager?.notify(NOTIF_ID_CRASH, notification)
    }

    fun notifyLastCrash(context: Context) {
        getSavedCrashLog(context)?.let { sendCrashNotification(context, it) }
    }

    fun copyCrashLog(context: Context, crashText: String?) {
        crashText ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(context.getString(R.string.crash_report_clipboard_label), crashText)
        clipboard.setPrimaryClip(clip)
    }

    fun getSavedCrashLog(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_CRASH, null)
    }

    fun clearSavedCrashLog(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_CRASH)
            .apply()
    }
}
