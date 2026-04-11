package com.vocalize.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.vocalize.app.dataStore
import com.vocalize.app.util.Constants
import com.vocalize.app.util.CrashReporter
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltAndroidApp
class VocalizeApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        CrashReporter.init(this)
        createNotificationChannels()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val storedToneUri = runBlocking {
                applicationContext.dataStore.data.first()[stringPreferencesKey(Constants.PREFS_NOTIF_SOUND)]
            }?.takeIf { it.isNotBlank() }?.let(Uri::parse)
            val soundUri = storedToneUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            // Reminder channel
            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                "Voice Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for scheduled voice memo reminders"
                enableVibration(true)
                enableLights(true)
                setSound(
                    soundUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            // Playback channel
            val playbackChannel = NotificationChannel(
                CHANNEL_PLAYBACK,
                "Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification shown during audio playback"
            }

            // Crash channel
            val crashChannel = NotificationChannel(
                CHANNEL_CRASH,
                "Crash Reports",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Crash notifications with crash log details"
                enableLights(true)
                enableVibration(true)
            }

            manager.createNotificationChannels(listOf(reminderChannel, playbackChannel, crashChannel))
        }
    }

    companion object {
        const val CHANNEL_REMINDERS = "vocalize_reminders"
        const val CHANNEL_PLAYBACK = "vocalize_playback"
        const val CHANNEL_CRASH = "vocalize_crash"
    }
}
