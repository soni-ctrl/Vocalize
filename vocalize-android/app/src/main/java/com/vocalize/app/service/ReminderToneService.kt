package com.vocalize.app.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vocalize.app.dataStore
import com.vocalize.app.util.Constants
import com.vocalize.app.data.repository.MemoRepository
import com.vocalize.app.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ReminderToneService : Service() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var memoRepository: MemoRepository

    private var mediaPlayer: MediaPlayer? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_REMINDER -> startReminder(intent)
            ACTION_SHOW_NOTE -> showNoteNotification(intent)
            ACTION_BACK_TO_REMINDER -> showReminderNotification(intent)
            ACTION_STOP_REMINDER -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startReminder(intent: Intent) {
        val memoId = intent.getStringExtra(Constants.EXTRA_MEMO_ID) ?: "test_reminder"
        val memoTitle = intent.getStringExtra(Constants.EXTRA_MEMO_TITLE) ?: "Reminder tone test"
        val reminderId = intent.getStringExtra(Constants.EXTRA_REMINDER_ID)
        val notification = notificationHelper.buildReminderNotification(memoId, memoTitle, reminderId)
        val notificationId = memoId.hashCode()

        startForegroundCompat(notificationId, notification)

        serviceScope.launch {
            playReminderTone()
        }
    }

    private fun showNoteNotification(intent: Intent) {
        stopTone()
        val memoId = intent.getStringExtra(Constants.EXTRA_MEMO_ID) ?: "test_reminder"
        val memoTitle = intent.getStringExtra(Constants.EXTRA_MEMO_TITLE) ?: "Reminder tone test"
        val reminderId = intent.getStringExtra(Constants.EXTRA_REMINDER_ID)

        serviceScope.launch {
            val memo = memoRepository.getMemoById(memoId)
            val noteText = memo?.textNote?.takeIf { it.isNotBlank() } ?: "No notes available."
            val notification = notificationHelper.buildReminderNoteNotification(memoId, memoTitle, noteText, reminderId)
            startForegroundCompat(memoId.hashCode(), notification)
        }
    }

    private fun showReminderNotification(intent: Intent) {
        val memoId = intent.getStringExtra(Constants.EXTRA_MEMO_ID) ?: "test_reminder"
        val memoTitle = intent.getStringExtra(Constants.EXTRA_MEMO_TITLE) ?: "Reminder tone test"
        val reminderId = intent.getStringExtra(Constants.EXTRA_REMINDER_ID)
        val notification = notificationHelper.buildReminderNotification(memoId, memoTitle, reminderId)
        startForegroundCompat(memoId.hashCode(), notification)
    }

    /**
     * On Android 14+, the service is declared with foregroundServiceType="alarm" in the manifest.
     * We must pass FOREGROUND_SERVICE_TYPE_ALARM so Android grants alarm Doze-mode exemptions.
     * On older versions the overload without type is used (alarm type doesn't exist pre-14).
     */
    private fun startForegroundCompat(id: Int, notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_ALARM)
        } else {
            startForeground(id, notification)
        }
    }

    private suspend fun playReminderTone() {
        val prefs = applicationContext.dataStore.data.first()
        val toneUri = prefs[stringPreferencesKey(Constants.PREFS_NOTIF_SOUND)]?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        val volume = prefs[intPreferencesKey(Constants.PREFS_REMINDER_VOLUME)] ?: 100
        val soundUri = toneUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        playMedia(soundUri, volume / 100f)
    }

    private fun playMedia(uri: Uri, volume: Float) {
        try {
            stopTone()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(applicationContext, uri)
                setVolume(volume, volume)
                isLooping = true
                setOnErrorListener { _, _, _ ->
                    stopSelf()
                    true
                }
                prepare()
                start()
            }
        } catch (exception: Exception) {
            exception.printStackTrace()
            stopSelf()
        }
    }

    private fun stopTone() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START_REMINDER = "com.vocalize.app.ACTION_START_REMINDER"
        const val ACTION_SHOW_NOTE = "com.vocalize.app.ACTION_SHOW_NOTE"
        const val ACTION_BACK_TO_REMINDER = "com.vocalize.app.ACTION_BACK_TO_REMINDER"
        const val ACTION_STOP_REMINDER = "com.vocalize.app.ACTION_STOP_REMINDER"
        const val REMINDER_NOTIFICATION_ID = 897654
    }
}
