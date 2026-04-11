package com.vocalize.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.vocalize.app.data.local.entity.RepeatType
import com.vocalize.app.data.repository.MemoRepository
import com.vocalize.app.service.ReminderToneService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val notificationHelper: NotificationHelper,
    private val memoRepository: MemoRepository,
    private val alarmScheduler: ReminderAlarmScheduler
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val memoId = inputData.getString(Constants.EXTRA_MEMO_ID) ?: return Result.failure()
        val memoTitle = inputData.getString(Constants.EXTRA_MEMO_TITLE) ?: "Voice Memo"
        val reminderId = inputData.getString(Constants.EXTRA_REMINDER_ID)

        setForeground(getForegroundInfo())

        val serviceIntent = Intent(appContext, ReminderToneService::class.java).apply {
            action = ReminderToneService.ACTION_START_REMINDER
            putExtra(Constants.EXTRA_MEMO_ID, memoId)
            putExtra(Constants.EXTRA_MEMO_TITLE, memoTitle)
            reminderId?.let { putExtra(Constants.EXTRA_REMINDER_ID, it) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(serviceIntent)
        } else {
            appContext.startService(serviceIntent)
        }

        if (reminderId != null) {
            val reminder = memoRepository.getReminderById(reminderId)
            if (reminder != null) {
                if (reminder.repeatType != RepeatType.NONE) {
                    alarmScheduler.scheduleNextRepeat(reminder, memoTitle)
                } else {
                    memoRepository.deleteReminderById(reminder.id)
                }
            }
        }

        refreshMemoReminderFields(memoId)
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val memoId = inputData.getString(Constants.EXTRA_MEMO_ID) ?: "unknown"
        val memoTitle = inputData.getString(Constants.EXTRA_MEMO_TITLE) ?: "Voice Memo"
        val reminderId = inputData.getString(Constants.EXTRA_REMINDER_ID)
        val notification = notificationHelper.buildReminderNotification(memoId, memoTitle, reminderId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                memoId.hashCode(),
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(memoId.hashCode(), notification)
        }
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

    companion object {
        const val WORK_TAG = "reminder_worker"

        fun enqueue(context: Context, memoId: String, memoTitle: String, reminderId: String?) {
            val data = workDataOf(
                Constants.EXTRA_MEMO_ID to memoId,
                Constants.EXTRA_MEMO_TITLE to memoTitle,
                Constants.EXTRA_REMINDER_ID to reminderId
            )
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInputData(data)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "reminder_fire_${reminderId ?: memoId}",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun scheduleWithDelay(
            context: Context,
            memoId: String,
            memoTitle: String,
            reminderId: String?,
            delayMillis: Long
        ) {
            val data = workDataOf(
                Constants.EXTRA_MEMO_ID to memoId,
                Constants.EXTRA_MEMO_TITLE to memoTitle,
                Constants.EXTRA_REMINDER_ID to reminderId
            )
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInputData(data)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "reminder_scheduled_${reminderId ?: memoId}",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
