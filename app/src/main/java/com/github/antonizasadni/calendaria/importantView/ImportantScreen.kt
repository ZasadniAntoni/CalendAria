package com.github.antonizasadni.calendaria.importantView

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.antonizasadni.calendaria.notifications.ReminderManager
import com.github.antonizasadni.calendaria.repetitiveView.CustomClockDialog
import com.github.antonizasadni.calendaria.tasks.FilterBottomSheet
import com.github.antonizasadni.calendaria.tasks.ImportantTask
import com.github.antonizasadni.calendaria.tasks.SearchableTopBar
import com.github.antonizasadni.calendaria.tasks.TaskFilter
import com.github.antonizasadni.calendaria.tasks.TaskManagement
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun ImportantTasksScreen(
    tasks: MutableList<ImportantTask>,
    onTasksChanged: () -> Unit
) {
    val context = LocalContext.current
    var viewingTask by remember { mutableStateOf<ImportantTask?>(null) }
    var editingTask by remember { mutableStateOf<ImportantTask?>(null) }
    var taskToDelete by remember { mutableStateOf<ImportantTask?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showFilterSheet by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf(TaskFilter()) }

    // Use derivedStateOf for reactive filtering that responds to changes in the 'tasks' list content
    val filteredTasks by remember {
        derivedStateOf {
            tasks.filter { task ->
                val tokens = searchQuery.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
                val matchesSearch = tokens.all { task.title.contains(it, ignoreCase = true) }
                val matchesStatus = when {
                    activeFilter.showCompleted && activeFilter.showIncomplete -> true
                    activeFilter.showCompleted -> task.isCompleted
                    else -> !task.isCompleted
                }
                val matchesDate = activeFilter.selectedDate?.let { nonNullDate ->
                    val filterDateString = nonNullDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                    task.dueDate == filterDateString
                } ?: true
                matchesSearch && matchesStatus && matchesDate
            }.sortedWith(compareBy<ImportantTask> { it.isCompleted }.thenBy { it.dueDate })
        }
    }

    if (tasks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No important tasks yet.", color = Color.Gray)
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchableTopBar(
                title = "My Tasks",
                searchQuery = searchQuery,
                onQueryChange = { searchQuery = it },
                onFilterClick = { showFilterSheet = true }
            )
            LazyColumn(modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)) {
                items(filteredTasks, key = { it.id }) { task ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { viewingTask = task },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (task.isCompleted)
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = task.isCompleted,
                                onCheckedChange = {
                                    TaskManagement.toggleImportantTaskCompletion(context, task.id)
                                    onTasksChanged()
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = task.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        modifier = Modifier.weight(1f),
                                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                        color = if (task.isCompleted) Color.Gray else Color.Unspecified
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = task.dueDate,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (task.isCompleted) Color.Gray else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.wrapContentWidth(),
                                        softWrap = false
                                    )
                                }
                                Text(
                                    text = "Time: ${TaskManagement.formatTime(context, task.time)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (task.isCompleted) Color.Gray else Color.Unspecified
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = task.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (task.isCompleted) Color.Gray else Color.Unspecified
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        FilterBottomSheet(
            isRepetitiveType = false,
            currentFilter = activeFilter,
            onDismiss = { showFilterSheet = false },
            onApply = { newFilter ->
                activeFilter = newFilter
                showFilterSheet = false
            }
        )
    }

    if (viewingTask != null) {
        ViewImportantTaskDialog(
            task = viewingTask!!,
            onDismiss = { viewingTask = null },
            onEdit = {
                editingTask = viewingTask
                viewingTask = null
            },
            onDeleteRequest = {
                taskToDelete = viewingTask
                viewingTask = null
            },
            onToggleComplete = {
                TaskManagement.toggleImportantTaskCompletion(context, viewingTask!!.id)
                val updatedTask = viewingTask?.copy(isCompleted = !viewingTask!!.isCompleted)
                updatedTask?.let { ReminderManager.scheduleImportantTask(context, it) }
                onTasksChanged()
                // Update local state to reflect change immediately in the dialog
                viewingTask = updatedTask
            }
        )
    }

    if (taskToDelete != null) {
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("Delete Important Task") },
            text = { Text("Are you sure you want to remove \"${taskToDelete?.title}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        val taskToCancel = taskToDelete
                        tasks.removeIf { it.id == taskToDelete?.id }
                        TaskManagement.saveImportantTasks(context, tasks)
                        taskToCancel?.let { ReminderManager.cancelImportantTask(context, it) }
                        onTasksChanged()
                        taskToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (editingTask != null) {
        AddImportantTaskDialog(
            existingTask = editingTask,
            onDismiss = { editingTask = null },
            onConfirm = { updatedTask ->
                val index = tasks.indexOfFirst { it.id == updatedTask.id }
                if (index != -1) {
                    tasks[index] = updatedTask
                } else {
                    tasks.add(updatedTask)
                }
                TaskManagement.saveImportantTasks(context, tasks)
                ReminderManager.scheduleImportantTask(context, updatedTask)
                onTasksChanged()
                editingTask = null
            }
        )
    }
}

@Composable
fun ViewImportantTaskDialog(
    task: ImportantTask,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDeleteRequest: () -> Unit,
    onToggleComplete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.title,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
                if (onEdit != null) {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = null) }
                }
            }
        },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleComplete() }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = task.isCompleted, onCheckedChange = { onToggleComplete() }, )
                    Text(text = if (task.isCompleted) "Completed" else "Mark as Completed")
                }
                val context = LocalContext.current
                Text(text = "Due Date: ${task.dueDate}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                Text(
                    text = if (task.time == "Whole Day") "Time: Whole Day" else "Time: ${TaskManagement.formatTime(context, task.time)} (${task.durationMinutes} min)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(text = "Notifications: ${if (task.notificationsEnabled) "Enabled" else "Disabled"}", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = task.description, style = MaterialTheme.typography.bodyLarge)
            }
        },
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDeleteRequest) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        // place here
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddImportantTaskDialog(
    existingTask: ImportantTask? = null,
    onDismiss: () -> Unit,
    onConfirm: (ImportantTask) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(existingTask?.title ?: "") }
    var description by remember { mutableStateOf(existingTask?.description ?: "") }
    var dueDate by remember { mutableStateOf(existingTask?.dueDate ?: LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))) }
    var time by remember { mutableStateOf(existingTask?.time ?: "Whole Day") }
    var durationMinutes by remember { mutableIntStateOf(existingTask?.durationMinutes?.takeIf { it > 0 } ?: 60) }
    var notificationsEnabled by remember { mutableStateOf(existingTask?.notificationsEnabled ?: true) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = if (existingTask != null) {
            LocalDate.parse(existingTask.dueDate, DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } else System.currentTimeMillis()
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingTask == null) "New Important Task" else "Edit Important Task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { },
                    label = { Text("Due Date") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = null)
                        }
                    }
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = time == "Whole Day", onClick = { time = "Whole Day" })
                    Text("Whole Day")
                    Spacer(modifier = Modifier.width(8.dp))
                    RadioButton(selected = time != "Whole Day", onClick = { showTimePicker = true })
                    Text(if (time == "Whole Day") "Specific Time" else TaskManagement.formatTime(context, time))
                }

                if (time != "Whole Day") {
                    Text("Duration: $durationMinutes minutes", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = durationMinutes.toFloat(),
                        onValueChange = { durationMinutes = it.toInt() },
                        valueRange = 15f..240f,
                        steps = 14
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().clickable { notificationsEnabled = !notificationsEnabled },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = notificationsEnabled, onCheckedChange = { notificationsEnabled = it })
                    Text("Enable Notifications")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    onConfirm(
                        ImportantTask(
                            id = existingTask?.id ?: UUID.randomUUID().toString(),
                            title = title,
                            description = description,
                            dueDate = dueDate,
                            time = time,
                            durationMinutes = durationMinutes,
                            isCompleted = existingTask?.isCompleted ?: false,
                            notificationsEnabled = notificationsEnabled
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        dueDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        CustomClockDialog(
            initialTime = time,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute, amPm ->
                time = TaskManagement.convertToUniformTime(hour, minute, amPm)
                showTimePicker = false
            }
        )
    }
}
