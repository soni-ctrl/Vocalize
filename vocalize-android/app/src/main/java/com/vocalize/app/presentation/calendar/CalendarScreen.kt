package com.vocalize.app.presentation.calendar

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.vocalize.app.data.local.entity.MemoEntity
import com.vocalize.app.presentation.components.MemoCard
import com.vocalize.app.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMemoDetail: (String) -> Unit,
    onNavigateToRecorder: () -> Unit,
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val monthNames = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    val dayAbbreviations = listOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Calendar",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToRecorder,
                containerColor = VocalizeRed,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Mic, "Record with reminder", modifier = Modifier.size(26.dp))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Month navigator
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Month + Year header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.changeMonth(-1) }) {
                            Icon(Icons.Default.ChevronLeft, "Previous month", tint = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            text = "${monthNames[uiState.selectedMonth]} ${uiState.selectedYear}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { viewModel.changeMonth(1) }) {
                            Icon(Icons.Default.ChevronRight, "Next month", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Day abbreviation headers
                    Row(modifier = Modifier.fillMaxWidth()) {
                        dayAbbreviations.forEach { day ->
                            Text(
                                text = day,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Calendar grid
                    val cal = Calendar.getInstance().apply {
                        set(uiState.selectedYear, uiState.selectedMonth, 1)
                    }
                    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
                    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    val totalCells = firstDayOfWeek + daysInMonth
                    val rows = (totalCells + 6) / 7

                    for (row in 0 until rows) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (col in 0 until 7) {
                                val cellIndex = row * 7 + col
                                val day = cellIndex - firstDayOfWeek + 1
                                Box(
                                    modifier = Modifier.weight(1f).aspectRatio(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (day in 1..daysInMonth) {
                                        CalendarDayCell(
                                            day = day,
                                            isSelected = day == uiState.selectedDay,
                                            hasMemo = day in uiState.daysWithMemos,
                                            hasReminder = day in uiState.daysWithReminders,
                                            isToday = run {
                                                val today = Calendar.getInstance()
                                                day == today.get(Calendar.DAY_OF_MONTH) &&
                                                        uiState.selectedMonth == today.get(Calendar.MONTH) &&
                                                        uiState.selectedYear == today.get(Calendar.YEAR)
                                            },
                                            onClick = { viewModel.selectDay(day) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Tabs for Memos and Reminders
            TabRow(
                selectedTabIndex = uiState.selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("Memos") }
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("Reminders") }
                )
            }

            // Selected day content
            AnimatedContent(
                targetState = Pair(uiState.selectedDay, uiState.selectedTab),
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                },
                label = "day_content"
            ) { (_, tab) ->
                val items = if (tab == 0) uiState.memosOnSelectedDay else uiState.remindersOnSelectedDay
                val itemName = if (tab == 0) "memo" else "reminder"
                Column {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (tab == 0) VocalizeRed else VocalizeOrange)
                        )
                        Text(
                            text = if (items.isEmpty()) "No ${itemName}s for this day"
                            else "${items.size} ${itemName}${if (items.size > 1) "s" else ""}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    if (items.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    if (tab == 0) Icons.Default.EventNote else Icons.Default.Alarm,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    if (tab == 0) "Tap the mic to record a memo\nfor this day" else "No reminders set for this day",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(items, key = { _, m -> m.id }) { _, memo ->
                                var visible by remember { mutableStateOf(false) }
                                LaunchedEffect(memo.id) { visible = true }
                                AnimatedVisibility(
                                    visible = visible,
                                    enter = slideInVertically(initialOffsetY = { 40 }) + fadeIn()
                                ) {
                                    MemoCard(
                                        memo = memo,
                                        category = null,
                                        onClick = { onNavigateToMemoDetail(memo.id) },
                                        onDelete = { viewModel.deleteMemo(memo) },
                                        onAddToPlaylist = {}
                                    )
                                }
                            }
                            item { Spacer(Modifier.height(80.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: Int,
    isSelected: Boolean,
    hasMemo: Boolean,
    hasReminder: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val bgColor = when {
        isSelected -> VocalizeRed
        isToday -> VocalizeRed.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> Color.White
        isToday -> VocalizeRed
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .padding(2.dp)
            .fillMaxSize()
            .clip(CircleShape)
            .background(bgColor)
            .then(if (isToday && !isSelected) Modifier.border(1.dp, VocalizeRed, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
            if (hasMemo || hasReminder) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(top = 1.dp)
                ) {
                    if (hasMemo) Box(Modifier.size(4.dp).clip(CircleShape).background(if (isSelected) Color.White else VocalizeAccentBlue))
                    if (hasReminder) Box(Modifier.size(4.dp).clip(CircleShape).background(if (isSelected) Color.White else VocalizeOrange))
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault()).format(Date(timestamp))
