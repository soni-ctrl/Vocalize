package com.vocalize.app.presentation.playlist

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.vocalize.app.data.local.entity.MemoEntity
import com.vocalize.app.presentation.components.MemoCard
import com.vocalize.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlistId: String,
    onNavigateBack: () -> Unit,
    onNavigateToMemoDetail: (String) -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val allMemos by viewModel.allMemos.collectAsState()
    var showRenameDialog by remember { mutableStateOf(false) }
    var showAddSelection by remember { mutableStateOf(false) }
    val selectedMemoIds = remember { mutableStateListOf<String>() }
    var newName by remember { mutableStateOf("") }

    // Playback bar animation
    val playbarAlpha by animateFloatAsState(
        targetValue = if (uiState.currentPlayingMemoId != null) 1f else 0f,
        animationSpec = tween(300),
        label = "playbar_alpha"
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.playlist?.name ?: "Playlist",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, "Menu")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename playlist") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = {
                                    newName = uiState.playlist?.name ?: ""
                                    showRenameDialog = true
                                    menuOpen = false
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            // Mini playback bar
            AnimatedVisibility(
                visible = uiState.currentPlayingMemoId != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                MiniPlaybackBar(
                    isPlaying = uiState.isPlaying,
                    memoTitle = uiState.memos.find { it.id == uiState.currentPlayingMemoId }?.title ?: "",
                    onPlayPause = viewModel::togglePlayPause,
                    onNext = viewModel::playNext,
                    onPrevious = viewModel::playPrevious
                )
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VocalizeRed)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Playlist header card
            item {
                PlaylistHeaderCard(
                    memoCount = uiState.memos.size,
                    totalDuration = uiState.memos.sumOf { it.duration },
                    isPlaying = uiState.isPlaying,
                    onPlayAll = viewModel::playAll,
                    onShuffle = {
                        val memos = uiState.memos.shuffled()
                        if (memos.isNotEmpty()) viewModel.playMemo(memos.first())
                    },
                    onAddSelection = { showAddSelection = true }
                )
            }

            if (uiState.memos.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.LibraryMusic,
                                null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "This playlist is empty",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Add memos from the memo detail screen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(uiState.memos, key = { _, m -> m.id }) { index, memo ->
                    val isCurrentlyPlaying = memo.id == uiState.currentPlayingMemoId
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(memo.id) {
                        kotlinx.coroutines.delay(index * 40L)
                        visible = true
                    }
                    AnimatedVisibility(
                        visible = visible,
                        enter = slideInVertically(initialOffsetY = { 60 }) + fadeIn()
                    ) {
                        Box {
                            MemoCard(
                                memo = memo,
                                category = null,
                                onClick = { onNavigateToMemoDetail(memo.id) },
                                onDelete = { viewModel.deleteMemo(memo) },
                                onAddToPlaylist = {}
                            )
                            if (isCurrentlyPlaying) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(VocalizeRed.copy(alpha = 0.10f), Color.Transparent)
                                            )
                                        )
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showAddSelection) {
        val currentPlaylistMemoIds = uiState.memos.map { it.id }.toSet()
        val availableMemos = allMemos.filterNot { it.id in currentPlaylistMemoIds }

        AlertDialog(
            onDismissRequest = {
                selectedMemoIds.clear()
                showAddSelection = false
            },
            title = { Text("Add memos to playlist") },
            text = {
                if (availableMemos.isEmpty()) {
                    Text("No additional memos available to add.")
                } else {
                    Column(Modifier.heightIn(max = 320.dp)) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(availableMemos) { memo ->
                                val checked = memo.id in selectedMemoIds
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (checked) selectedMemoIds.remove(memo.id) else selectedMemoIds.add(memo.id)
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = {
                                            if (it) selectedMemoIds.add(memo.id) else selectedMemoIds.remove(memo.id)
                                        }
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(memo.title.ifBlank { "Untitled memo" }, fontWeight = FontWeight.Medium)
                                        Text(formatDuration(memo.duration), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedMemoIds.isNotEmpty()) {
                            viewModel.addMemosToPlaylist(selectedMemoIds.toList())
                        }
                        selectedMemoIds.clear()
                        showAddSelection = false
                    },
                    enabled = selectedMemoIds.isNotEmpty()
                ) {
                    Text("Add selected")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedMemoIds.clear()
                    showAddSelection = false
                }) { Text("Cancel") }
            }
        )
    }

    // Rename dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank()) viewModel.renamePlaylist(newName.trim())
                    showRenameDialog = false
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PlaylistHeaderCard(
    memoCount: Int,
    totalDuration: Long,
    isPlaying: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onAddSelection: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(listOf(VocalizePurple, VocalizeRed))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.QueueMusic, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "$memoCount memo${if (memoCount != 1) "s" else ""} · ${formatDuration(totalDuration)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPlayAll,
                    colors = ButtonDefaults.buttonColors(containerColor = VocalizeRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    enabled = memoCount > 0
                ) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isPlaying) "Pause" else "Play All", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onAddSelection,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    enabled = true
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onShuffle,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                enabled = memoCount > 0
            ) {
                Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Shuffle", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun MiniPlaybackBar(
    isPlaying: Boolean,
    memoTitle: String,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VocalizeRed.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, null, tint = VocalizeRed, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = memoTitle.ifBlank { "Now Playing" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            IconButton(onClick = onPrevious) {
                Icon(Icons.Default.SkipPrevious, "Previous", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(
                onClick = onPlayPause,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = VocalizeRed)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (isPlaying) "Pause" else "Play",
                    tint = Color.White
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.SkipNext, "Next", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m ${s}s"
}
