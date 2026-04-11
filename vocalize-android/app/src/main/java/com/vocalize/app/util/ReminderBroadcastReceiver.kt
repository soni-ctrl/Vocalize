package com.vocalize.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.vocalize.app.data.repository.MemoRepository
import com.vocalize.app.service.ReminderToneService
import dagger.hilt.android.AndroidEntryPoint
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vocalize.app.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ReminderBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var alarmScheduler: ReminderAlarmScheduler
    @Inject lateinit var memoRepository: MemoRepository

    override fun onReceive(context: Context, intent: Intent) {
        val memoId = intent.getStringExtra(Constants.EXTRA_MEMO_ID) ?: return
        val memoTitle = intent.getStringExtra(Constants.EXTRA_MEMO_TITLE) ?: "Voice Memo"

        when (intent.action) {
            Constants.ACTION_PLAY -> {
                val serviceIntent = Intent(context, ReminderToneService::class.java).apply {
                    action = ReminderToneService.ACTION_START_REMINDER
                    putExtra(Constants.EXTRA_MEMO_ID, memoId)
                    putExtra(Constants.EXTRA_MEMO_TITLE, memoTitle)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                // Schedule next repeat if applicable
                CoroutineScope(Dispatchers.IO).launch {
                    val memo = memoRepository.getMemoById(memoId) ?: return@launch
                    if (memo.repeatType != com.vocalize.app.data.local.entity.RepeatType.NONE) {
                        alarmScheduler.scheduleNextRepeat(memo)
                    }
                }
            }
            Constants.ACTION_SHOW_NOTE -> {
                notificationHelper.showReminderNoteNotification(memoId, memoTitle)
            }
            Constants.ACTION_BACK_TO_REMINDER -> {
                val serviceIntent = Intent(context, ReminderToneService::class.java).apply {
                    action = ReminderToneService.ACTION_START_REMINDER
                    putExtra(Constants.EXTRA_MEMO_ID, memoId)
                    putExtra(Constants.EXTRA_MEMO_TITLE, memoTitle)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
            Constants.ACTION_SNOOZE -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val memo = memoRepository.getMemoById(memoId) ?: return@launch
                    val snoozeMinutes = context.dataStore.data.first()[stringPreferencesKey(Constants.PREFS_DEFAULT_SNOOZE)]?.toIntOrNull() ?: 10
                    val snoozeTime = System.currentTimeMillis() + snoozeMinutes * 60 * 1000L
                    alarmScheduler.scheduleReminder(memo.copy(reminderTime = snoozeTime))
                }
                notificationHelper.cancelNotification(memoId)
                context.stopService(Intent(context, ReminderToneService::class.java))
            }
            Constants.ACTION_DISMISS -> {
                notificationHelper.cancelNotification(memoId)
                context.stopService(Intent(context, ReminderToneService::class.java))
            }
        }
    }
}
