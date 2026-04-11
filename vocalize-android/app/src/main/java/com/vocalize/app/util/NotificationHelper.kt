package com.vocalize.app.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import android.support.v4.media.session.MediaSessionCompat
import com.vocalize.app.MainActivity
import com.vocalize.app.R
import com.vocalize.app.VocalizeApplication
import com.vocalize.app.data.repository.MemoRepository
import com.vocalize.app.service.PlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoRepository: MemoRepository
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private var reminderSoundUri: Uri? = null
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun showReminderNotification(memoId: String, memoTitle: String) {
        notificationManager.notify(memoId.hashCode(), buildReminderNotification(memoId, memoTitle))
    }

    fun buildReminderNotification(memoId: String, memoTitle: String): Notification {
        val notifId = memoId.hashCode()

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(Constants.EXTRA_MEMO_ID, memoId)
            putExtra(Constants.EXTRA_ACTION_PLAY, true)
        }
        val openPending = PendingIntent.getActivity(
            context, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playIntent = Intent(context, PlaybackService::class.java).apply {
            action = Constants.ACTION_PLAY_AUDIO
            putExtra(Constants.EXTRA_MEMO_ID, memoId)
            putExtra(Constants.EXTRA_MEMO_TITLE, memoTitle)
        }
        val playPending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                context, notifId + 4, playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                context, notifId + 4, playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val noteIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = Constants.ACTION_SHOW_NOTE
            putExtra(Constants.EXTRA_MEMO_ID, memoId)
            putExtra(Constants.EXTRA_MEMO_TITLE, memoTitle)
        }
        val notePending = PendingIntent.getBroadcast(
            context, notifId + 5, noteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = Constants.ACTION_SNOOZE
            putExtra(Constants.EXTRA_MEMO_ID, memoId)
            putExtra(Constants.EXTRA_MEMO_TITLE, memoTitle)
        }
        val snoozePending = PendingIntent.getBroadcast(
            context, notifId + 1, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = Constants.ACTION_DISMISS
            putExtra(Constants.EXTRA_MEMO_ID, memoId)
        }
        val dismissPending = PendingIntent.getBroadcast(
            context, notifId + 2, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(Constants.EXTRA_MEMO_ID, memoId)
            putExtra(Constants.EXTRA_FULL_SCREEN_REMINDER, true)
        }
        val fullScreenPending = PendingIntent.getActivity(
            context, notifId + 3, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, VocalizeApplication.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Time to listen: $memoTitle")
            .setContentText("Tap Play to listen without opening the app")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(openPending)
            .addAction(R.drawable.ic_play, "Play", playPending)
            .addAction(R.drawable.ic_mic, "Note", notePending)
            .addAction(R.drawable.ic_alarm, "Snooze", snoozePending)
            .addAction(R.drawable.ic_delete, "Dismiss", dismissPending)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(reminderSoundUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setFullScreenIntent(fullScreenPending, true)
        }

        return builder.build()
    }

    fun updateReminderChannelSound(soundUri: Uri?) {
        reminderSoundUri = soundUri
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Recreate the reminder channel to ensure updated sound settings are applied.
            notificationManager.deleteNotificationChannel(VocalizeApplication.CHANNEL_REMINDERS)
            val soundToUse = soundUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val reminderChannel = NotificationChannel(
                VocalizeApplication.CHANNEL_REMINDERS,
                "Voice Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for scheduled voice memo reminders"
                enableVibration(true)
                enableLights(true)
                setSound(
                    soundToUse,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(reminderChannel)
        }
    }

    fun showReminderNoteNotification(memoId: String, memoTitle: String) {
        val notifId = memoId.hashCode()

        coroutineScope.launch {
            val memo = memoRepository.getMemoById(memoId)
            val noteText = memo?.textNote?.takeIf { it.isNotBlank() } ?: "No notes available."

            val backIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
                action = Constants.ACTION_BACK_TO_REMINDER
                putExtra(Constants.EXTRA_MEMO_ID, memoId)
                putExtra(Constants.EXTRA_MEMO_TITLE, memoTitle)
            }
            val backPending = PendingIntent.getBroadcast(
                context, notifId + 6, backIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val dismissIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
                action = Constants.ACTION_DISMISS
                putExtra(Constants.EXTRA_MEMO_ID, memoId)
            }
            val dismissPending = PendingIntent.getBroadcast(
                context, notifId + 2, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, VocalizeApplication.CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_mic)
                .setContentTitle("Memo notes: $memoTitle")
                .setContentText(noteText)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(0, "Back", backPending)
                .addAction(R.drawable.ic_delete, "Dismiss", dismissPending)
                .setStyle(NotificationCompat.BigTextStyle().bigText(noteText))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            notificationManager.notify(notifId, builder.build())
        }
    }

    fun buildPlaybackNotification(
        memoTitle: String,
        isPlaying: Boolean,
        currentPosition: Int,
        duration: Int,
        mediaSessionToken: MediaSessionCompat.Token?,
        pendingIntent: PendingIntent,
        playPauseIntent: PendingIntent,
        stopIntent: PendingIntent,
        replayIntent: PendingIntent
    ): Notification {
        val builder = NotificationCompat.Builder(context, VocalizeApplication.CHANNEL_PLAYBACK)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(memoTitle)
            .setContentText(if (isPlaying) "Playing..." else "Paused")
            .setContentIntent(pendingIntent)
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) "Pause" else "Play",
                playPauseIntent
            )
            .addAction(R.drawable.ic_mic, "Once more", replayIntent)
            .addAction(R.drawable.ic_delete, "Stop", stopIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (duration > 0) {
            builder.setProgress(duration, currentPosition.coerceIn(0, duration), false)
        }

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
            .setMediaSession(mediaSessionToken)

        return builder
            .setStyle(mediaStyle)
            .build()
    }

    fun postDailyDigestNotification(count: Int) {
        val openIntent = PendingIntent.getActivity(
            context, 9999,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, VocalizeApplication.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Daily Digest")
            .setContentText("You have $count reminder${if (count != 1) "s" else ""} for today")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openIntent)
            .build()
        notificationManager.notify(88888, notification)
    }

    fun cancelNotification(memoId: String) {
        notificationManager.cancel(memoId.hashCode())
    }
}
