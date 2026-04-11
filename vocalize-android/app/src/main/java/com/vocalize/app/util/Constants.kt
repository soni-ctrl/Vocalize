package com.vocalize.app.util

object Constants {
    const val DB_NAME = "vocalize_database"
    const val RECORDINGS_DIR = "recordings"
    const val MODELS_DIR = "models"
    const val VOSK_MODEL_DIR = "vosk-model-small-en-us-0.15"
    const val VOSK_MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"

    // Notification actions
    const val ACTION_PLAY = "com.vocalize.app.ACTION_PLAY"
    const val ACTION_REMINDER_PLAY = "com.vocalize.app.ACTION_REMINDER_PLAY"
    const val ACTION_PLAY_AUDIO = "com.vocalize.app.ACTION_PLAY_AUDIO"
    const val ACTION_REPLAY_AUDIO = "com.vocalize.app.ACTION_REPLAY_AUDIO"
    const val ACTION_SHOW_NOTE = "com.vocalize.app.ACTION_SHOW_NOTE"
    const val ACTION_BACK_TO_REMINDER = "com.vocalize.app.ACTION_BACK_TO_REMINDER"
    const val ACTION_SNOOZE = "com.vocalize.app.ACTION_SNOOZE"
    const val ACTION_DISMISS = "com.vocalize.app.ACTION_DISMISS"
    const val EXTRA_MEMO_ID = "memo_id"
    const val EXTRA_MEMO_TITLE = "memo_title"
    const val EXTRA_ACTION_PLAY = "action_play"
    const val EXTRA_FULL_SCREEN_REMINDER = "full_screen_reminder"
    const val SNOOZE_DURATION_MS = 10 * 60 * 1000L // 10 minutes default

    // WorkManager tags
    const val TRANSCRIPTION_WORK_TAG = "transcription_work"
    const val REMINDER_WORK_TAG = "reminder_work"
    const val BACKUP_WORK_TAG = "backup_work"

    // DataStore keys
    const val PREFS_DARK_MODE = "dark_mode"
    const val PREFS_ACCENT_COLOR = "accent_color"
    const val PREFS_VOSK_ENABLED = "vosk_enabled"
    const val PREFS_DEFAULT_SNOOZE = "default_snooze"
    const val PREFS_NOTIF_SOUND = "notification_sound"
    const val PREFS_REMINDER_TONE_FOLDER_URI = "reminder_tone_folder_uri"
    const val PREFS_REMINDER_TONE_NAME = "reminder_tone_name"
    const val PREFS_REMINDER_VOLUME = "reminder_volume"
    const val PREFS_LAST_BACKUP = "last_backup_time"
    const val PREFS_GOOGLE_ACCOUNT = "google_account"
    const val DEFAULT_REMINDER_TONE_FOLDER = "/storage/emulated/0/Alarms"

    // Default categories
    val DEFAULT_CATEGORIES = listOf(
        Triple("cat_work", "Work", "#2196F3"),
        Triple("cat_personal", "Personal", "#9C27B0"),
        Triple("cat_ideas", "Ideas", "#FF9800"),
        Triple("cat_shopping", "Shopping", "#4CAF50"),
        Triple("cat_health", "Health", "#F44336")
    )
}
