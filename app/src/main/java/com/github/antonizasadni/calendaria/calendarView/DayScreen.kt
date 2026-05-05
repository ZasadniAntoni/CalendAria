package com.github.antonizasadni.calendaria.calendarView

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.antonizasadni.calendaria.importantView.AddImportantTaskDialog
import com.github.antonizasadni.calendaria.repetitiveView.AddRepetitiveTaskDialog
import com.github.antonizasadni.calendaria.tasks.ImportantTask
import com.github.antonizasadni.calendaria.tasks.RepetitiveTask
import com.github.antonizasadni.calendaria.tasks.TaskManagement
import com.github.antonizasadni.calendaria.importantView.ViewImportantTaskDialog
import com.github.antonizasadni.calendaria.repetitiveView.ViewRepetitiveTaskDialog
import com.github.antonizasadni.calendaria.completionList.FileManagement
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(
    date: LocalDate,
    repetitiveTasks: List<RepetitiveTask>,
    importantTasks: List<ImportantTask>,
    onBack: () -> Unit,
    onTasksChanged: () -> Unit
) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val isToday = date == today

    val dayOfWeek = date.dayOfWeek.value
    val dateString = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

    var viewingRepetitive by remember { mutableStateOf<RepetitiveTask?>(null) }
    var editingRepetitive by remember { mutableStateOf<RepetitiveTask?>(null) }
    var repetitiveToDelete by remember { mutableStateOf<RepetitiveTask?>(null) }

    var viewingImportant by remember { mutableStateOf<ImportantTask?>(null) }
    var editingImportant by remember { mutableStateOf<ImportantTask?>(null) }
    var importantToDelete by remember { mutableStateOf<ImportantTask?>(null) }

    val filteredRepetitive = repetitiveTasks
        .filter { it.selectedDays.contains(dayOfWeek) }
        .sortedWith(
            compareBy<RepetitiveTask> {
                if (isToday) {
                    val history = FileManagement.loadHistory(context, it.id, date)
                    history[date.dayOfMonth] ?: false
                } else false
            }.thenBy { TaskManagement.parseTimeToMinutes(it.time) }
        )

    val filteredImportant = importantTasks
        .filter { it.dueDate == dateString }
        .sortedWith(
            compareBy<ImportantTask> { TaskManagement.parseTimeToMinutes(it.time) }
        )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${date.year}")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            if (filteredRepetitive.isEmpty() && filteredImportant.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No tasks for this day", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(filteredImportant, key = { "imp_${it.id}" }) { task ->
                        val completed = task.isCompleted
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewingImportant = task },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (completed)
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isToday) {
                                    Checkbox(
                                        checked = completed,
                                        onCheckedChange = {
                                            TaskManagement.toggleImportantTaskCompletion(context, task.id)
                                            onTasksChanged()
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = task.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            modifier = Modifier.weight(1f),
                                            textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None,
                                            color = if (completed) Color.Gray else Color.Unspecified
                                        )
                                        Text(
                                            text = "IMPORTANT",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (completed) Color.Gray else MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = task.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (completed) Color.Gray else Color.Unspecified
                                    )
                                    Text(
                                        text = "Time: ${TaskManagement.formatTime(context, task.time)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (completed) Color.Gray else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    items(filteredRepetitive, key = { "rep_${it.id}" }) { task ->
                        val history = FileManagement.loadHistory(context, task.id, date)
                        val completed = if (isToday) (history[date.dayOfMonth] ?: false) else false

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewingRepetitive = task },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (completed)
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isToday) {
                                    Checkbox(
                                        checked = completed,
                                        onCheckedChange = {
                                            FileManagement.toggleDay(context, task.id, date)
                                            onTasksChanged()
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = task.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None,
                                        color = if (completed) Color.Gray else Color.Unspecified
                                    )
                                    Text(
                                        text = "Time: ${TaskManagement.formatTime(context, task.time)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (completed) Color.Gray else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (viewingRepetitive != null) {
        ViewRepetitiveTaskDialog(
            task = viewingRepetitive!!,
            date = if (isToday) date else null,
            onDismiss = { viewingRepetitive = null },
            onEdit = {
                editingRepetitive = viewingRepetitive
                viewingRepetitive = null
            },
            onDeleteRequest = {
                repetitiveToDelete = viewingRepetitive
                viewingRepetitive = null
            },
            onToggleComplete = { dateToToggle ->
                if (isToday) {
                    FileManagement.toggleDay(context, viewingRepetitive!!.id, dateToToggle)
                    onTasksChanged()
                }
            },
            onDateChanged = { onTasksChanged() }
        )
    }

    if (viewingImportant != null) {
        key(viewingImportant?.id) {
            ViewImportantTaskDialog(
                task = viewingImportant!!,
                onDismiss = { viewingImportant = null },
                onEdit = {
                    editingImportant = viewingImportant
                    viewingImportant = null
                },
                onDeleteRequest = {
                    importantToDelete = viewingImportant
                    viewingImportant = null
                },
                onToggleComplete = {
                    TaskManagement.toggleImportantTaskCompletion(context, viewingImportant!!.id)
                    viewingImportant =
                        viewingImportant!!.copy(isCompleted = !viewingImportant!!.isCompleted)
                    onTasksChanged()
                }
            )
        }
    }

    if (repetitiveToDelete != null) {
        AlertDialog(
            onDismissRequest = { repetitiveToDelete = null },
            title = { Text("Delete Task") },
            text = { Text("Are you sure you want to remove \"${repetitiveToDelete?.title}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        val list = TaskManagement.loadRepetitiveTasks(context).toMutableList()
                        list.removeIf { it.id == repetitiveToDelete?.id }
                        TaskManagement.saveRepetitiveTasks(context, list)
                        onTasksChanged()
                        repetitiveToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { repetitiveToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (importantToDelete != null) {
        AlertDialog(
            onDismissRequest = { importantToDelete = null },
            title = { Text("Delete Important Task") },
            text = { Text("Are you sure you want to remove \"${importantToDelete?.title}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        val list = TaskManagement.loadImportantTasks(context).toMutableList()
                        list.removeIf { it.id == importantToDelete?.id }
                        TaskManagement.saveImportantTasks(context, list)
                        onTasksChanged()
                        importantToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { importantToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (editingRepetitive != null) {
        AddRepetitiveTaskDialog(
            existingTask = editingRepetitive,
            onDismiss = { editingRepetitive = null },
            onConfirm = { updated ->
                val list = TaskManagement.loadRepetitiveTasks(context).toMutableList()
                val idx = list.indexOfFirst { it.id == updated.id }
                if (idx != -1) {
                    list[idx] = updated
                    TaskManagement.saveRepetitiveTasks(context, list)
                }
                onTasksChanged()
                editingRepetitive = null
            }
        )
    }

    if (editingImportant != null) {
        AddImportantTaskDialog(
            existingTask = editingImportant,
            onDismiss = { editingImportant = null },
            onConfirm = { updated ->
                val list = TaskManagement.loadImportantTasks(context).toMutableList()
                val idx = list.indexOfFirst { it.id == updated.id }
                if (idx != -1) {
                    list[idx] = updated
                    TaskManagement.saveImportantTasks(context, list)
                }
                onTasksChanged()
                editingImportant = null
            }
        )
    }
}
