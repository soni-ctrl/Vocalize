package com.vocalize.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.vocalize.app.util.CrashReporter
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VocalizeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashReporter.init(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Reminder channel
            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                "Voice Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for scheduled voice memo reminders"
                enableVibration(true)
                enableLights(true)
            }

            // Playback channel
            val playbackChannel = NotificationChannel(
                CHANNEL_PLAYBACK,
                "Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification shown during audio playback"
            }

            manager.createNotificationChannels(listOf(reminderChannel, playbackChannel))
        }
    }

    companion object {
        const val CHANNEL_REMINDERS = "vocalize_reminders"
        const val CHANNEL_PLAYBACK = "vocalize_playback"
    }
}
