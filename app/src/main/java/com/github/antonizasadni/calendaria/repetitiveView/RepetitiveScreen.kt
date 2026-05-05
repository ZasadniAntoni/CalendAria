package com.github.antonizasadni.calendaria.repetitiveView

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.github.antonizasadni.calendaria.completionList.FileManagement
import com.github.antonizasadni.calendaria.tasks.FilterBottomSheet
import com.github.antonizasadni.calendaria.tasks.RepetitiveTask
import com.github.antonizasadni.calendaria.tasks.SearchableTopBar
import com.github.antonizasadni.calendaria.tasks.TaskFilter
import com.github.antonizasadni.calendaria.tasks.TaskManagement
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import androidx.compose.material.icons.filled.DateRange
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import android.content.Context

@Composable
fun RepetitiveTasksScreen(
    tasks: MutableList<RepetitiveTask>,
    onTasksChanged: () -> Unit
) {
    val context = LocalContext.current
    var viewingTask by remember { mutableStateOf<RepetitiveTask?>(null) }
    var editingTask by remember { mutableStateOf<RepetitiveTask?>(null) }
    var taskToDelete by remember { mutableStateOf<RepetitiveTask?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showFilterSheet by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf(TaskFilter()) }

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
                val matchesDays = activeFilter.selectedDays.isEmpty() ||
                        activeFilter.selectedDays.any { task.selectedDays.contains(it) }
                matchesSearch && matchesStatus && matchesDays
            }.sortedBy { it.isCompleted }
        }
    }

    if (tasks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No repetitive tasks yet.", color = Color.Gray)
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchableTopBar(
                title = "My Habits",
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
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                    color = if (task.isCompleted) Color.Gray else Color.Unspecified
                                )
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
            isRepetitiveType = true,
            currentFilter = activeFilter,
            onDismiss = { showFilterSheet = false },
            onApply = { newFilter ->
                activeFilter = newFilter
                showFilterSheet = false
            }
        )
    }

    if (viewingTask != null) {
        ViewRepetitiveTaskDialog(
            task = viewingTask!!,
            date = LocalDate.now(),
            onDismiss = { viewingTask = null },
            onEdit = {
                editingTask = viewingTask
                viewingTask = null
            },
            onDeleteRequest = {
                taskToDelete = viewingTask
                viewingTask = null
            }
        )
    }

    if (taskToDelete != null) {
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("Delete Repetitive Task") },
            text = { Text("Are you sure you want to remove \"${taskToDelete?.title}\"? This will also hide its history.") },
            confirmButton = {
                Button(
                    onClick = {
                        tasks.removeIf { it.id == taskToDelete?.id }
                        TaskManagement.saveRepetitiveTasks(context, tasks)
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
        AddRepetitiveTaskDialog(
            existingTask = editingTask,
            onDismiss = { editingTask = null },
            onConfirm = { updatedTask ->
                val index = tasks.indexOfFirst { it.id == updatedTask.id }
                if (index != -1) {
                    tasks[index] = updatedTask
                } else {
                    tasks.add(updatedTask)
                }
                TaskManagement.saveRepetitiveTasks(context, tasks)
                onTasksChanged()
                editingTask = null
            }
        )
    }
}

@Composable
fun ViewRepetitiveTaskDialog(
    task: RepetitiveTask,
    date: LocalDate? = null,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDeleteRequest: () -> Unit,
    onToggleComplete: ((LocalDate) -> Unit)? = null,
    onDateChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val displayDate = date ?: LocalDate.now()
    val (doneCount, scheduledCount) = remember(task, displayDate) { FileManagement.getMonthSummary(context, task, displayDate) }

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
                if (onToggleComplete != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleComplete(displayDate) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = task.isCompleted,
                            onCheckedChange = { onToggleComplete(displayDate) })
                        Text(text = if (task.isCompleted) "Done for today!" else "Mark as done for today")
                    }
                }
                Text(text = "Days: ${formatSelectedDays(task.selectedDays)}", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = if (task.time == "Whole Day") "Time: Whole Day" else "Time: ${TaskManagement.formatTime(context, task.time)} (${task.durationMinutes} min)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(text = "Completion this month: $doneCount / $scheduledCount", style = MaterialTheme.typography.labelMedium)
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
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRepetitiveTaskDialog(
    existingTask: RepetitiveTask? = null,
    onDismiss: () -> Unit,
    onConfirm: (RepetitiveTask) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(existingTask?.title ?: "") }
    var description by remember { mutableStateOf(existingTask?.description ?: "") }
    val selectedDays = remember { mutableStateListOf<Int>().apply { addAll(existingTask?.selectedDays ?: emptySet()) } }
    var createdAt by remember { mutableStateOf(existingTask?.createdAt ?: LocalDate.now().toString()) }
    var time by remember { mutableStateOf(existingTask?.time ?: "Whole Day") }
    var durationMinutes by remember { mutableIntStateOf(existingTask?.durationMinutes?.takeIf { it > 0 } ?: 60) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = try {
            LocalDate.parse(createdAt).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) { System.currentTimeMillis() }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingTask == null) "New Habit" else "Edit Habit") },
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
                    value = LocalDate.parse(createdAt).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                    onValueChange = { },
                    label = { Text("Start Date") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = null)
                        }
                    }
                )

                Text("Repeat on:", style = MaterialTheme.typography.labelLarge)
                val days = listOf("M", "T", "W", "T", "F", "S", "S")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    days.forEachIndexed { index, day ->
                        val dayNum = index + 1
                        val isSelected = selectedDays.contains(dayNum)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                                .clickable {
                                    if (isSelected) selectedDays.remove(dayNum) 
                                    else selectedDays.add(dayNum)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary 
                                        else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

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
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank() && selectedDays.isNotEmpty(),
                onClick = {
                    onConfirm(
                        RepetitiveTask(
                            id = existingTask?.id ?: UUID.randomUUID().toString(),
                            title = title,
                            description = description,
                            selectedDays = selectedDays.toList(),
                            time = time,
                            durationMinutes = durationMinutes,
                            isCompleted = existingTask?.isCompleted ?: false,
                            createdAt = createdAt
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
                        createdAt = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                            .toString()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomClockDialog(
    initialTime: String = "12:00 AM",
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, String) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("CalendAriaPrefs", Context.MODE_PRIVATE) }
    val use24h = remember { prefs.getBoolean("use_24h_format", false) }

    val initialValues = remember(initialTime, use24h) {
        val regex = Regex("(\\d+):(\\d+)\\s*(AM|PM)", RegexOption.IGNORE_CASE)
        val match = regex.find(initialTime)
        if (match != null) {
            val (h, m, ap) = match.destructured
            val hour12 = h.toIntOrNull() ?: 12
            val minute = m.toIntOrNull() ?: 0
            val ampm = ap.uppercase()
            
            if (use24h) {
                var hour24 = hour12
                if (ampm == "PM" && hour12 < 12) hour24 += 12
                if (ampm == "AM" && hour12 == 12) hour24 = 0
                Triple(hour24, minute, "")
            } else {
                Triple(hour12, minute, ampm)
            }
        } else {
            if (use24h) Triple(12, 0, "") else Triple(12, 0, "AM")
        }
    }

    var selectedHour by remember { mutableIntStateOf(initialValues.first) }
    var selectedMinute by remember { mutableIntStateOf(initialValues.second) }
    var amPm by remember { mutableStateOf(initialValues.third) }

    var showHourInput by remember { mutableStateOf(false) }
    var showMinuteInput by remember { mutableStateOf(false) }
    
    val hourFocusRequester = remember { FocusRequester() }
    val minuteFocusRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.wrapContentSize()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Select Time",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Hour Picker
                    Box(modifier = Modifier.weight(1f).height(150.dp), contentAlignment = Alignment.Center) {
                        if (showHourInput) {
                            var textValue by remember { mutableStateOf(selectedHour.toString()) }
                            val maxHour = if (use24h) 23 else 12
                            val minHour = if (use24h) 0 else 1
                            val isError = textValue.toIntOrNull()?.let { it !in minHour..maxHour } ?: true
                            
                            LaunchedEffect(Unit) {
                                hourFocusRequester.requestFocus()
                            }

                            OutlinedTextField(
                                value = textValue,
                                onValueChange = {
                                    if (it.length <= 2 && it.all { char -> char.isDigit() }) {
                                        textValue = it
                                        it.toIntOrNull()?.let { h ->
                                            if (h in minHour..maxHour) selectedHour = h
                                        }
                                    }
                                },
                                isError = isError,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { showHourInput = false }),
                                modifier = Modifier.width(70.dp).focusRequester(hourFocusRequester),
                                singleLine = true
                            )
                        } else {
                            InfiniteNumberPicker(
                                range = if (use24h) 0..23 else 1..12,
                                initialValue = selectedHour,
                                onValueChange = { selectedHour = it },
                                onDoubleClick = { showHourInput = true }
                            )
                        }
                    }

                    Text(":", style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(horizontal = 8.dp))

                    // Minute Picker
                    Box(modifier = Modifier.weight(1f).height(150.dp), contentAlignment = Alignment.Center) {
                        if (showMinuteInput) {
                            var textValue by remember { mutableStateOf(selectedMinute.toString().padStart(2, '0')) }
                            val isError = textValue.toIntOrNull()?.let { it !in 0..59 } ?: true

                            LaunchedEffect(Unit) {
                                minuteFocusRequester.requestFocus()
                            }

                            OutlinedTextField(
                                value = textValue,
                                onValueChange = {
                                    if (it.length <= 2 && it.all { char -> char.isDigit() }) {
                                        textValue = it
                                        it.toIntOrNull()?.let { m ->
                                            if (m in 0..59) selectedMinute = m
                                        }
                                    }
                                },
                                isError = isError,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { showMinuteInput = false }),
                                modifier = Modifier.width(70.dp).focusRequester(minuteFocusRequester),
                                singleLine = true
                            )
                        } else {
                            InfiniteNumberPicker(
                                range = 0..59,
                                initialValue = selectedMinute,
                                onValueChange = { selectedMinute = it },
                                onDoubleClick = { showMinuteInput = true }
                            )
                        }
                    }

                    if (!use24h) {
                        Spacer(modifier = Modifier.width(16.dp))

                        // AM/PM
                        Column {
                            Button(
                                onClick = { amPm = "AM" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (amPm == "AM") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (amPm == "AM") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.width(64.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) { Text("AM") }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { amPm = "PM" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (amPm == "PM") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (amPm == "PM") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.width(64.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) { Text("PM") }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onConfirm(selectedHour, selectedMinute, amPm) }) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

@Composable
fun InfiniteNumberPicker(
    range: IntRange,
    initialValue: Int,
    onValueChange: (Int) -> Unit,
    onDoubleClick: () -> Unit
) {
    val itemCount = range.last - range.first + 1
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = Int.MAX_VALUE / 2 - (Int.MAX_VALUE / 2 % itemCount) + (initialValue - range.first))
    val snappingLayout = rememberSnapFlingBehavior(lazyListState = listState)

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }
            .map { layoutInfo ->
                val viewportHeight = layoutInfo.viewportEndOffset + layoutInfo.viewportStartOffset
                val center = viewportHeight / 2
                layoutInfo.visibleItemsInfo
                    .minByOrNull { Math.abs(it.offset + it.size / 2 - center) }
                    ?.index
            }
            .filterNotNull()
            .distinctUntilChanged()
            .map { (it % itemCount) + range.first }
            .collect { onValueChange(it) }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Overlay for highlight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), MaterialTheme.shapes.medium)
        )

        LazyColumn(
            state = listState,
            flingBehavior = snappingLayout,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { onDoubleClick() }
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = 50.dp)
        ) {
            items(Int.MAX_VALUE) { index ->
                val value = (index % itemCount) + range.first
                val isSelected = remember(listState) {
                    derivedStateOf {
                        val layoutInfo = listState.layoutInfo
                        val viewportHeight = layoutInfo.viewportEndOffset + layoutInfo.viewportStartOffset
                        val center = viewportHeight / 2
                        val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == index }
                        itemInfo != null && Math.abs(itemInfo.offset + itemInfo.size / 2 - center) < itemInfo.size / 2
                    }
                }
                
                Text(
                    text = if (itemCount > 12) value.toString().padStart(2, '0') else value.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .graphicsLayer {
                            // Optional: add some scaling or alpha based on distance from center
                        },
                    color = if (isSelected.value)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
}

fun formatSelectedDays(days: List<Int>): String {
    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    if (days.size == 7) {
        return ""
    }
    return days.sorted()
        .mapNotNull { dayIndex -> dayNames.getOrNull(dayIndex - 1) }
        .joinToString(", ")
}
