package com.github.antonizasadni.calendaria.mainActivity

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.github.antonizasadni.calendaria.ArchiveData.ArchiveDataScreen
import com.github.antonizasadni.calendaria.DailyPlanView.DailyPlanScreen
import com.github.antonizasadni.calendaria.Settings.SettingsScreen
import com.github.antonizasadni.calendaria.calendarView.CalendarScreen
import com.github.antonizasadni.calendaria.completionList.CompletionListScreen
import com.github.antonizasadni.calendaria.importantView.ImportantTasksScreen
import com.github.antonizasadni.calendaria.importantView.AddImportantTaskDialog
import com.github.antonizasadni.calendaria.repetitiveView.RepetitiveTasksScreen
import com.github.antonizasadni.calendaria.repetitiveView.AddRepetitiveTaskDialog
import com.github.antonizasadni.calendaria.tasks.ImportantTask
import com.github.antonizasadni.calendaria.tasks.TaskManagement
import com.github.antonizasadni.calendaria.tasks.RepetitiveTask
import com.github.antonizasadni.calendaria.tasks.DailyPlan
import com.github.antonizasadni.calendaria.R
import java.time.LocalDate

@Composable
fun NavigationHost(
    currentScreen: String,
    repetitiveTasks: MutableList<RepetitiveTask>,
    importantTasks: MutableList<ImportantTask>,
    dailyPlans: MutableList<DailyPlan>,
    selectedMonth: Int,
    selectedYear: Int,
    today: LocalDate,
    showAddTaskDialog: Boolean,
    onDismissDialog: () -> Unit,
    onMonthYearChange: (Int, Int) -> Unit,
    onTasksChanged: () -> Unit
) {
    when (currentScreen) {
        "calendar" -> CalendarScreen(selectedMonth, selectedYear, today, repetitiveTasks, importantTasks, onMonthYearChange, onTasksChanged)
        "important" -> ImportantTasksScreen(importantTasks, onTasksChanged)
        "repetitive" -> RepetitiveTasksScreen(repetitiveTasks, onTasksChanged)
        "completion" -> CompletionListScreen(repetitiveTasks, onTasksChanged)
        "dailyPlan" -> DailyPlanScreen(dailyPlans, repetitiveTasks, importantTasks, showAddTaskDialog, onDismissDialog, onTasksChanged)
        "archive" -> ArchiveDataScreen()
        "options" -> SettingsScreen()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    currentScreen: String,
    onMenuClick: () -> Unit,
    onTodayClick: () -> Unit,
    onFabClick: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            val (title, icon) = when (currentScreen) {
                "calendar" -> "Calendar" to Icons.Default.DateRange
                "dailyPlan" -> "Plans" to Icons.Default.Edit
                "important" -> "Important" to Icons.Default.Warning
                "repetitive" -> "Repetitive" to Icons.Default.Refresh
                "completion" -> "Completion" to Icons.Default.CheckCircle
                "archive" -> "Archive" to Icons.Default.Share
                "options" -> "Settings" to Icons.Default.Settings
                else -> currentScreen.replaceFirstChar { it.uppercase() } to null
            }
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, null) }
                },
                actions = {
                    icon?.let {
                        IconButton(onClick = { if (currentScreen == "calendar") onTodayClick() }) {
                            Icon(it, null)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (currentScreen == "repetitive" || currentScreen == "important" || currentScreen == "dailyPlan") {
                FloatingActionButton(onClick = onFabClick) { Icon(Icons.Default.Add, null) }
            }
        },
        content = content
    )
}

@Composable
fun AppDrawerContent(currentScreen: String, onNavigate: (String) -> Unit) {
    val iconSizeDp = 54.dp

    ModalDrawerSheet(modifier = Modifier.fillMaxHeight().fillMaxWidth(0.7f)) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(iconSizeDp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .size(iconSizeDp)
                        .graphicsLayer(
                            scaleX = 1.5f,
                            scaleY = 1.5f
                        ),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "CalendAria",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        val navItems = listOf(
            Triple("calendar", "Calendar View", Icons.Default.DateRange),
            Triple("important", "Important Tasks", Icons.Default.Warning),
            Triple("repetitive", "Repetitive Tasks", Icons.Default.Refresh),
            Triple("completion", "Completion List", Icons.Default.CheckCircle),
            Triple("dailyPlan", "Daily Plan", Icons.Default.Edit)
        )

        navItems.forEach { (route, displayName, icon) ->
            NavigationDrawerItem(
                label = { Text(text = displayName) },
                selected = currentScreen == route,
                icon = { Icon(icon, contentDescription = null) },
                onClick = { onNavigate(route) },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text(
            "Settings",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 28.dp, bottom = 8.dp)
        )
        NavigationDrawerItem(
            label = { Text("Archive Data") },
            selected = currentScreen == "archive",
            icon = { Icon(Icons.Default.Share, contentDescription = null) },
            onClick = { onNavigate("archive") },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        NavigationDrawerItem(
            label = { Text("Settings") },
            selected = currentScreen == "options",
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            onClick = { onNavigate("options") },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun TaskDialogHandler(
    currentScreen: String,
    onDismiss: () -> Unit,
    onTaskCreated: (Any) -> Unit
) {
    val context = LocalContext.current

    when (currentScreen) {
        "repetitive" -> AddRepetitiveTaskDialog(
            onDismiss = onDismiss,
            onConfirm = { newTask ->
                val tasks = TaskManagement.loadRepetitiveTasks(context).toMutableList()
                tasks.add(newTask)
                TaskManagement.saveRepetitiveTasks(context, tasks)
                onTaskCreated(newTask)
            }
        )
        "important" -> AddImportantTaskDialog(
            onDismiss = onDismiss,
            onConfirm = { newTask ->
                val tasks = TaskManagement.loadImportantTasks(context).toMutableList()
                tasks.add(newTask)
                TaskManagement.saveImportantTasks(context, tasks)
                onTaskCreated(newTask)
            }
        )
    }
}
