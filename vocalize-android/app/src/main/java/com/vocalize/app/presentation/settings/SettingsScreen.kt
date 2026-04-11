package com.vocalize.app.presentation.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.vocalize.app.presentation.theme.*
import com.vocalize.app.util.PermissionsHelper
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCategories: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showSnoozeDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showDigestHourDialog by remember { mutableStateOf(false) }
    var showToneListDialog by remember { mutableStateOf(false) }
    var showToneStatusDialog by remember { mutableStateOf(false) }
    var showExportConfirmDialog by remember { mutableStateOf(false) }
    var exportFolderUri by remember { mutableStateOf<Uri?>(null) }
    var allPermissionsGranted by remember { mutableStateOf(PermissionsHelper.areAllRequiredPermissionsGranted(context)) }
    var allFilesAccessGranted by remember { mutableStateOf(PermissionsHelper.hasManageExternalStoragePermission(context)) }
    var previewToneUri by remember { mutableStateOf<Uri?>(null) }
    var isPreviewPlaying by remember { mutableStateOf(false) }
    val toneListScrollState = rememberScrollState()
    val exactAlarmPermissionGranted = PermissionsHelper.hasScheduleExactAlarmPermission(context)
    val mediaPlayer = remember { MediaPlayer() }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        allPermissionsGranted = result.values.all { it }
    }
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            exportFolderUri = uri
            showExportConfirmDialog = true
        }
    }
    val toneFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.setReminderToneFolderUri(uri)
        }
    }
    val backupFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.performImportBackup(it, context) }
    }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    DisposableEffect(mediaPlayer) {
        onDispose {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            mediaPlayer.release()
        }
    }

    fun playTone(uri: Uri) {
        try {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            mediaPlayer.reset()
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mediaPlayer.setDataSource(context, uri)
            mediaPlayer.setVolume(uiState.reminderToneVolume / 100f, uiState.reminderToneVolume / 100f)
            mediaPlayer.setOnCompletionListener { isPreviewPlaying = false }
            mediaPlayer.prepare()
            mediaPlayer.start()
            previewToneUri = uri
            isPreviewPlaying = true
        } catch (error: Exception) {
            error.printStackTrace()
        }
    }

    fun stopPreview() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
        isPreviewPlaying = false
        previewToneUri = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Account & Backup ──────────────────────────────────────────
            SettingsSectionHeader("Account & Backup", Icons.Default.Cloud)

            SettingsCard {
                Text(
                    "Google Drive backup is disabled.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // ── Voice-to-Text ──────────────────────────────────────────
            SettingsSectionHeader("Voice-to-Text (Offline)", Icons.Default.RecordVoiceOver)

            SettingsCard {
                SettingsToggleRow(
                    icon = Icons.Default.RecordVoiceOver,
                    iconTint = VocalizeGreen,
                    title = "Auto-transcribe recordings",
                    subtitle = "Uses Vosk AI (offline, ~40MB model)",
                    checked = uiState.voskEnabled,
                    onCheckedChange = viewModel::setVoskEnabled
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = if (uiState.voskModelExists) Icons.Default.Delete else Icons.Default.Download,
                    iconTint = if (uiState.voskModelExists) MaterialTheme.colorScheme.error else VocalizeAccentBlue,
                    title = if (uiState.voskModelExists) "Delete Vosk model" else if (uiState.isDownloadingModel) "Downloading model..." else "Download Vosk model",
                    subtitle = if (uiState.voskModelExists) "Free up ~40MB" else "Required for offline transcription (~40MB)",
                    onClick = {
                        if (uiState.voskModelExists) viewModel.deleteVoskModel()
                        else viewModel.downloadVoskModel()
                    },
                    enabled = !uiState.isDownloadingModel
                )
            }

            // ── Appearance ──────────────────────────────────────────
            SettingsSectionHeader("Appearance", Icons.Default.Palette)

            SettingsCard {
                SettingsToggleRow(
                    icon = Icons.Default.DarkMode,
                    iconTint = VocalizePurple,
                    title = "Dark mode",
                    subtitle = "App-wide dark theme",
                    checked = uiState.isDarkMode,
                    onCheckedChange = viewModel::setDarkMode
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                // Accent color row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingsIconBox(Icons.Default.Palette, VocalizeRed)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Accent color", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text("Choose app highlight color", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("#E53935" to "Red", "#1E88E5" to "Blue", "#43A047" to "Green", "#FB8C00" to "Orange", "#8E24AA" to "Purple").forEach { (hex, name) ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .clickable { viewModel.setAccentColor(hex) }
                                    .then(if (uiState.accentColor == hex) Modifier.border(2.dp, Color.White, CircleShape) else Modifier),
                                contentAlignment = Alignment.Center
                            ) {
                                if (uiState.accentColor == hex) {
                                    Icon(Icons.Default.Check, name, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }

            // ── Reminders ──────────────────────────────────────────
            SettingsSectionHeader("Reminder Defaults", Icons.Default.Alarm)

            SettingsCard {
                SettingsActionRow(
                    icon = Icons.Default.Snooze,
                    iconTint = VocalizeOrange,
                    title = "Default snooze time",
                    subtitle = "${uiState.defaultSnoozeMinutes} minutes",
                    onClick = { showSnoozeDialog = true }
                )
            }

            // ── Notifications ──────────────────────────────────────────
            SettingsSectionHeader("Notifications", Icons.Default.Notifications)

            SettingsCard {
                SettingsToggleRow(
                    icon = Icons.Default.WbSunny,
                    iconTint = VocalizeOrange,
                    title = "Daily digest",
                    subtitle = "Morning reminder of today's scheduled memos",
                    checked = uiState.dailyDigestEnabled,
                    onCheckedChange = viewModel::setDailyDigestEnabled
                )
                AnimatedVisibility(visible = uiState.dailyDigestEnabled) {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsActionRow(
                            icon = Icons.Default.Schedule,
                            iconTint = VocalizeAccentBlue,
                            title = "Digest time",
                            subtitle = "${uiState.dailyDigestHour}:00 ${if (uiState.dailyDigestHour < 12) "AM" else "PM"}",
                            onClick = { showDigestHourDialog = true }
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Default.Folder,
                    iconTint = VocalizeAccentBlue,
                    title = "Reminder tone folder",
                    subtitle = uiState.reminderToneFolderUri?.let { "Folder section selected" } ?: "Default: ${uiState.reminderToneFolderPath}",
                    onClick = { toneFolderLauncher.launch(null) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Default.MusicNote,
                    iconTint = VocalizeGreen,
                    title = "Reminder tone",
                    subtitle = uiState.reminderToneFileName,
                    onClick = { showToneListDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Default.PlayArrow,
                    iconTint = VocalizeGreen,
                    title = "Test reminder tone",
                    subtitle = "Schedules a test reminder in 3 seconds",
                    onClick = { viewModel.testReminderTone() }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Reminder volume: ${uiState.reminderToneVolume}%",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
                )
                Slider(
                    value = uiState.reminderToneVolume / 100f,
                    onValueChange = { viewModel.setReminderVolume((it * 100).toInt()) },
                    valueRange = 0f..1f,
                    steps = 4,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // ── Reminder Tone Status ──────────────────────────────
            SettingsSectionHeader("Tone status", Icons.Default.Info)

            SettingsCard {
                SettingsActionRow(
                    icon = Icons.Default.Info,
                    iconTint = VocalizeAccentBlue,
                    title = "View reminder tone details",
                    subtitle = "Tap to view tone folder, selected tone, permissions and volume",
                    onClick = { showToneStatusDialog = true }
                )
            }

            // ── Categories ──────────────────────────────────────────
            SettingsSectionHeader("Organisation", Icons.Default.Label)

            SettingsCard {
                SettingsActionRow(
                    icon = Icons.Default.Label,
                    iconTint = VocalizePurple,
                    title = "Manage categories",
                    subtitle = "Create, edit, and delete memo categories",
                    onClick = onNavigateToCategories
                )
            }

            // ── Permissions ──────────────────────────────────────────
            SettingsSectionHeader("Permissions", Icons.Default.PrivacyTip)

            SettingsCard {
                SettingsActionRow(
                    icon = Icons.Default.PrivacyTip,
                    iconTint = VocalizeAccentBlue,
                    title = "Grant app permissions",
                    subtitle = if (allPermissionsGranted) "Microphone, notifications, and media permissions granted"
                    else "Allow microphone, notifications, and audio permissions",
                    onClick = { permissionLauncher.launch(PermissionsHelper.getRequiredPermissions()) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Default.Storage,
                    iconTint = VocalizeAccentBlue,
                    title = "Grant storage permissions",
                    subtitle = "Allow backup export/import and storage access",
                    onClick = { permissionLauncher.launch(PermissionsHelper.getRequiredPermissions()) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Default.FileOpen,
                    iconTint = VocalizeAccentBlue,
                    title = "Grant all files access",
                    subtitle = if (allFilesAccessGranted) "All files access granted" else "Allow access to tone folders and external audio",
                    onClick = { PermissionsHelper.openManageAllFilesAccessSettings(context) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Default.Schedule,
                    iconTint = VocalizeAccentBlue,
                    title = "Open alarm settings",
                    subtitle = "Grant exact alarm permission for reminders",
                    onClick = { PermissionsHelper.openAlarmSettings(context) }
                )
            }

            // ── Data Management ──────────────────────────────────────────
            SettingsSectionHeader("Data Management", Icons.Default.ManageAccounts)

            SettingsCard {
                SettingsActionRow(
                    icon = Icons.Default.DeleteForever,
                    iconTint = MaterialTheme.colorScheme.error,
                    title = "Delete all data",
                    subtitle = "Permanently removes all memos and settings",
                    onClick = { showDeleteAllDialog = true }
                )
            }

            // ── Backup / Export ──────────────────────────────────────────
            SettingsSectionHeader("Backup", Icons.Default.FolderOpen)

            SettingsCard {
                SettingsActionRow(
                    icon = Icons.Default.UploadFile,
                    iconTint = VocalizeGreen,
                    title = "Export special .voc backup",
                    subtitle = "Choose a folder and create a full app backup",
                    onClick = { folderLauncher.launch(null) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Default.FileOpen,
                    iconTint = VocalizeAccentBlue,
                    title = "Import special .voc backup",
                    subtitle = "Select a .voc file to restore all app content",
                    onClick = { backupFileLauncher.launch(arrayOf("*/*")) }
                )
            }

            // ── Storage ──────────────────────────────────────────
            SettingsSectionHeader("Storage", Icons.Default.Storage)

            SettingsCard {
                SettingsInfoRow(
                    icon = Icons.Default.GraphicEq,
                    iconTint = VocalizeAccentBlue,
                    title = "Recordings",
                    subtitle = "${uiState.totalMemos} memos · ${String.format("%.1f", uiState.storageUsedMb)} MB used"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Default.Delete,
                    iconTint = MaterialTheme.colorScheme.error,
                    title = "Clear cache",
                    subtitle = "Frees temporary files",
                    onClick = { showClearCacheDialog = true }
                )
            }

            // ── About ──────────────────────────────────────────
            SettingsSectionHeader("About", Icons.Default.Info)

            SettingsCard {
                SettingsInfoRow(
                    icon = Icons.Default.MicNone,
                    iconTint = VocalizeRed,
                    title = "Vocalize",
                    subtitle = "Version 1.0 · com.vocalize.app"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Default.OpenInNew,
                    iconTint = VocalizeAccentBlue,
                    title = "Open source repository",
                    subtitle = "Hosted on GitHub: neet-ctrl/Vocalize",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/neet-ctrl/Vocalize"))
                        context.startActivity(intent)
                    }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedOwnerBadge("Shakti Kumar")
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }

    if (showExportConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExportConfirmDialog = false },
            title = { Text("Create .voc backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Create a full backup file with audio, notes, reminders, categories, tags, and playlists.")
                    Text(
                        text = exportFolderUri?.path ?: "Selected folder",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    exportFolderUri?.let {
                        viewModel.performExportBackup(it, context)
                    }
                    showExportConfirmDialog = false
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showToneListDialog) {
        AlertDialog(
            onDismissRequest = {
                showToneListDialog = false
                stopPreview()
            },
            title = { Text("Select reminder tone") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(toneListScrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.availableReminderTones.isEmpty()) {
                        Text("No audio files found in the selected folder or default Alarms folder.")
                    } else {
                        uiState.availableReminderTones.forEach { tone ->
                            val toneUri = Uri.parse(tone.uri)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { previewToneUri = toneUri }
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(tone.name, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "${tone.durationMs / 1000 / 60}:${String.format("%02d", (tone.durationMs / 1000) % 60)} • ${when {
                                            tone.sizeBytes >= 1_048_576 -> "%.1f MB".format(tone.sizeBytes / 1_048_576f)
                                            tone.sizeBytes >= 1024 -> "%.1f KB".format(tone.sizeBytes / 1024f)
                                            else -> "${tone.sizeBytes} B"
                                        }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = {
                                            if (isPreviewPlaying && previewToneUri == toneUri) {
                                                stopPreview()
                                            } else {
                                                playTone(toneUri)
                                            }
                                        }) {
                                            Text(if (isPreviewPlaying && previewToneUri == toneUri) "Stop" else "Play")
                                        }
                                        Button(onClick = {
                                            viewModel.setReminderTone(toneUri, tone.name)
                                            showToneListDialog = false
                                            stopPreview()
                                        }) {
                                            Text("Set tone")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showToneListDialog = false
                    stopPreview()
                }) {
                    Text("Close")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showToneListDialog = false
                    stopPreview()
                }) { Text("Cancel") }
            }
        )
    }

    if (showToneStatusDialog) {
        val toneStatusIssue = when {
            !allFilesAccessGranted && uiState.reminderToneFolderUri != null ->
                "Custom tone folder selected but all-files access is not granted."
            uiState.reminderToneFileUri.isNullOrBlank() ->
                "No reminder tone is selected yet. The app will use the default notification sound."
            uiState.availableReminderTones.isEmpty() ->
                "The selected folder contains no audio files. Choose another folder."
            !exactAlarmPermissionGranted ->
                "Exact alarm permission is not granted; reminder delivery may be delayed."
            else ->
                "Tone configured correctly. If sound still does not play, verify battery optimization and notification settings."
        }

        AlertDialog(
            onDismissRequest = { showToneStatusDialog = false },
            title = { Text("Reminder tone status") },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Status details", style = MaterialTheme.typography.bodyLarge)
                    Text(toneStatusIssue, style = MaterialTheme.typography.bodyMedium)
                    SettingsInfoRow(
                        icon = Icons.Default.MusicNote,
                        iconTint = VocalizeGreen,
                        title = "Selected tone",
                        subtitle = uiState.reminderToneFileName.ifBlank { "Default tone" }
                    )
                    SettingsInfoRow(
                        icon = Icons.Default.Folder,
                        iconTint = VocalizeAccentBlue,
                        title = "Tone folder",
                        subtitle = uiState.reminderToneFolderUri?.let { "Custom folder selected" } ?: uiState.reminderToneFolderPath
                    )
                    SettingsInfoRow(
                        icon = Icons.Default.VolumeUp,
                        iconTint = VocalizeOrange,
                        title = "Volume",
                        subtitle = "${uiState.reminderToneVolume}%"
                    )
                    SettingsInfoRow(
                        icon = Icons.Default.CheckCircle,
                        iconTint = if (allPermissionsGranted) VocalizeGreen else MaterialTheme.colorScheme.error,
                        title = "Core permissions",
                        subtitle = if (allPermissionsGranted) "Audio + notification permissions granted" else "Missing microphone or notification permission"
                    )
                    SettingsInfoRow(
                        icon = Icons.Default.Security,
                        iconTint = if (exactAlarmPermissionGranted) VocalizeGreen else MaterialTheme.colorScheme.error,
                        title = "Alarm permission",
                        subtitle = if (exactAlarmPermissionGranted) "Exact alarm granted" else "Needs exact alarm permission"
                    )
                    SettingsInfoRow(
                        icon = Icons.Default.Storage,
                        iconTint = if (allFilesAccessGranted) VocalizeGreen else MaterialTheme.colorScheme.error,
                        title = "Storage access",
                        subtitle = if (allFilesAccessGranted) "All files access granted" else "Needs all files permission"
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showToneStatusDialog = false }) { Text("Close") }
            }
        )
    }

    // Snooze dialog
    if (showSnoozeDialog) {
        var tempSnooze by remember { mutableIntStateOf(uiState.defaultSnoozeMinutes) }
        var customSnooze by remember { mutableStateOf(uiState.defaultSnoozeMinutes.toString()) }
        var customSelected by remember { mutableStateOf(false) }
        val customMinutes = customSnooze.toIntOrNull()
        val saveEnabled = !customSelected || (customMinutes != null && customMinutes > 0)

        AlertDialog(
            onDismissRequest = { showSnoozeDialog = false },
            title = { Text("Default Snooze") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.snoozeOptions.forEach { min ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    customSelected = false
                                    tempSnooze = min
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = !customSelected && tempSnooze == min, onClick = {
                                customSelected = false
                                tempSnooze = min
                            })
                            Spacer(Modifier.width(8.dp))
                            Text("$min minutes")
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                customSelected = true
                                customMinutes?.let { tempSnooze = it }
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = customSelected, onClick = { customSelected = true })
                        Spacer(Modifier.width(8.dp))
                        Text("Custom duration", modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = customSnooze,
                            onValueChange = { value ->
                                customSnooze = value.filter { it.isDigit() }
                                customSelected = true
                                value.toIntOrNull()?.let { tempSnooze = it }
                            },
                            modifier = Modifier.width(100.dp),
                            singleLine = true,
                            label = { Text("Minutes")
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VocalizeRed,
                                focusedLabelColor = VocalizeRed
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val selected = if (customSelected) customMinutes ?: tempSnooze else tempSnooze
                        viewModel.setDefaultSnooze(selected)
                        showSnoozeDialog = false
                    },
                    enabled = saveEnabled
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSnoozeDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Clear cache dialog
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear Cache") },
            text = { Text("This will delete temporary files. Your recordings will not be affected.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearCache(); showClearCacheDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearCacheDialog = false }) { Text("Cancel") } }
        )
    }

    // Delete all data dialog
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete All Data") },
            text = { Text("This will permanently delete ALL memos, audio files, and settings. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteAllData(); showDeleteAllDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete Everything") }
            },
            dismissButton = { TextButton(onClick = { showDeleteAllDialog = false }) { Text("Cancel") } }
        )
    }

    // Digest hour picker dialog
    if (showDigestHourDialog) {
        var tempHour by remember { mutableIntStateOf(uiState.dailyDigestHour) }
        AlertDialog(
            onDismissRequest = { showDigestHourDialog = false },
            title = { Text("Daily Digest Time") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Send digest at:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    listOf(6, 7, 8, 9, 10, 12).forEach { hour ->
                        val label = when {
                            hour == 12 -> "12:00 PM"
                            hour < 12 -> "$hour:00 AM"
                            else -> "${hour - 12}:00 PM"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { tempHour = hour }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = tempHour == hour, onClick = { tempHour = hour })
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.setDailyDigestHour(tempHour); showDigestHourDialog = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showDigestHourDialog = false }) { Text("Cancel") } }
        )
    }
}

// ── Settings UI components ──────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconBox(icon, iconTint)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VocalizeRed)
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconBox(icon, if (enabled) iconTint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.Default.ChevronRight,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.7f else 0.3f)
        )
    }
}

@Composable
private fun SettingsInfoRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconBox(icon, iconTint)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AnimatedOwnerBadge(name: String) {
    val transition = rememberInfiniteTransition()
    val scale by transition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        )
    )
    val color by transition.animateColor(
        initialValue = VocalizePurple,
        targetValue = VocalizeAccentBlue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scale, scaleY = scale),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Star, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "Shakti Kumar — Owner",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@Composable
private fun SettingsIconBox(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

private fun formatTs(ts: Long): String =
    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(ts))
