package com.github.antonizasadni.calendaria.DailyPlanView

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.colorResource
import com.github.antonizasadni.calendaria.R
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.animation.*
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import com.github.antonizasadni.calendaria.repetitiveView.CustomClockDialog
import com.github.antonizasadni.calendaria.tasks.DailyPlan
import com.github.antonizasadni.calendaria.tasks.ImportantTask
import com.github.antonizasadni.calendaria.tasks.RepetitiveTask
import com.github.antonizasadni.calendaria.tasks.TaskManagement
import com.github.antonizasadni.calendaria.completionList.FileManagement
import com.github.antonizasadni.calendaria.importantView.ViewImportantTaskDialog
import com.github.antonizasadni.calendaria.repetitiveView.ViewRepetitiveTaskDialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed class UnifiedTask {
    data class Plan(val dailyPlan: DailyPlan) : UnifiedTask()
    data class Repetitive(val task: RepetitiveTask, val completed: Boolean) : UnifiedTask()
    data class Important(val task: ImportantTask) : UnifiedTask()

    val id: String get() = when(this) {
        is Plan -> dailyPlan.id
        is Repetitive -> task.id
        is Important -> task.id
    }
    val title: String get() = when(this) {
        is Plan -> dailyPlan.title
        is Repetitive -> task.title
        is Important -> task.title
    }
    val time: String get() = when(this) {
        is Plan -> dailyPlan.time
        is Repetitive -> task.time
        is Important -> task.taskTime
    }
    val isCompleted: Boolean get() = when(this) {
        is Plan -> dailyPlan.isCompleted
        is Repetitive -> completed
        is Important -> task.isCompleted
    }
    val durationMinutes: Int get() = when(this) {
        is Plan -> dailyPlan.durationMinutes
        is Repetitive -> task.durationMinutes
        is Important -> task.durationMinutes
    }
    fun getColor(importantColor: Long, repetitiveColor: Long): Long = when(this) {
        is Plan -> dailyPlan.color
        is Repetitive -> repetitiveColor
        is Important -> importantColor
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyPlanScreen(
    dailyPlans: MutableList<DailyPlan>,
    repetitiveTasks: List<RepetitiveTask>,
    importantTasks: List<ImportantTask>,
    showAddDialog: Boolean,
    onDismissDialog: () -> Unit,
    onPlansChanged: () -> Unit
) {
    val context = LocalContext.current
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var planToEdit by remember { mutableStateOf<DailyPlan?>(null) }
    var viewingRepetitive by remember { mutableStateOf<RepetitiveTask?>(null) }
    var viewingImportant by remember { mutableStateOf<ImportantTask?>(null) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )

    val importantColor = colorResource(R.color.plan_red).toArgb().toLong()
    val repetitiveColor = colorResource(R.color.plan_navy).toArgb().toLong()

    val allTasks by remember(selectedDate, dailyPlans, repetitiveTasks, importantTasks) {
        derivedStateOf {
            val dateStr = selectedDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            val dayOfWeek = selectedDate.dayOfWeek.value

            val plans = dailyPlans.filter { it.date == dateStr }.map { UnifiedTask.Plan(it) }
            
            val repetitive = repetitiveTasks
                .filter { it.selectedDays.contains(dayOfWeek) }
                .map { task ->
                    val history = FileManagement.loadHistory(context, task.id, selectedDate)
                    val completed = history[selectedDate.dayOfMonth] ?: false
                    UnifiedTask.Repetitive(task, completed)
                }

            val important = importantTasks
                .filter { it.taskDate == dateStr }
                .map { UnifiedTask.Important(it) }

            (plans + repetitive + important)
        }
    }

    val wholeDayTasks = allTasks.filter { it.time == "Whole Day" }
    val timedTasks = allTasks.filter { it.time != "Whole Day" }

    val hourHeight = 80.dp

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            IconButton(onClick = { showDatePicker = true }) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Select Date",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            IconButton(onClick = { selectedDate = selectedDate.minusDays(1) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous Day",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )

            IconButton(onClick = { selectedDate = selectedDate.plusDays(1) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next Day",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (wholeDayTasks.isNotEmpty()) {
            Text("Whole Day Tasks", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                wholeDayTasks.forEach { task ->
                    UnifiedTaskItem(
                        task = task,
                        importantColor = importantColor,
                        repetitiveColor = repetitiveColor,
                        onToggle = {
                            when(task) {
                                is UnifiedTask.Plan -> TaskManagement.toggleDailyPlanCompletion(context, task.id)
                                is UnifiedTask.Repetitive -> FileManagement.toggleDay(context, task.id, selectedDate)
                                is UnifiedTask.Important -> TaskManagement.toggleImportantTaskCompletion(context, task.id)
                            }
                            onPlansChanged()
                        },
                        onClick = {
                            when(task) {
                                is UnifiedTask.Plan -> planToEdit = task.dailyPlan
                                is UnifiedTask.Repetitive -> viewingRepetitive = task.task
                                is UnifiedTask.Important -> viewingImportant = task.task
                            }
                        }
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // Timeline Hours
            Column {
                for (hour in 0..23) {
                    Box(modifier = Modifier.height(hourHeight).fillMaxWidth()) {
                        Text(
                            text = TaskManagement.formatTime(context, String.format(Locale.US, "%02d:00", hour)),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        // Quarter hour lines
                        for (quarter in 0..3) {
                            HorizontalDivider(
                                modifier = Modifier
                                    .padding(top = (hourHeight / 4) * quarter)
                                    .padding(start = 40.dp),
                                thickness = if (quarter == 0) 1.dp else 0.5.dp,
                                color = if (quarter == 0) Color.LightGray else Color.LightGray.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(hourHeight))
            }

            // Layout Logic for Timed Tasks
            val tasksWithLayout = remember(timedTasks) {
                if (timedTasks.isEmpty()) return@remember emptyList<Triple<UnifiedTask, Int, Int>>()

                val sorted = timedTasks.map { task ->
                    val start = TaskManagement.parseTimeToMinutes(task.time)
                    val duration = task.durationMinutes
                    task to (start to (start + duration))
                }.filter { it.second.first >= 0 }
                    .sortedWith(compareBy({ it.second.first }, { -it.second.second }))

                val groups = mutableListOf<MutableList<Pair<UnifiedTask, Pair<Int, Int>>>>()
                var currentGroup = mutableListOf<Pair<UnifiedTask, Pair<Int, Int>>>()
                var currentGroupEnd = -1

                for (item in sorted) {
                    if (item.second.first >= currentGroupEnd) {
                        if (currentGroup.isNotEmpty()) groups.add(currentGroup)
                        currentGroup = mutableListOf(item)
                        currentGroupEnd = item.second.second
                    } else {
                        currentGroup.add(item)
                        currentGroupEnd = maxOf(currentGroupEnd, item.second.second)
                    }
                }
                if (currentGroup.isNotEmpty()) groups.add(currentGroup)

                groups.flatMap { group ->
                    val columns = mutableListOf<Int>()
                    val placedItems = group.map { item ->
                        var columnIndex = -1
                        for (i in columns.indices) {
                            if (item.second.first >= columns[i]) {
                                columnIndex = i
                                columns[i] = item.second.second
                                break
                            }
                        }
                        if (columnIndex == -1) {
                            columnIndex = columns.size
                            columns.add(item.second.second)
                        }
                        item.first to columnIndex
                    }
                    val totalCols = columns.size
                    placedItems.map { (task, colIndex) -> Triple(task, colIndex, totalCols) }
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(hourHeight * 24)
                    .padding(start = 45.dp)
            ) {
                val containerWidth = this.maxWidth
                tasksWithLayout.forEach { (task, columnIndex, totalColumns) ->
                    val startMinutes = TaskManagement.parseTimeToMinutes(task.time)
                    val duration = task.durationMinutes
                    val topOffset = (hourHeight.value * (startMinutes / 60f)).dp
                    val height = (hourHeight.value * (duration / 60f)).dp
                    
                    val itemWidth = containerWidth / totalColumns
                    val xOffset = itemWidth * columnIndex

                    val taskColor = Color(task.getColor(importantColor, repetitiveColor))
                    
                    Card(
                        modifier = Modifier
                            .offset(x = xOffset, y = topOffset)
                            .width(itemWidth - 2.dp)
                            .height(height - 1.dp)
                            .zIndex(columnIndex.toFloat() + 1f)
                            .clickable { 
                                when(task) {
                                    is UnifiedTask.Plan -> planToEdit = task.dailyPlan
                                    is UnifiedTask.Repetitive -> viewingRepetitive = task.task
                                    is UnifiedTask.Important -> viewingImportant = task.task
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (task.isCompleted)
                                taskColor.copy(alpha = 0.5f)
                            else
                                taskColor
                        ),
                        border = if (task.isCompleted) null else androidx.compose.foundation.BorderStroke(1.dp, taskColor.copy(alpha = 0.8f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (totalColumns <= 2 || itemWidth > 60.dp) {
                                Checkbox(
                                    checked = task.isCompleted,
                                    onCheckedChange = {
                                        when(task) {
                                            is UnifiedTask.Plan -> TaskManagement.toggleDailyPlanCompletion(context, task.id)
                                            is UnifiedTask.Repetitive -> FileManagement.toggleDay(context, task.id, selectedDate)
                                            is UnifiedTask.Important -> TaskManagement.toggleImportantTaskCompletion(context, task.id)
                                        }
                                        onPlansChanged()
                                    },
                                    modifier = Modifier.scale(0.7f)
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .padding(start = if (totalColumns <= 2 || itemWidth > 60.dp) 0.dp else 4.dp)
                                    .weight(1f)
                            ) {
                                Text(
                                    text = task.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (totalColumns > 2) 9.sp else 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                    color = Color.White
                                )
                                if (height >= 30.dp && itemWidth > 40.dp) {
                                    Text(
                                        text = "${TaskManagement.formatTime(context, task.time)} (${task.durationMinutes} min)",
                                        fontSize = 8.sp,
                                        color = if (task.isCompleted) Color.LightGray else Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
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

    if (showAddDialog) {
        AddOrEditDailyPlanDialog(
            date = selectedDate,
            onDismiss = onDismissDialog,
            onConfirm = { newPlan ->
                dailyPlans.add(newPlan)
                TaskManagement.saveDailyPlans(context, dailyPlans.toList())
                com.github.antonizasadni.calendaria.notifications.ReminderManager.scheduleDailyPlan(context, newPlan)
                onPlansChanged()
                onDismissDialog()
            }
        )
    }

    if (planToEdit != null) {
        AddOrEditDailyPlanDialog(
            date = selectedDate,
            existingPlan = planToEdit,
            onDismiss = { planToEdit = null },
            onConfirm = { updatedPlan ->
                val index = dailyPlans.indexOfFirst { it.id == updatedPlan.id }
                if (index != -1) {
                    dailyPlans[index] = updatedPlan
                } else {
                    dailyPlans.add(updatedPlan)
                }
                TaskManagement.saveDailyPlans(context, dailyPlans.toList())
                com.github.antonizasadni.calendaria.notifications.ReminderManager.scheduleDailyPlan(context, updatedPlan)
                onPlansChanged()
                planToEdit = null
            },
            onDelete = {
                val planToDelete = planToEdit
                dailyPlans.removeIf { it.id == planToEdit?.id }
                TaskManagement.saveDailyPlans(context, dailyPlans.toList())
                planToDelete?.let { com.github.antonizasadni.calendaria.notifications.ReminderManager.cancelDailyPlan(context, it) }
                onPlansChanged()
                planToEdit = null
            }
        )
    }

    if (viewingRepetitive != null) {
        ViewRepetitiveTaskDialog(
            task = viewingRepetitive!!,
            date = selectedDate,
            onDismiss = { viewingRepetitive = null },
            onEdit = null,
            onDeleteRequest = { viewingRepetitive = null },
            onToggleComplete = { date ->
                FileManagement.toggleDay(context, viewingRepetitive!!.id, date)
                onPlansChanged()
            }
        )
    }

    if (viewingImportant != null) {
        ViewImportantTaskDialog(
            task = viewingImportant!!,
            onDismiss = { viewingImportant = null },
            onEdit = null,
            onDeleteRequest = { viewingImportant = null },
            onToggleComplete = {
                TaskManagement.toggleImportantTaskCompletion(context, viewingImportant!!.id)
                onPlansChanged()
                viewingImportant = viewingImportant!!.copy(isCompleted = !viewingImportant!!.isCompleted)
            }
        )
    }
}

@Composable
fun UnifiedTaskItem(
    task: UnifiedTask,
    importantColor: Long,
    repetitiveColor: Long,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val taskColor = Color(task.getColor(importantColor, repetitiveColor))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted)
                taskColor.copy(alpha = 0.3f)
            else
                taskColor
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggle() }
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    text = task.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (task.isCompleted) Color.Gray else Color.White
                )
                Text(
                    text = TaskManagement.formatTime(context, task.time),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.isCompleted) Color.Gray else Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddOrEditDailyPlanDialog(
    date: LocalDate,
    existingPlan: DailyPlan? = null,
    onDismiss: () -> Unit,
    onConfirm: (DailyPlan) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(existingPlan?.title ?: "") }
    var time by remember { mutableStateOf(existingPlan?.time ?: "Whole Day") }
    var durationMinutes by remember { mutableIntStateOf(existingPlan?.durationMinutes?.takeIf { it > 0 } ?: 60) }
    val colorResIds = listOf(
        R.color.plan_pink,
        R.color.plan_orange,
        R.color.plan_brown,
        R.color.plan_grey,
        R.color.plan_purple,
        R.color.plan_teal,
        R.color.plan_green
    )

    val colors = colorResIds.map { colorResource(it).toArgb().toLong() }
    var selectedColor by remember { mutableLongStateOf(existingPlan?.color ?: colors[0]) }
    var notificationsEnabled by remember { mutableStateOf(existingPlan?.notificationsEnabled ?: true) }
    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingPlan == null) "New Daily Plan" else "Edit Daily Plan") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("What needs to be done?") },
                    modifier = Modifier.fillMaxWidth()
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

                Text("Select Color", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    colors.forEach { colorVal ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(colorVal), CircleShape)
                                .border(
                                    width = if (selectedColor == colorVal) 2.dp else 0.dp,
                                    color = if (selectedColor == colorVal) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = colorVal }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { notificationsEnabled = !notificationsEnabled }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tint by animateColorAsState(
                        if (notificationsEnabled) MaterialTheme.colorScheme.primary else Color.Gray,
                        label = "bellTint"
                    )

                    AnimatedContent(
                        targetState = notificationsEnabled,
                        transitionSpec = {
                            (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut())
                        },
                        label = "bellAnimation"
                    ) { enabled ->
                        Icon(
                            imageVector = if (enabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = if (notificationsEnabled) "Notification ON" else "Notification OFF",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = tint
                    )
                }
            }
        },
        confirmButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Delete")
                    }
                    Spacer(Modifier.weight(1f))
                }
                Button(
                    enabled = title.isNotBlank(),
                    onClick = {
                        onConfirm(
                            DailyPlan(
                                id = existingPlan?.id ?: java.util.UUID.randomUUID().toString(),
                                title = title,
                                time = time,
                                durationMinutes = durationMinutes,
                                date = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                                isCompleted = existingPlan?.isCompleted ?: false,
                                color = selectedColor,
                                notificationsEnabled = notificationsEnabled
                            )
                        )
                    }
                ) { Text(if (existingPlan == null) "Add" else "Save") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

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
