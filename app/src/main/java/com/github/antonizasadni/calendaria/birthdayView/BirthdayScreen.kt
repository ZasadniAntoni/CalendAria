package com.github.antonizasadni.calendaria.birthdayView

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.antonizasadni.calendaria.notifications.ReminderManager
import com.github.antonizasadni.calendaria.repetitiveView.CustomClockDialog
import com.github.antonizasadni.calendaria.tasks.Birthday
import com.github.antonizasadni.calendaria.tasks.SearchableTopBar
import com.github.antonizasadni.calendaria.tasks.TaskManagement
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun BirthdayScreen(
    birthdays: MutableList<Birthday>,
    onBirthdaysChanged: () -> Unit
) {
    val context = LocalContext.current
    var editingBirthday by remember { mutableStateOf<Birthday?>(null) }
    var birthdayToDelete by remember { mutableStateOf<Birthday?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val birthdayGroups by remember(searchQuery, birthdays) {
        derivedStateOf {
            val today = LocalDate.now()
            val filtered = birthdays.filter { it.name.contains(searchQuery, ignoreCase = true) }
            
            val (upcoming, passed) = filtered.partition { birthday ->
                val date = parseDate(birthday.date)
                val bdayThisYear = date.withYear(today.year)
                !bdayThisYear.isBefore(today)
            }

            val sortCriteria = compareBy<Birthday> { parseDate(it.date).monthValue }
                .thenBy { parseDate(it.date).dayOfMonth }

            upcoming.sortedWith(sortCriteria) to passed.sortedWith(sortCriteria)
        }
    }

    val (upcomingBirthdays, passedBirthdays) = birthdayGroups

    Column(modifier = Modifier.fillMaxSize()) {
        SearchableTopBar(
            title = "Birthdays",
            searchQuery = searchQuery,
            onQueryChange = { searchQuery = it },
            onFilterClick = { /* Not needed for birthdays yet */ }
        )

        if (birthdays.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No birthdays added yet.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(upcomingBirthdays, key = { it.id }) { birthday ->
                    BirthdayItem(
                        birthday = birthday,
                        onClick = { editingBirthday = birthday }
                    )
                }

                if (passedBirthdays.isNotEmpty()) {
                    item {
                        Text(
                            text = "Celebrate in the following year:",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(passedBirthdays, key = { it.id }) { birthday ->
                        BirthdayItem(
                            birthday = birthday,
                            isPassed = true,
                            onClick = { editingBirthday = birthday }
                        )
                    }
                }
            }
        }
    }

    if (editingBirthday != null) {
        AddOrEditBirthdayDialog(
            existingBirthday = editingBirthday,
            onDismiss = { editingBirthday = null },
            onConfirm = { updated ->
                val index = birthdays.indexOfFirst { it.id == updated.id }
                if (index != -1) birthdays[index] = updated else birthdays.add(updated)
                TaskManagement.saveBirthdays(context, birthdays)
                ReminderManager.scheduleBirthday(context, updated)
                onBirthdaysChanged()
                editingBirthday = null
            },
            onDelete = {
                birthdayToDelete = editingBirthday
                editingBirthday = null
            }
        )
    }

    if (birthdayToDelete != null) {
        AlertDialog(
            onDismissRequest = { birthdayToDelete = null },
            title = { Text("Delete Birthday") },
            text = { Text("Are you sure you want to remove ${birthdayToDelete?.name}'s birthday?") },
            confirmButton = {
                Button(
                    onClick = {
                        val toCancel = birthdayToDelete
                        birthdays.removeIf { it.id == birthdayToDelete?.id }
                        TaskManagement.saveBirthdays(context, birthdays)
                        toCancel?.let { ReminderManager.cancelBirthday(context, it) }
                        onBirthdaysChanged()
                        birthdayToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { birthdayToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun BirthdayItem(
    birthday: Birthday,
    isPassed: Boolean = false,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val date = parseDate(birthday.date)
    val today = LocalDate.now()
    var nextBirthday = date.withYear(today.year)
    if (nextBirthday.isBefore(today)) nextBirthday = nextBirthday.plusYears(1)
    
    val age = if (birthday.date.length > 5) {
        today.year - date.year + (if (nextBirthday.year > today.year) -1 else 0)
    } else -1

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isPassed) Color.Gray.copy(alpha = 0.1f) 
                            else Color(birthday.color).copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp).alpha(if (isPassed) 0.5f else 1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(birthday.color)),
                contentAlignment = Alignment.Center
            ) {
                val icon = when (birthday.type) {
                    "Name Day" -> "💐"
                    else -> "🎂"
                }
                Text(icon, fontSize = 32.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(birthday.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                val birthdayText = when (birthday.type) {
                    "Name Day" -> "Name Day on ${date.format(DateTimeFormatter.ofPattern("MMMM d"))}"
                    else -> {
                        if (isPassed || age < 0) "Birthday on ${date.format(DateTimeFormatter.ofPattern("MMMM d"))}"
                        else "Turns ${age + 1} on ${date.format(DateTimeFormatter.ofPattern("MMMM d"))}"
                    }
                }
                Text(text = birthdayText, style = MaterialTheme.typography.bodySmall)
                
                if (birthday.notificationsEnabled) {
                    Text(
                        text = "Reminder: ${TaskManagement.formatTime(context, birthday.reminderTime)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddOrEditBirthdayDialog(
    existingBirthday: Birthday? = null,
    onDismiss: () -> Unit,
    onConfirm: (Birthday) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(existingBirthday?.name ?: "") }
    var dateStr by remember { mutableStateOf(existingBirthday?.date ?: LocalDate.now().toString()) }
    var birthdayType by remember { mutableStateOf(existingBirthday?.type ?: "Birthday") }
    var notificationsEnabled by remember { mutableStateOf(existingBirthday?.notificationsEnabled ?: true) }
    var reminderTime by remember { mutableStateOf(existingBirthday?.reminderTime ?: "08:00 AM") }
    
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = try {
            LocalDate.parse(dateStr).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) { 
            System.currentTimeMillis() 
        }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingBirthday == null) "New Event" else "Edit Event") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val options = listOf("Birthday", "Name Day")
                    options.forEachIndexed { index, label ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            onClick = { birthdayType = label },
                            selected = birthdayType == label,
                            icon = {
                                SegmentedButtonDefaults.Icon(active = birthdayType == label) {
                                    Icon(
                                        imageVector = if (label == "Birthday") Icons.Default.Cake else Icons.Default.LocalFlorist,
                                        contentDescription = null,
                                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                                    )
                                }
                            }
                        ) {
                            Text(label)
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Person's Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = try {
                        val d = LocalDate.parse(dateStr)
                        d.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                    } catch (_: Exception) { dateStr },
                    onValueChange = { },
                    label = { 
                        Text(
                            when (birthdayType) {
                                "Name Day" -> "Name Day Date"
                                else -> "Birth Date"
                            }
                        ) 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            val icon = when (birthdayType) {
                                "Name Day" -> Icons.Default.LocalFlorist
                                else -> Icons.Default.Cake
                            }
                            Icon(icon, contentDescription = null)
                        }
                    }
                )

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

                if (notificationsEnabled) {
                    OutlinedTextField(
                        value = TaskManagement.formatTime(context, reminderTime),
                        onValueChange = { },
                        label = { Text("Reminder Time") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { showTimePicker = true }) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                
                Button(
                    enabled = name.isNotBlank(),
                    onClick = {
                        onConfirm(
                            Birthday(
                                id = existingBirthday?.id ?: UUID.randomUUID().toString(),
                                name = name,
                                date = dateStr,
                                type = birthdayType,
                                notificationsEnabled = notificationsEnabled,
                                reminderTime = reminderTime,
                                color = when (birthdayType) {
                                    "Name Day" -> 0xFF435B43
                                    else -> 0xFFFFD700
                                }
                            )
                        )
                    }
                ) { Text("Save") }
            }
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
                        dateStr = Instant.ofEpochMilli(millis)
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
            initialTime = reminderTime,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute, amPm ->
                reminderTime = TaskManagement.convertToUniformTime(hour, minute, amPm)
                showTimePicker = false
            }
        )
    }
}

private fun parseDate(dateStr: String): LocalDate {
    return try {
        LocalDate.parse(dateStr)
    } catch (_: Exception) {
        try {
            LocalDate.parse("$dateStr.2000", DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        } catch (_: Exception) {
            LocalDate.now()
        }
    }
}
