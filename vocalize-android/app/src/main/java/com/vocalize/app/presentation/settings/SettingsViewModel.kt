package com.vocalize.app.presentation.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vocalize.app.data.repository.MemoRepository
import com.vocalize.app.util.AudioFileManager
import com.vocalize.app.util.BackupManager
import com.vocalize.app.util.Constants
import com.vocalize.app.util.DailyDigestWorker
import com.vocalize.app.util.VoskTranscriber
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private val Context.dataStore by preferencesDataStore(name = "vocalize_prefs")

data class SettingsUiState(
    val isDarkMode: Boolean = true,
    val voskEnabled: Boolean = true,
    val defaultSnoozeMinutes: Int = 10,
    val lastBackupTime: Long = 0L,
    val storageUsedMb: Float = 0f,
    val totalMemos: Int = 0,
    val isBackingUp: Boolean = false,
    val backupStatusMessage: String = "",
    val isSignedIn: Boolean = false,
    val signedInEmail: String = "",
    val isDownloadingModel: Boolean = false,
    val voskModelExists: Boolean = false,
    val snoozeOptions: List<Int> = listOf(5, 10, 15, 30, 60),
    val dailyDigestEnabled: Boolean = false,
    val dailyDigestHour: Int = 8,
    val accentColor: String = "#E53935",
    val snackbarMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoRepository: MemoRepository,
    private val audioFileManager: AudioFileManager,
    private val backupManager: BackupManager,
    private val alarmScheduler: ReminderAlarmScheduler,
    private val voskTranscriber: VoskTranscriber
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
        computeStorage()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            context.dataStore.data.collect { prefs ->
                _uiState.update {
                    it.copy(
                        isDarkMode = prefs[booleanPreferencesKey(Constants.PREFS_DARK_MODE)] ?: true,
                        voskEnabled = prefs[booleanPreferencesKey(Constants.PREFS_VOSK_ENABLED)] ?: true,
                        defaultSnoozeMinutes = (prefs[stringPreferencesKey(Constants.PREFS_DEFAULT_SNOOZE)] ?: "10").toIntOrNull() ?: 10,
                        lastBackupTime = prefs[longPreferencesKey(Constants.PREFS_LAST_BACKUP)] ?: 0L,
                        signedInEmail = prefs[stringPreferencesKey(Constants.PREFS_GOOGLE_ACCOUNT)] ?: "",
                        isSignedIn = (prefs[stringPreferencesKey(Constants.PREFS_GOOGLE_ACCOUNT)] ?: "").isNotBlank(),
                        dailyDigestEnabled = prefs[booleanPreferencesKey("daily_digest_enabled")] ?: false,
                        dailyDigestHour = prefs[intPreferencesKey("daily_digest_hour")] ?: 8,
                        accentColor = prefs[stringPreferencesKey("accent_color")] ?: "#E53935"
                    )
                }
            }
        }
    }

    private fun computeStorage() {
        viewModelScope.launch {
            val recordingsDir = File(context.filesDir, Constants.RECORDINGS_DIR)
            val bytes = recordingsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            val memoCount = memoRepository.getMemoCount()
            val voskModelDir = File(context.filesDir, "${Constants.MODELS_DIR}/${Constants.VOSK_MODEL_DIR}")
            _uiState.update {
                it.copy(
                    storageUsedMb = bytes / 1_048_576f,
                    totalMemos = memoCount,
                    voskModelExists = voskModelDir.exists() && voskModelDir.listFiles()?.isNotEmpty() == true
                )
            }
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[booleanPreferencesKey(Constants.PREFS_DARK_MODE)] = enabled }
        }
    }

    fun setVoskEnabled(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[booleanPreferencesKey(Constants.PREFS_VOSK_ENABLED)] = enabled }
        }
    }

    fun setDefaultSnooze(minutes: Int) {
        viewModelScope.launch {
            context.dataStore.edit { it[stringPreferencesKey(Constants.PREFS_DEFAULT_SNOOZE)] = minutes.toString() }
        }
    }

    fun setDailyDigestEnabled(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[booleanPreferencesKey("daily_digest_enabled")] = enabled }
            if (enabled) {
                DailyDigestWorker.schedule(context, _uiState.value.dailyDigestHour)
            } else {
                DailyDigestWorker.cancel(context)
            }
        }
    }

    fun setDailyDigestHour(hour: Int) {
        viewModelScope.launch {
            context.dataStore.edit { it[intPreferencesKey("daily_digest_hour")] = hour }
            if (_uiState.value.dailyDigestEnabled) {
                DailyDigestWorker.schedule(context, hour)
            }
        }
    }

    fun setAccentColor(colorHex: String) {
        viewModelScope.launch {
            context.dataStore.edit { it[stringPreferencesKey("accent_color")] = colorHex }
        }
    }

    fun performBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBackingUp = true, backupStatusMessage = "Starting backup...") }
            val dbFile = context.getDatabasePath(Constants.DB_NAME)
            val recordingsDir = File(context.filesDir, Constants.RECORDINGS_DIR)
            val success = backupManager.backup(dbFile, recordingsDir) { msg ->
                _uiState.update { it.copy(backupStatusMessage = msg) }
            }
            val now = System.currentTimeMillis()
            if (success) {
                context.dataStore.edit { it[longPreferencesKey(Constants.PREFS_LAST_BACKUP)] = now }
                _uiState.update { it.copy(isBackingUp = false, backupStatusMessage = "Backup successful!", lastBackupTime = now) }
            } else {
                _uiState.update { it.copy(isBackingUp = false, backupStatusMessage = "Backup failed. Sign in to Google first.") }
            }
        }
    }

    fun performRestore() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBackingUp = true, backupStatusMessage = "Restoring from Drive...") }
            val recordingsDir = File(context.filesDir, Constants.RECORDINGS_DIR)
            val success = backupManager.restore(recordingsDir) { msg ->
                _uiState.update { it.copy(backupStatusMessage = msg) }
            }
            _uiState.update {
                it.copy(
                    isBackingUp = false,
                    backupStatusMessage = if (success) "Restore complete! Restart app." else "Restore failed."
                )
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            context.cacheDir.deleteRecursively()
            computeStorage()
            showSnackbar("Cache cleared")
        }
    }

    fun deleteVoskModel() {
        viewModelScope.launch {
            val modelDir = File(context.filesDir, Constants.MODELS_DIR)
            modelDir.deleteRecursively()
            _uiState.update { it.copy(voskModelExists = false) }
            showSnackbar("Voice model deleted")
        }
    }

    fun downloadVoskModel() {
        if (_uiState.value.isDownloadingModel) return
        _uiState.update { it.copy(isDownloadingModel = true) }
        voskTranscriber.downloadModel(
            onProgress = { /* progress not used */ },
            onComplete = { success ->
                _uiState.update {
                    it.copy(
                        isDownloadingModel = false,
                        voskModelExists = success
                    )
                }
                showSnackbar(if (success) "Voice model downloaded" else "Download failed")
                if (success) computeStorage()
            }
        )
    }

    fun deleteAllData() {
        viewModelScope.launch {
            val all = memoRepository.getAllMemos().first()
            all.forEach { memo ->
                if (memo.hasReminder) alarmScheduler.cancelReminder(memo.id)
                audioFileManager.deleteAudioFile(memo.filePath)
            }
            memoRepository.deleteAllMemos()
            context.dataStore.edit { it.clear() }
            computeStorage()
            showSnackbar("All data deleted")
        }
    }

    fun signOut() {
        viewModelScope.launch {
            context.dataStore.edit { it[stringPreferencesKey(Constants.PREFS_GOOGLE_ACCOUNT)] = "" }
        }
    }

    fun showSnackbar(msg: String) = _uiState.update { it.copy(snackbarMessage = msg) }
    fun clearSnackbar() = _uiState.update { it.copy(snackbarMessage = null) }
}
