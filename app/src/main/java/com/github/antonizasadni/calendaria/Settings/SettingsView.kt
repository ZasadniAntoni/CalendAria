package com.github.antonizasadni.calendaria.Settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import android.widget.Toast
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
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
import androidx.core.content.ContextCompat
import com.github.antonizasadni.calendaria.notifications.NotificationHelper
import com.github.antonizasadni.calendaria.notifications.ReminderManager
import com.github.antonizasadni.calendaria.repetitiveView.CustomClockDialog
import com.github.antonizasadni.calendaria.tasks.ImportantTask
import com.github.antonizasadni.calendaria.tasks.TaskManagement
import java.util.Calendar

@Composable
fun SettingsScreen() {
    var showDebugScreen by remember { mutableStateOf(false) }

    if (showDebugScreen) {
        DebugSettings(onBack = { showDebugScreen = false })
    } else {
        MainSettings(onShowDebug = { showDebugScreen = true })
    }
}

@Composable
fun MainSettings(onShowDebug: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("CalendAriaPrefs", Context.MODE_PRIVATE) }
    
    val hasNotificationPermission = remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    var currentEnabled by remember { mutableStateOf(prefs.getBoolean("reminders_current_week_enabled", true)) }
    var previousEnabled by remember { mutableStateOf(prefs.getBoolean("reminders_previous_weeks_enabled", true)) }
    var importantNotificationsEnabled by remember { mutableStateOf(prefs.getBoolean("important_tasks_notifications_enabled", true)) }
    var use24HourFormat by remember { mutableStateOf(prefs.getBoolean("use_24h_format", false)) }

    var reminderDay by remember { mutableIntStateOf(prefs.getInt("reminders_day", Calendar.SUNDAY)) }
    var reminderTime by remember { mutableStateOf(prefs.getString("reminders_time", "08:00 AM") ?: "08:00 AM") }

    fun updateReminders() {
        ReminderManager.scheduleReminders(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Time",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                // Time Format Toggle
                Row(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Use 24-Hour Format",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Switch between 12-hour (AM/PM) and 24-hour clock.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = use24HourFormat,
                        onCheckedChange = { enabled ->
                            use24HourFormat = enabled
                            prefs.edit().putBoolean("use_24h_format", enabled).apply()
                        }
                    )
                }
            }
        }

        Text(
            text = "Reminders",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        
        val alpha = if (hasNotificationPermission.value) 1f else 0.5f

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(alpha),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    // Current Week Toggle
                    Row(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Current Week Summary",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Remind me to finish this week's summaries.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = currentEnabled,
                            enabled = hasNotificationPermission.value,
                            onCheckedChange = { enabled ->
                                currentEnabled = enabled
                                prefs.edit().putBoolean("reminders_current_week_enabled", enabled).apply()
                                updateReminders()
                            }
                        )
                    }

                    // Previous Weeks Toggle
                    Row(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Past Weeks Summaries",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Remind me about missing summaries from previous weeks.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = previousEnabled,
                            enabled = hasNotificationPermission.value,
                            onCheckedChange = { enabled ->
                                previousEnabled = enabled
                                prefs.edit().putBoolean("reminders_previous_weeks_enabled", enabled).apply()
                                updateReminders()
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

                    // Important Tasks Toggle
                    Row(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Important Task Notifications",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Get notified about pending important tasks at their due time.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = importantNotificationsEnabled,
                            enabled = hasNotificationPermission.value,
                            onCheckedChange = { enabled ->
                                importantNotificationsEnabled = enabled
                                prefs.edit().putBoolean("important_tasks_notifications_enabled", enabled).apply()
                                updateReminders()
                            }
                        )
                    }
                }
            }

            if (currentEnabled || previousEnabled) {
                Text(
                    text = "Weekly Reminder Schedule",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        ReminderConfig(
                            day = reminderDay,
                            time = reminderTime,
                            enabled = hasNotificationPermission.value,
                            onDayChange = { d ->
                                reminderDay = d
                                prefs.edit().putInt("reminders_day", d).apply()
                                updateReminders()
                            },
                            onTimeChange = { t ->
                                reminderTime = t
                                prefs.edit().putString("reminders_time", t).apply()
                                updateReminders()
                            }
                        )
                    }
                }
            }
        }

        if (!hasNotificationPermission.value) {
            Text(
                text = "Please turn on notifications permission in system settings to use reminders.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        var showClearDialog by remember { mutableStateOf(false) }
        
        Text(
            text = "Data Management",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showClearDialog = true },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Clean Your Data",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Permanently delete all tasks, plans, and history. This cannot be undone.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        }

        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Clear All Data?") },
                text = { Text("This will permanently delete all your habits, important tasks, daily plans, and completion history. Are you sure?") },
                confirmButton = {
                    Button(
                        onClick = {
                            TaskManagement.clearAllData(context)
                            showClearDialog = false
                            Toast.makeText(context, "All data has been cleared.", Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete Everything", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onShowDebug() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Debug Actions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Test different actions of the app.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Icon(Icons.Default.BugReport, contentDescription = null)
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            text = "App Version: 1.0.0",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val importantTasks = remember { TaskManagement.loadImportantTasks(context) }
    var selectedTask by remember { mutableStateOf<ImportantTask?>(null) }
    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Actions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Summary Reminders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    Button(
                        onClick = {
                            val intent = android.content.Intent(context, com.github.antonizasadni.calendaria.notifications.ReminderReceiver::class.java).apply {
                                putExtra("type", "current")
                            }
                            context.sendBroadcast(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Test Current Week Summary")
                    }

                    Button(
                        onClick = {
                            val intent = android.content.Intent(context, com.github.antonizasadni.calendaria.notifications.ReminderReceiver::class.java).apply {
                                putExtra("type", "previous")
                            }
                            context.sendBroadcast(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Test Previous Weeks Summaries")
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Important Task Reminder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedTask?.title ?: "Select a task",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Task to test") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            importantTasks.forEach { task ->
                                DropdownMenuItem(
                                    text = { Text(task.title) },
                                    onClick = {
                                        selectedTask = task
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            selectedTask?.let { task ->
                                NotificationHelper.showNotification(
                                    context,
                                    "Debug: Pending Task",
                                    task.description.ifBlank { task.title }
                                )
                            }
                        },
                        enabled = selectedTask != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Test Important Task Reminder")
                    }
                }
            }
        }
    }
}

@Composable
fun ReminderConfig(
    day: Int,
    time: String,
    enabled: Boolean,
    onDayChange: (Int) -> Unit,
    onTimeChange: (String) -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }
    
    val daysLabels = listOf("M", "T", "W", "T", "F", "S", "S")
    val dayValues = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)

    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text("Select Day:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            daysLabels.forEachIndexed { index, label ->
                val dayValue = dayValues[index]
                val isSelected = day == dayValue
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable(enabled = enabled) { onDayChange(dayValue) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = { showTimePicker = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(12.dp)
        ) {
            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            val context = LocalContext.current
            Text("Reminder Time: ${TaskManagement.formatTime(context, time)}", style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (showTimePicker && enabled) {
        CustomClockDialog(
            initialTime = time,
            onDismiss = { showTimePicker = false },
            onConfirm = { h, m, amPm ->
                onTimeChange(TaskManagement.convertToUniformTime(h, m, amPm))
                showTimePicker = false
            }
        )
    }
}
