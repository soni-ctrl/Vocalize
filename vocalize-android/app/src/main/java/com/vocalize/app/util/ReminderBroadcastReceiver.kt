package com.vocalize.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.vocalize.app.data.local.entity.ReminderEntity
import com.vocalize.app.data.local.entity.RepeatType
import com.vocalize.app.data.repository.MemoRepository
import com.vocalize.app.service.PlaybackService
import com.vocalize.app.service.ReminderToneService
import dagger.hilt.android.AndroidEntryPoint
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vocalize.app.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class ReminderBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var alarmScheduler: ReminderAlarmScheduler
    @Inject lateinit var memoRepository: MemoRepository

    override fun onReceive(context: Context, intent: Intent) {
        val memoId = intent.getStringExtra(Constants.EXTRA_MEMO_ID) ?: return
        val memoTitle = intent.getStringExtra(Constants.EXTRA_MEMO_TITLE) ?: "Voice Memo"
        val reminderId = intent.getStringExtra(Constants.EXTRA_REMINDER_ID)
        val pendingResult = goAsync()

        when (intent.action) {
            Constants.ACTION_PLAY -> {
                val serviceIntent = Intent(context, ReminderToneService::class.java).apply {
                    action = ReminderToneService.ACTION_START_REMINDER
                    putExtra(Constants.EXTRA_MEMO_ID, memoId)
                    putExtra(Constants.EXTRA_MEMO_TITLE, memoTitle)
                    reminderId?.let { putExtra(Constants.EXTRA_REMINDER_ID, it) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        handleTriggeredReminder(memoId, memoTitle, reminderId)
                    } finally {
                        pendingResult.finish()
                    }
                }
                return
            }
            Constants.ACTION_SHOW_NOTE -> {
                notificationHelper.showReminderNoteNotification(memoId, memoTitle, reminderId)
                context.stopService(Intent(context, ReminderToneService::class.java))
            }
            Constants.ACTION_BACK_TO_REMINDER -> {
                notificationHelper.showReminderNotification(memoId, memoTitle, reminderId)
            }
            Constants.ACTION_REMINDER_PLAY -> {
                val playbackIntent = Intent(context, PlaybackService::class.java).apply {
                    action = Constants.ACTION_PLAY_AUDIO
                    putExtra(Constants.EXTRA_MEMO_ID, memoId)
                    putExtra(Constants.EXTRA_MEMO_TITLE, memoTitle)
                    putExtra(Constants.EXTRA_NOTIFICATION_ID, memoId.hashCode())
                    reminderId?.let { putExtra(Constants.EXTRA_REMINDER_ID, it) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(playbackIntent)
                } else {
                    context.startService(playbackIntent)
                }
                context.stopService(Intent(context, ReminderToneService::class.java))
            }
            Constants.ACTION_SNOOZE -> {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        handleSnoozeAction(context, memoId, memoTitle, reminderId)
                    } finally {
                        pendingResult.finish()
                    }
                }
                notificationHelper.cancelNotification(memoId)
                context.stopService(Intent(context, ReminderToneService::class.java))
                return
            }
            Constants.ACTION_DISMISS -> {
                notificationHelper.cancelNotification(memoId)
                context.stopService(Intent(context, ReminderToneService::class.java))
                pendingResult.finish()
                return
            }
        }

        pendingResult.finish()
    }

    private suspend fun handleTriggeredReminder(memoId: String, memoTitle: String, reminderId: String?) {
        if (reminderId == null) {
            refreshMemoReminderFields(memoId)
            return
        }

        val reminder = memoRepository.getReminderById(reminderId)
        if (reminder != null) {
            if (reminder.repeatType != RepeatType.NONE) {
                alarmScheduler.scheduleNextRepeat(reminder, memoTitle)
            } else {
                memoRepository.deleteReminderById(reminder.id)
            }
        }

        refreshMemoReminderFields(memoId)
    }

    private suspend fun refreshMemoReminderFields(memoId: String) {
        val reminders = memoRepository.getRemindersForMemo(memoId)
            .first()
            .filter { it.reminderTime > System.currentTimeMillis() }

        if (reminders.isEmpty()) {
            memoRepository.updateReminder(memoId, false, null, RepeatType.NONE, "")
        } else {
            val nextReminder = reminders.minByOrNull { it.reminderTime }!!
            memoRepository.updateReminder(
                memoId,
                true,
                nextReminder.reminderTime,
                nextReminder.repeatType,
                nextReminder.customDays
            )
        }
    }

    private suspend fun handleSnoozeAction(context: Context, memoId: String, memoTitle: String, reminderId: String?) {
        val snoozeMinutes = context.dataStore.data.first()[stringPreferencesKey(Constants.PREFS_DEFAULT_SNOOZE)]?.toIntOrNull() ?: 10
        val snoozeTime = System.currentTimeMillis() + snoozeMinutes * 60 * 1000L
        val tempReminder = ReminderEntity(
            id = java.util.UUID.randomUUID().toString(),
            memoId = memoId,
            reminderTime = snoozeTime,
            repeatType = RepeatType.NONE,
            customDays = ""
        )

        memoRepository.insertReminder(tempReminder)
        alarmScheduler.scheduleReminder(tempReminder, memoTitle)
        refreshMemoReminderFields(memoId)
    }
}
