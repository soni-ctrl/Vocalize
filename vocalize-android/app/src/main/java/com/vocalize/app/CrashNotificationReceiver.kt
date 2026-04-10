package com.vocalize.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.vocalize.app.util.CrashReporter

class CrashNotificationReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_COPY_CRASH = "com.vocalize.app.ACTION_COPY_CRASH"
        const val ACTION_SHOW_CRASH = "com.vocalize.app.ACTION_SHOW_CRASH"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_COPY_CRASH -> {
                val crashLog = intent.getStringExtra(CrashReportActivity.EXTRA_CRASH_LOG)
                    ?: CrashReporter.getSavedCrashLog(context)
                if (crashLog != null) {
                    CrashReporter.copyCrashLog(context, crashLog)
                    Toast.makeText(context, R.string.crash_report_copied, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, R.string.crash_report_no_details, Toast.LENGTH_SHORT).show()
                }
            }
            ACTION_SHOW_CRASH -> {
                val crashLog = intent.getStringExtra(CrashReportActivity.EXTRA_CRASH_LOG)
                    ?: CrashReporter.getSavedCrashLog(context)
                val activityIntent = Intent(context, CrashReportActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra(CrashReportActivity.EXTRA_CRASH_LOG, crashLog)
                }
                context.startActivity(activityIntent)
            }
        }
    }
}
