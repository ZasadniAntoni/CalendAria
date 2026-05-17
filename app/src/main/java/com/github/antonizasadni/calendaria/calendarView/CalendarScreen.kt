package com.github.antonizasadni.calendaria.calendarView

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.antonizasadni.calendaria.tasks.ImportantTask
import com.github.antonizasadni.calendaria.tasks.RepetitiveTask
import com.github.antonizasadni.calendaria.tasks.TaskManagement
import com.github.antonizasadni.calendaria.completionList.FileManagement
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
@Composable
fun CalendarScreen(
    month: Int,
    year: Int,
    today: LocalDate,
    repetitiveTasks: List<RepetitiveTask>,
    importantTasks: List<ImportantTask>,
    onMonthYearChange: (Int, Int) -> Unit,
    onTasksChanged: () -> Unit
) {
    val context = LocalContext.current
    val yearMonth = remember(month, year) { YearMonth.of(year, month) }
    val daysInMonth = yearMonth.lengthOfMonth()
    val firstOfMonth = yearMonth.atDay(1)
    val dayOfWeekOffset = (firstOfMonth.dayOfWeek.value - 1) % 7
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    var currentToday by remember { mutableStateOf(LocalDate.now()) }

    LaunchedEffect(Unit) {
        val result = TaskManagement.checkAndResetDailyTasks(context)
        currentToday = result.currentDate
        onTasksChanged()
    }

    if (selectedDate != null) {
        DayScreen(
            date = selectedDate!!,
            repetitiveTasks = repetitiveTasks,
            importantTasks = importantTasks,
            onBack = { selectedDate = null },
            onTasksChanged = onTasksChanged
        )
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        val prev = yearMonth.minusMonths(1)
                        onMonthYearChange(prev.monthValue, prev.year)
                    }) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null)
                    }

                    val isCurrentMonth = month == currentToday.monthValue && year == currentToday.year

                    TextButton(
                        onClick = { onMonthYearChange(currentToday.monthValue, currentToday.year) },
                        enabled = !isCurrentMonth
                    ) {
                        Text(
                            text = "This Month",
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrentMonth) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = {
                        val next = yearMonth.plusMonths(1)
                        onMonthYearChange(next.monthValue, next.year)
                    }) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
                    }
                }

                Text(
                    text = "${yearMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} $year",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                days.forEach { day ->
                    Text(day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxWidth().height(280.dp)
            ) {
                items(dayOfWeekOffset) { Spacer(modifier = Modifier.size(40.dp)) }
                items(daysInMonth) { index ->
                    val dayNum = index + 1
                    val date = yearMonth.atDay(dayNum)
                    val dateStr = date.format(dateFormatter)
                    val dRepetitive = repetitiveTasks.filter { TaskManagement.isTaskActiveOnDate(it, date) }
                    val dImportant = importantTasks.filter { it.taskDate == dateStr }
                    val isToday = dayNum == currentToday.dayOfMonth && month == currentToday.monthValue && year == currentToday.year

                    Card(
                        modifier = Modifier.padding(2.dp).clickable { selectedDate = date },
                        colors = when {
                            isToday -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            dImportant.isNotEmpty() -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                            dRepetitive.isNotEmpty() -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            else -> CardDefaults.cardColors()
                        }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(4.dp).fillMaxWidth()
                        ) {
                            Text(text = dayNum.toString(), textAlign = TextAlign.Center, fontSize = 14.sp)
                            Row(
                                modifier = Modifier.padding(top = 2.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                dImportant.take(2).forEach { _ ->
                                    Box(Modifier.padding(horizontal = 1.dp).size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                                }
                                dRepetitive.take(3 - dImportant.size.coerceAtMost(2)).forEach { _ ->
                                    Box(Modifier.padding(horizontal = 1.dp).size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Today's Tasks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            val todaysRepetitive = repetitiveTasks
                .filter { it.selectedDays.contains(currentToday.dayOfWeek.value) }
                .map { task ->
                    val history = FileManagement.loadHistory(context, task.id, currentToday)
                    val isCompletedInHistory = history[currentToday.dayOfMonth] ?: false
                    task to isCompletedInHistory
                }
                .sortedWith(compareBy<Pair<RepetitiveTask, Boolean>> { it.second }
                    .thenBy { TaskManagement.parseTimeToMinutes(it.first.time) }
                )

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(todaysRepetitive, key = { it.first.id }) { (task, isCompleted) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isCompleted,
                            onCheckedChange = {
                                FileManagement.toggleDay(context, task.id, currentToday)
                                onTasksChanged()
                            }
                        )
                        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (isCompleted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = task.time,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (todaysRepetitive.isEmpty()) {
                    item {
                        Text(
                            "No tasks for today",
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}
