package com.vocalize.app.presentation.detail

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.vocalize.app.data.local.entity.RepeatType
import com.vocalize.app.presentation.components.MemoCard
import com.vocalize.app.presentation.components.WaveformView
import com.vocalize.app.presentation.components.ReminderBottomSheet
import com.vocalize.app.presentation.components.AddToPlaylistBottomSheet
import com.vocalize.app.presentation.components.SetCategoryBottomSheet
import com.vocalize.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoDetailScreen(
    memoId: String,
    onNavigateBack: () -> Unit,
    viewModel: MemoDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val memo = uiState.memo
    val playbackState = uiState.playbackState
    val isThisMemoPlaying = playbackState.currentMemoId == memoId && playbackState.isPlaying
    val clipboardManager = LocalClipboardManager.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

    // Rotate play button on start
    val playButtonRotation by animateFloatAsState(
        targetValue = if (isThisMemoPlaying) 360f else 0f,
        animationSpec = tween(400),
        label = "play_rotate"
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isEditingTitle) {
                        OutlinedTextField(
                            value = uiState.editedTitle,
                            onValueChange = viewModel::updateEditedTitle,
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = memo?.title?.ifBlank { "Voice Memo" } ?: "Loading...",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { viewModel.startEditTitle() }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.stopPlayback(); onNavigateBack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (uiState.isEditingTitle) {
                        IconButton(onClick = viewModel::saveTitle) {
                            Icon(Icons.Default.Check, "Save", tint = VocalizeGreen)
                        }
                        IconButton(onClick = viewModel::cancelEditTitle) {
                            Icon(Icons.Default.Close, "Cancel")
                        }
                    } else {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, "More")
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Add to playlist") },
                                    leadingIcon = { Icon(Icons.Default.QueueMusic, null) },
                                    onClick = { showMenu = false; viewModel.showPlaylistSheet() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Set category") },
                                    leadingIcon = { Icon(Icons.Default.Label, null) },
                                    onClick = { showMenu = false; viewModel.showCategorySheet() }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        if (memo == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VocalizeRed)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(24.dp))

            // Album art / waveform display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 24.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                VocalizeRed.copy(0.15f),
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                WaveformView(
                    amplitudes = List(50) {
                        (kotlin.math.sin(it * 0.4f).toFloat() + 1f) / 2f * 0.6f + 0.1f
                    },
                    isRecording = false,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
                // Center play button
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .rotate(playButtonRotation)
                        .background(VocalizeRed, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = viewModel::playPause,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            imageVector = if (isThisMemoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Playback seekbar
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                val currentPosition = if (playbackState.currentMemoId == memoId) playbackState.currentPosition else 0
                val duration = if (playbackState.currentMemoId == memoId && playbackState.duration > 0)
                    playbackState.duration else memo.duration.toInt()

                if (duration > 0) {
                    Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { viewModel.seekTo(it.toInt()) },
                        valueRange = 0f..duration.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = VocalizeRed,
                            activeTrackColor = VocalizeRed,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            formatDuration(currentPosition.toLong()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            formatDuration(duration.toLong()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Speed selector
                Text(
                    "Speed: ${playbackState.playbackSpeed}x",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    speedOptions.forEach { speed ->
                        FilterChip(
                            selected = playbackState.playbackSpeed == speed,
                            onClick = { viewModel.setSpeed(speed) },
                            label = { Text("${speed}x", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(20.dp))

            // Reminder section
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Alarm, null, tint = VocalizeOrange, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Reminder", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = viewModel::showReminderSheet) {
                        Text(if (memo.hasReminder) "Edit" else "Set", color = VocalizeRed)
                    }
                }

                AnimatedVisibility(
                    visible = memo.hasReminder && memo.reminderTime != null,
                    enter = slideInVertically() + fadeIn()
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = VocalizeOrange.copy(0.1f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Alarm, null, tint = VocalizeOrange)
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                memo.reminderTime?.let {
                                    Text(
                                        formatDateTime(it),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = VocalizeOrange
                                    )
                                }
                                Text(
                                    memo.repeatType.name.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VocalizeOrange.copy(0.7f)
                                )
                            }
                            IconButton(onClick = viewModel::clearReminder, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, "Clear", tint = VocalizeOrange, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(20.dp))

            // Notes section
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Note, null, tint = VocalizeAccentBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Note", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    if (!uiState.isEditingNote) {
                        TextButton(onClick = viewModel::startEditNote) {
                            Text("Edit", color = VocalizeRed)
                        }
                    }
                }

                if (uiState.isEditingNote) {
                    OutlinedTextField(
                        value = uiState.editedNote,
                        onValueChange = viewModel::updateEditedNote,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VocalizeRed)
                    )
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = viewModel::cancelEditNote) { Text("Cancel") }
                        TextButton(onClick = viewModel::saveNote) { Text("Save", color = VocalizeRed) }
                    }
                } else {
                    Text(
                        text = memo.textNote.ifBlank { "No note added" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (memo.textNote.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.clickable { viewModel.startEditNote() }
                    )
                }
            }

            // Transcription section
            if (memo.transcription.isNotBlank() || memo.isTranscribing) {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(20.dp))
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TextFields, null, tint = VocalizePurple, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Transcription", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        if (memo.transcription.isNotBlank()) {
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(memo.transcription))
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    if (memo.isTranscribing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = VocalizePurple)
                            Spacer(Modifier.width(8.dp))
                            Text("Transcribing...", style = MaterialTheme.typography.bodySmall, color = VocalizePurple)
                        }
                    } else {
                        Text(
                            text = memo.transcription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    // Reminder bottom sheet
    if (uiState.showReminderSheet) {
        ReminderBottomSheet(
            currentReminderTime = memo?.reminderTime,
            currentRepeatType = memo?.repeatType ?: RepeatType.NONE,
            onDismiss = viewModel::hideReminderSheet,
            onSave = { time, repeat, days -> viewModel.setReminder(time, repeat, days) }
        )
    }

    // Add to playlist sheet
    if (uiState.showPlaylistSheet) {
        AddToPlaylistBottomSheet(
            playlists = uiState.playlists,
            onDismiss = viewModel::hidePlaylistSheet,
            onSelect = { viewModel.addToPlaylist(it) }
        )
    }

    // Set category sheet
    if (uiState.showCategorySheet) {
        SetCategoryBottomSheet(
            categories = uiState.categories,
            currentCategoryId = memo?.categoryId,
            onDismiss = viewModel::hideCategorySheet,
            onSelect = viewModel::updateCategory
        )
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Memo") },
            text = { Text("Are you sure? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteMemo { onNavigateBack() }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

private fun formatDuration(ms: Long): String {
    val sec = ms / 1000
    return "${(sec / 60).toString().padStart(2, '0')}:${(sec % 60).toString().padStart(2, '0')}"
}

private fun formatDateTime(ts: Long): String {
    val fmt = java.text.SimpleDateFormat("MMM d, yyyy h:mm a", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(ts))
}
