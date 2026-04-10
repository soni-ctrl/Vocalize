package com.vocalize.app.util

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import com.vocalize.app.CrashReportActivity
import java.io.PrintWriter
import java.io.StringWriter

object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val PREFS_NAME = "crash_reporter"
    private const val KEY_LAST_CRASH = "last_crash_log"

    fun init(application: Application) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashText = formatCrash(thread, throwable)
                saveCrash(application, crashText)
                launchCrashActivity(application, crashText)
            } catch (error: Throwable) {
                Log.e(TAG, "Crash reporter failed", error)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
                    ?: run {
                        Process.killProcess(Process.myPid())
                        System.exit(2)
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
