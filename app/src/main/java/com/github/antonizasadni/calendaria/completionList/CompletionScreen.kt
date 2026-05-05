package com.github.antonizasadni.calendaria.completionList

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import com.github.antonizasadni.calendaria.tasks.RepetitiveTask
import com.github.antonizasadni.calendaria.tasks.TaskFilter
import com.github.antonizasadni.calendaria.tasks.SearchableTopBar
import com.github.antonizasadni.calendaria.tasks.filterTasks
import com.github.antonizasadni.calendaria.completionList.FileManagement.TaskStatsResult
import com.github.antonizasadni.calendaria.tasks.FilterBottomSheet

enum class CompletionView { LIST, CALENDAR, ARCHIVE_INDEX }

@Composable
fun CompletionListScreen(
    repetitiveTasks: List<RepetitiveTask>,
    onTasksChanged: () -> Unit
) {
    var selectedTask by remember { mutableStateOf<RepetitiveTask?>(null) }
    var currentView by remember { mutableStateOf(CompletionView.LIST) }
    var archiveTargetDate by remember { mutableStateOf<LocalDate?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showFilterSheet by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf(TaskFilter()) }
    val filteredTasks = remember(searchQuery, repetitiveTasks, activeFilter) {
        repetitiveTasks.filter { task ->
            val tokens = searchQuery.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
            val matchesSearch = tokens.all { task.title.contains(it, ignoreCase = true) }
            val matchesDays = activeFilter.selectedDays.isEmpty() ||
                    task.selectedDays.any { it in activeFilter.selectedDays }
            matchesSearch && matchesDays
        }
    }

    when (currentView) {
        CompletionView.LIST -> {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                SearchableTopBar(
                    title = "Completion History",
                    searchQuery = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onFilterClick = { showFilterSheet = true }
                )

                if (filteredTasks.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No tasks match your search.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(repetitiveTasks) { task ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedTask = task
                                        currentView = CompletionView.CALENDAR
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(
                                        alpha = 0.4f
                                    )
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            task.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                        Text(
                                            text = if (task.selectedDays.size == 7) "Daily" else "${task.selectedDays.size} days/week",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Icon(
                                        Icons.Default.KeyboardArrowRight,
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        CompletionView.CALENDAR -> {
            TaskHistoryCalendar(
                task = selectedTask!!,
                initialDate = archiveTargetDate ?: LocalDate.now(),
                externalArchiveMode = archiveTargetDate != null,
                onBack = {
                    if (archiveTargetDate != null) {
                        currentView = CompletionView.ARCHIVE_INDEX
                        archiveTargetDate = null
                    } else {
                        currentView = CompletionView.LIST
                        selectedTask = null
                        onTasksChanged()
                    }
                },
                onOpenArchive = { currentView = CompletionView.ARCHIVE_INDEX }
            )
        }

        CompletionView.ARCHIVE_INDEX -> {
            ArchiveScreen(
                task = selectedTask!!,
                onBack = { currentView = CompletionView.CALENDAR },
                onYearSelected = { date ->
                    archiveTargetDate = date
                    currentView = CompletionView.CALENDAR
                }
            )
        }
    }

    if (showFilterSheet) {
        FilterBottomSheet(
            isRepetitiveType = true,
            currentFilter = activeFilter,
            onDismiss = { showFilterSheet = false },
            onApply = { newFilter ->
                activeFilter = newFilter
                showFilterSheet = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskHistoryCalendar(
    task: RepetitiveTask,
    initialDate: LocalDate,
    externalArchiveMode: Boolean,
    onBack: () -> Unit,
    onOpenArchive: () -> Unit
) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val taskStartDate = remember { LocalDate.parse(task.createdAt) }

    var displayDate by remember(initialDate) {
        val calculatedDate = if (externalArchiveMode) {
            if (initialDate.year == taskStartDate.year) {
                initialDate.withMonth(taskStartDate.monthValue)
            } else {
                initialDate.withMonth(1)
            }
        } else {
            initialDate
        }
        mutableStateOf(calculatedDate)
    }

    var isEditMode by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var historyMap by remember { mutableStateOf<Map<Int, Boolean>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var isMonthly by remember { mutableStateOf(true) }

    val isArchiveView = externalArchiveMode || (displayDate.year < today.year)
    val showArchiveButton = !isArchiveView && FileManagement.hasArchivedData(context)

    LaunchedEffect(displayDate, refreshTrigger) {
        isLoading = true
        val data = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            FileManagement.loadHistory(context, task.id, displayDate)
        }
        historyMap = data
        isLoading = false
    }

    val yearMonth = remember(displayDate) { YearMonth.of(displayDate.year, displayDate.monthValue) }
    val daysInMonth = remember(yearMonth) { yearMonth.lengthOfMonth() }
    val dayOfWeekOffset = remember(yearMonth) { (yearMonth.atDay(1).dayOfWeek.value - 1) % 7 }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxSize().alpha(if (isLoading) 0.8f else 1f),
            contentPadding = PaddingValues(16.dp)
        ) {
            item(span = { GridItemSpan(7) }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null)
                    }
                    Text(
                        text = if (isArchiveView) "Archive: ${task.title}" else task.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isArchiveView) MaterialTheme.colorScheme.tertiary else Color.Unspecified
                    )
                }
            }

            item(span = { GridItemSpan(7) }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val canGoBack = !isLoading && if (externalArchiveMode) {
                        val minMonth = if (displayDate.year == taskStartDate.year) taskStartDate.monthValue else 1
                        displayDate.monthValue > minMonth
                    } else {
                        displayDate.year > taskStartDate.year || displayDate.monthValue > taskStartDate.monthValue
                    }

                    val canGoForward = !isLoading && if (externalArchiveMode) {
                        displayDate.monthValue < 12
                    } else {
                        displayDate.plusMonths(1).isBefore(today.plusMonths(1))
                    }

                    IconButton(
                        onClick = { displayDate = displayDate.minusMonths(1) },
                        enabled = canGoBack
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowLeft,
                            contentDescription = "Previous Month",
                            tint = if (canGoBack) MaterialTheme.colorScheme.onSurface
                            else Color.Gray.copy(alpha = 0.5f)
                        )
                    }

                    Text(
                        text = "${displayDate.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${displayDate.year}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    IconButton(
                        onClick = { displayDate = displayDate.plusMonths(1) },
                        enabled = canGoForward
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "Next Month",
                            tint = if (canGoForward) MaterialTheme.colorScheme.onSurface
                            else Color.Gray.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            items(days.size) { index ->
                Text(days[index], textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
            }

            items(dayOfWeekOffset) { Spacer(modifier = Modifier.size(40.dp)) }

            items(
                count = daysInMonth,
                key = { index -> "${yearMonth.year}-${yearMonth.monthValue}-${index + 1}" }
            ) { index ->
                val dayNum = index + 1
                val currentDate = yearMonth.atDay(dayNum)
                val isTaskDay = task.selectedDays.contains(currentDate.dayOfWeek.value)
                val isCompleted = historyMap[dayNum] ?: false
                val isWithinBounds = !currentDate.isBefore(taskStartDate) && !currentDate.isAfter(today)

                CalendarDayCell(
                    dayNum = dayNum,
                    isCompleted = isCompleted,
                    isPastScheduledDay = isTaskDay && isWithinBounds && !isCompleted,
                    isEditMode = isEditMode,
                    isTaskDay = isTaskDay,
                    isWithinBounds = isWithinBounds,
                    onClick = {
                        FileManagement.toggleDay(context, task.id, currentDate)
                        refreshTrigger++
                    }
                )
            }

            item(span = { GridItemSpan(7) }) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    if (showArchiveButton) {
                        Button(
                            onClick = onOpenArchive,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Text("View Past Years Archive")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LegendItem(Color(0xFF4CAF50), "Completed")
                        LegendItem(Color(0xFFEF5350), "Missed")
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    TaskStatistics(
                        task = task,
                        displayDate = displayDate,
                        refreshTrigger = refreshTrigger,
                        history = historyMap,
                        isMonthly = isMonthly,
                        onToggleView = { isMonthly = it }
                    )
                    WeeklyBreakdown(task, displayDate, refreshTrigger, isMonthly)
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }

        }

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }

        FloatingActionButton(
            onClick = { isEditMode = !isEditMode },
            containerColor = if (isEditMode) Color(0xFF4CAF50) else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
        ) {
            Icon(if (isEditMode) Icons.Default.Check else Icons.Default.Edit, null, tint = Color.White)
        }
    }
}

@Composable
fun CalendarDayCell(
    dayNum: Int,
    isCompleted: Boolean,
    isPastScheduledDay: Boolean,
    isEditMode: Boolean,
    isTaskDay: Boolean,
    isWithinBounds: Boolean,
    onClick: () -> Unit
) {
    val baseColor = when {
        isCompleted -> Color(0xFF4CAF50)
        isPastScheduledDay -> Color(0xFFEF5350)
        else -> Color.Transparent
    }

    val activeBorder = if (isEditMode && isTaskDay && isWithinBounds) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else if (isCompleted || isPastScheduledDay) {
        null
    } else {
        BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.2f))
    }

    Card(
        modifier = Modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clickable(enabled = isEditMode && isTaskDay && isWithinBounds) { onClick() },
        colors = CardDefaults.cardColors(containerColor = baseColor),
        border = activeBorder
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                if (isEditMode && isTaskDay && isWithinBounds) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                else Color.Transparent
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = dayNum.toString(),
                fontSize = 14.sp,
                fontWeight = if (isEditMode && isTaskDay && isWithinBounds) FontWeight.Bold else FontWeight.Normal,
                color = if (isCompleted || isPastScheduledDay) Color.White
                else if (!isWithinBounds) Color.Gray.copy(alpha = 0.5f)
                else Color.Unspecified
            )
        }
    }
}

@Composable
fun TaskStatistics(
    task: RepetitiveTask,
    displayDate: LocalDate,
    refreshTrigger: Int,
    history: Map<Int, Boolean>,
    isMonthly: Boolean,
    onToggleView: (Boolean) -> Unit
) {
    val context = LocalContext.current

    val stats = remember(task, displayDate, refreshTrigger, isMonthly, history) {
        if (isMonthly) {
            val (done, total) = FileManagement.calculateStatsFromMap(history, task, displayDate)
            val percent = if (total > 0) (done.toFloat() / total * 100).toInt() else 0
            val missed = total - done

            TaskStatsResult(
                label = "${displayDate.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} Statistics",
                completedCount = done,
                totalScheduledCount = total,
                completionPercentage = percent,
                missedCount = missed,
                missedPercentage = if (total > 0) (missed.toFloat() / total * 100).toInt() else 0
            )
        } else {
            val (done, total) = FileManagement.getYearlySummary(context, task, displayDate.year)
            val percent = if (total > 0) (done.toFloat() / total * 100).toInt() else 0
            val missed = total - done

            TaskStatsResult(
                label = "${displayDate.year} Statistics",
                completedCount = done,
                totalScheduledCount = total,
                completionPercentage = percent,
                missedCount = missed,
                missedPercentage = if (total > 0) (missed.toFloat() / total * 100).toInt() else 0
            )
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = stats.completionPercentage.toFloat() / 100f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stats.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Year", style = MaterialTheme.typography.labelSmall)
                Switch(
                    checked = isMonthly,
                    onCheckedChange = { onToggleView(it) },
                    modifier = Modifier.scale(0.7f)
                )
                Text("Month", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
            Canvas(modifier = Modifier.size(200.dp)) {
                val strokeWidthPx = 16.dp.toPx()
                drawArc(
                    color = Color(0xFFEF5350).copy(alpha = 0.15f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
                drawArc(
                    color = Color(0xFF4CAF50),
                    startAngle = 270f,
                    sweepAngle = animatedProgress * 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF4CAF50)
                )
                Text(if (isMonthly) "This Month" else "This Year", fontSize = 12.sp, color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        StatRow("Completed", "${stats.completedCount} days", Color(0xFF4CAF50), "${stats.completionPercentage}%")
        Spacer(modifier = Modifier.height(12.dp))
        StatRow("Missed", "${stats.missedCount} days", Color(0xFFEF5350), "${stats.missedPercentage}%")

        Spacer(modifier = Modifier.height(16.dp))
        Text("Total scheduled: ${stats.totalScheduledCount}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@Composable
fun LegendItem(color: Color, label: String, isOutline: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (isOutline) color.copy(alpha = 0.2f) else color)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun StatRow(label: String, count: String, color: Color, percent: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, fontWeight = FontWeight.Medium)
        }
        Text("$count ($percent)", fontWeight = FontWeight.Bold)
    }
}

@Composable
fun WeeklyBreakdown(
    task: RepetitiveTask,
    displayDate: LocalDate,
    refreshTrigger: Int,
    isMonthly: Boolean
) {
    val context = LocalContext.current
    val yearMonth = remember(displayDate) { YearMonth.of(displayDate.year, displayDate.monthValue) }

    val weeklyStats = remember(task, displayDate, refreshTrigger, isMonthly) {
        val allSummaries = FileManagement.calculateWeeklySummaries(context, task)

        if (isMonthly) {
            allSummaries.filter { week ->
                val weekStartsInMonth = YearMonth.from(week.startDate) == yearMonth
                val weekEndsInMonth = YearMonth.from(week.endDate) == yearMonth
                weekStartsInMonth || weekEndsInMonth
            }
        } else {
            allSummaries.filter { week ->
                week.startDate.year == displayDate.year || week.endDate.year == displayDate.year
            }
        }
    }

    val weeklyNotes = remember(task.id, refreshTrigger) {
        FileManagement.loadWeeklyNotes(context, task.id)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Text(
            "Weekly Summaries",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        weeklyStats.forEach { week ->
            var isEditing by remember { mutableStateOf(false) }
            var noteText by remember { mutableStateOf(weeklyNotes[week.weekNumber] ?: "") }

            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Week ${week.weekNumber}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "${week.startDate.dayOfMonth}/${week.startDate.monthValue} - ${week.endDate.dayOfMonth}/${week.endDate.monthValue}",
                                style = MaterialTheme.typography.labelSmall, color = Color.Gray
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            val barColor = when {
                                week.percentage >= 80 -> Color(0xFF4CAF50)
                                week.percentage >= 50 -> Color(0xFFFFB74D)
                                else -> Color(0xFFEF5350)
                            }

                            val animatedProgress by animateFloatAsState(
                                targetValue = week.percentage / 100f,
                                animationSpec = tween(800),
                                label = "weeklyProgress"
                            )

                            Text("${week.percentage}%", fontWeight = FontWeight.Black, color = barColor, fontSize = 16.sp)

                            Canvas(modifier = Modifier.width(70.dp).height(4.dp)) {
                                val strokeWidthPx = size.height
                                val yCenter = size.height / 2

                                drawLine(
                                    color = barColor.copy(alpha = 0.1f),
                                    start = Offset(0f, yCenter),
                                    end = Offset(size.width, yCenter),
                                    strokeWidth = strokeWidthPx,
                                    cap = StrokeCap.Round
                                )

                                if (animatedProgress > 0) {
                                    val progressWidth = animatedProgress * size.width
                                    drawLine(
                                        color = barColor,
                                        start = Offset(0f, yCenter),
                                        end = Offset(progressWidth, yCenter),
                                        strokeWidth = strokeWidthPx,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isEditing) {
                        OutlinedTextField(
                            value = noteText,
                            onValueChange = {
                                val wordCount = it.trim().split(Regex("\\s+")).filter { s -> s.isNotEmpty() }.size
                                if (wordCount <= 300) {
                                    noteText = it
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = false,
                            minLines = 3
                        )
                    } else {
                        Text(
                            text = if (noteText.isBlank()) "No summary for this week." else noteText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (noteText.isBlank()) Color.Gray else Color.Unspecified,
                            modifier = Modifier.fillMaxWidth(),
                            lineHeight = 20.sp
                        )
                    }

                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
                        if (isEditing) {
                            TextButton(onClick = {
                                FileManagement.saveWeeklyNote(context, task.id, week.weekNumber, noteText)
                                isEditing = false
                            }) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Save")
                            }
                        } else {
                            IconButton(onClick = { isEditing = true }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
