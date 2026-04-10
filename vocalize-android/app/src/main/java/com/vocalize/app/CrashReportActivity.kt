package com.vocalize.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.vocalize.app.util.CrashReporter

class CrashReportActivity : ComponentActivity() {
    companion object {
        const val EXTRA_CRASH_LOG = "extra_crash_log"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash_report)

        val crashLog = intent.getStringExtra(EXTRA_CRASH_LOG)
            ?: CrashReporter.getSavedCrashLog(this)
            ?: getString(R.string.crash_report_no_details)

        val crashDetailsView = findViewById<TextView>(R.id.crashDetails)
        crashDetailsView.text = crashLog

        findViewById<Button>(R.id.copyCrashButton).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(getString(R.string.crash_report_clipboard_label), crashLog)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, R.string.crash_report_copied, Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.notifyCrashButton).setOnClickListener {
            CrashReporter.notifyLastCrash(this)
            Toast.makeText(this, R.string.crash_report_notification_sent, Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.closeCrashButton).setOnClickListener {
            CrashReporter.clearSavedCrashLog(this)
            finishAffinity()
        }
    }
}
