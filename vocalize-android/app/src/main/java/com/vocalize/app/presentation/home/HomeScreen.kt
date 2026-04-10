package com.vocalize.app.presentation.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.vocalize.app.data.local.entity.CategoryEntity
import com.vocalize.app.data.local.entity.MemoEntity
import com.vocalize.app.data.local.entity.PlaylistEntity
import com.vocalize.app.presentation.components.MemoCard
import com.vocalize.app.presentation.components.PlaylistCard
import com.vocalize.app.presentation.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToRecorder: () -> Unit,
    onNavigateToMemoDetail: (String) -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState { 3 }
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedBottomTab by remember { mutableIntStateOf(0) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showCategoryFilter by remember { mutableStateOf(false) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    // Import audio launcher
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importAudio(it) }
    }

    // Snackbar
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    // FAB pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "fab_pulse")
    val fabScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fab_scale"
    )
    val fabGlow by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fab_glow"
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .drawBehind {
                        drawLine(
                            color = Color.White.copy(alpha = 0.08f),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
            ) {
                listOf(
                    Triple(Icons.Outlined.Home, Icons.Filled.Home, "Home"),
                    Triple(Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth, "Calendar"),
                    Triple(Icons.Outlined.Search, Icons.Filled.Search, "Search"),
                    Triple(Icons.Outlined.Person, Icons.Filled.Person, "Profile")
                ).forEachIndexed { idx, (outlinedIcon, filledIcon, label) ->
                    NavigationBarItem(
                        selected = selectedBottomTab == idx,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedBottomTab = idx
                            when (idx) {
                                1 -> onNavigateToCalendar()
                                2 -> onNavigateToSearch()
                                3 -> onNavigateToSettings()
                            }
                        },
                        icon = { Icon(if (selectedBottomTab == idx) filledIcon else outlinedIcon, label) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = VocalizeRed,
                            selectedTextColor = VocalizeRed,
                            indicatorColor = VocalizeRed.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !uiState.isBatchMode,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .scale(fabScale),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .alpha(fabGlow * 0.4f)
                            .background(
                                brush = Brush.radialGradient(colors = listOf(VocalizeRed, Color.Transparent)),
                                shape = CircleShape
                            )
                    )
                    FloatingActionButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNavigateToRecorder()
                        },
                        containerColor = VocalizeRed,
                        contentColor = Color.White,
                        elevation = FloatingActionButtonDefaults.elevation(8.dp),
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(Icons.Default.Mic, "Record", modifier = Modifier.size(32.dp))
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Batch selection toolbar
            AnimatedVisibility(
                visible = uiState.isBatchMode,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.cancelBatchMode() }) {
                            Icon(Icons.Default.Close, "Cancel", tint = Color.White)
                        }
                        Text(
                            text = "${uiState.selectedMemoIds.size} selected",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.selectAll() }) {
                            Text("All", color = Color.White)
                        }
                        IconButton(onClick = { viewModel.deleteSelected() }) {
                            Icon(Icons.Default.Delete, "Delete selected", tint = Color.White)
                        }
                    }
                }
            }

            // Top bar
            AnimatedVisibility(visible = !uiState.isBatchMode) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Vocalize",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (uiState.totalMemos > 0) {
                                Text(
                                    text = "${uiState.totalMemos} memo${if (uiState.totalMemos != 1) "s" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { importLauncher.launch("audio/*") }) {
                            Icon(Icons.Default.FileUpload, "Import", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { showCategoryFilter = !showCategoryFilter }) {
                            Icon(Icons.Default.FilterList, "Filter", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.AccountCircle, "Profile", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(30.dp))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }

            // Category filter chips
            AnimatedVisibility(
                visible = showCategoryFilter && !uiState.isBatchMode,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                LazyRow(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = uiState.selectedCategoryFilter == null,
                            onClick = { viewModel.setCategoryFilter(null) },
                            label = { Text("All") }
                        )
                    }
                    items(uiState.categories) { cat ->
                        FilterChip(
                            selected = uiState.selectedCategoryFilter == cat.id,
                            onClick = { viewModel.setCategoryFilter(cat.id) },
                            label = { Text(cat.name) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            runCatching { Color(android.graphics.Color.parseColor(cat.colorHex)) }.getOrDefault(VocalizeRed),
                                            CircleShape
                                        )
                                )
                            }
                        )
                    }
                }
            }

            // Tabs
            val tabs = listOf("Recents", "All Memos", "Playlists")
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
                contentColor = VocalizeRed,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                        height = 3.dp,
                        color = VocalizeRed
                    )
                },
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        selectedContentColor = VocalizeRed,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> MemoList(
                        memos = uiState.recentMemos,
                        pinnedMemos = emptyList(),
                        isLoading = uiState.isLoading,
                        isBatchMode = false,
                        selectedMemoIds = emptySet(),
                        onMemoClick = onNavigateToMemoDetail,
                        onDeleteMemo = { viewModel.deleteMemo(it) },
                        onAddToPlaylist = { memoId ->
                            uiState.playlists.firstOrNull()?.let { viewModel.addMemoToPlaylist(memoId, it.id) }
                        },
                        onPin = { viewModel.togglePin(it) },
                        onLongPress = {},
                        onSelectionToggle = {},
                        categories = uiState.categories,
                        memoCategories = uiState.memoCategories,
                        emptyText = "No recent memos. Tap the mic to start recording!"
                    )
                    1 -> MemoList(
                        memos = uiState.allMemos,
                        pinnedMemos = uiState.pinnedMemos,
                        isLoading = uiState.isLoading,
                        isBatchMode = uiState.isBatchMode,
                        selectedMemoIds = uiState.selectedMemoIds,
                        onMemoClick = onNavigateToMemoDetail,
                        onDeleteMemo = { viewModel.deleteMemo(it) },
                        onAddToPlaylist = { memoId ->
                            uiState.playlists.firstOrNull()?.let { viewModel.addMemoToPlaylist(memoId, it.id) }
                        },
                        onPin = { viewModel.togglePin(it) },
                        onLongPress = { viewModel.enterBatchMode(it) },
                        onSelectionToggle = { viewModel.toggleMemoSelection(it) },
                        categories = uiState.categories,
                        memoCategories = uiState.memoCategories,
                        emptyText = "No memos found.",
                        showDeleteAll = true,
                        onDeleteAll = { showDeleteAllConfirm = true }
                    )
                    2 -> PlaylistList(
                        playlists = uiState.playlists,
                        onPlaylistClick = onNavigateToPlaylist,
                        onDeletePlaylist = { viewModel.deletePlaylist(it) },
                        onCreatePlaylist = { showCreatePlaylistDialog = true }
                    )
                }
            }
        }
    }

    if (showCreatePlaylistDialog) {
        var playlistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Playlist name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (playlistName.isNotBlank()) {
                        viewModel.createPlaylist(playlistName)
                        showCreatePlaylistDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text("Delete All Memos") },
            text = { Text("This will permanently delete all ${uiState.totalMemos} memos and their audio files. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { showDeleteAllConfirm = false; viewModel.deleteAllMemos() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete All") }
            },
            dismissButton = { TextButton(onClick = { showDeleteAllConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MemoList(
    memos: List<MemoEntity>,
    pinnedMemos: List<MemoEntity>,
    isLoading: Boolean,
    isBatchMode: Boolean,
    selectedMemoIds: Set<String>,
    onMemoClick: (String) -> Unit,
    onDeleteMemo: (MemoEntity) -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onPin: (MemoEntity) -> Unit,
    onLongPress: (String) -> Unit,
    onSelectionToggle: (String) -> Unit,
    categories: List<CategoryEntity>,
    memoCategories: Map<String, List<CategoryEntity>> = emptyMap(),
    emptyText: String,
    showDeleteAll: Boolean = false,
    onDeleteAll: () -> Unit = {}
) {
    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = VocalizeRed)
        }
        return
    }

    val nonPinnedMemos = remember(memos, pinnedMemos) {
        memos.filter { m -> pinnedMemos.none { it.id == m.id } }
    }

    if (memos.isEmpty() && pinnedMemos.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.MicOff, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(16.dp))
                Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Pinned section
        if (pinnedMemos.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PushPin, null, tint = VocalizeOrange, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pinned", style = MaterialTheme.typography.labelMedium, color = VocalizeOrange, fontWeight = FontWeight.SemiBold)
                }
            }
            itemsIndexed(pinnedMemos, key = { _, m -> "pin_${m.id}" }) { index, memo ->
                AnimatedMemoCard(
                    memo = memo,
                    index = index,
                    isBatchMode = isBatchMode,
                    isSelected = memo.id in selectedMemoIds,
                    onMemoClick = onMemoClick,
                    onDeleteMemo = onDeleteMemo,
                    onAddToPlaylist = onAddToPlaylist,
                    onPin = onPin,
                    onLongPress = onLongPress,
                    onSelectionToggle = onSelectionToggle,
                    categories = categories,
                    memoCategories = memoCategories
                )
            }

            if (nonPinnedMemos.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("All Memos", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        itemsIndexed(nonPinnedMemos, key = { _, m -> m.id }) { index, memo ->
            AnimatedMemoCard(
                memo = memo,
                index = index,
                isBatchMode = isBatchMode,
                isSelected = memo.id in selectedMemoIds,
                onMemoClick = onMemoClick,
                onDeleteMemo = onDeleteMemo,
                onAddToPlaylist = onAddToPlaylist,
                onPin = onPin,
                onLongPress = onLongPress,
                onSelectionToggle = onSelectionToggle,
                categories = categories,
                memoCategories = memoCategories
            )
        }

        if (showDeleteAll && (memos.isNotEmpty() || pinnedMemos.isNotEmpty()) && !isBatchMode) {
            item {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onDeleteAll,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete all memos")
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnimatedMemoCard(
    memo: MemoEntity,
    index: Int,
    isBatchMode: Boolean,
    isSelected: Boolean,
    onMemoClick: (String) -> Unit,
    onDeleteMemo: (MemoEntity) -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onPin: (MemoEntity) -> Unit,
    onLongPress: (String) -> Unit,
    onSelectionToggle: (String) -> Unit,
    categories: List<CategoryEntity>,
    memoCategories: Map<String, List<CategoryEntity>>
) {
    val animDelay = (index * 60).coerceAtMost(600)
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(memo.id) {
        kotlinx.coroutines.delay(animDelay.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)
        ) + fadeIn(tween(300))
    ) {
        MemoCard(
            memo = memo,
            categories = memoCategories[memo.id] ?: emptyList(),
            category = categories.find { it.id == memo.categoryId },
            onClick = { onMemoClick(memo.id) },
            onDelete = { onDeleteMemo(memo) },
            onAddToPlaylist = { onAddToPlaylist(memo.id) },
            onPin = { onPin(memo) },
            isSelected = isSelected,
            onSelectionToggle = if (isBatchMode) ({ onSelectionToggle(memo.id) }) else null
        )
    }
}

@Composable
private fun PlaylistList(
    playlists: List<PlaylistEntity>,
    onPlaylistClick: (String) -> Unit,
    onDeletePlaylist: (PlaylistEntity) -> Unit,
    onCreatePlaylist: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedButton(
                onClick = onCreatePlaylist,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Create Playlist")
            }
        }

        if (playlists.isEmpty()) {
            item {
                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.QueueMusic, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(16.dp))
                        Text("No playlists yet. Create one!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            itemsIndexed(playlists, key = { _, p -> p.id }) { _, playlist ->
                PlaylistCard(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist.id) },
                    onDelete = { onDeletePlaylist(playlist) }
                )
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}
