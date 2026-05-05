package com.github.antonizasadni.calendaria.mainActivity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.github.antonizasadni.calendaria.completionList.FileManagement
import com.github.antonizasadni.calendaria.tasks.DailyPlan
import com.github.antonizasadni.calendaria.tasks.ImportantTask
import com.github.antonizasadni.calendaria.tasks.RepetitiveTask
import com.github.antonizasadni.calendaria.tasks.TaskManagement
import com.github.antonizasadni.calendaria.ui.theme.CalendAriaTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.github.antonizasadni.calendaria.notifications.NotificationHelper
import com.github.antonizasadni.calendaria.notifications.ReminderManager
import com.github.antonizasadni.calendaria.tasks.DataMigrationManager

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            NotificationHelper.createNotificationChannel(this)
            ReminderManager.scheduleReminders(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DataMigrationManager.checkAndMigrate(this)
        FileManagement.archivePastYearsTasks(this)
        TaskManagement.checkAndResetDailyTasks(this)
        
        checkNotificationPermission()
        NotificationHelper.createNotificationChannel(this)
        ReminderManager.scheduleReminders(this)

        enableEdgeToEdge()
        setContent {
            CalendAriaTheme {
                CalendAriaApp()
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendAriaApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val today = remember { LocalDate.now() }

    var currentScreen by remember { mutableStateOf("calendar") }
    var selectedMonth by remember { mutableIntStateOf(today.monthValue) }
    var selectedYear by remember { mutableIntStateOf(today.year) }
    var showAddTaskDialog by remember { mutableStateOf(false) }

    val repetitiveTasks = remember {
        mutableStateListOf<RepetitiveTask>().apply {
            addAll(TaskManagement.loadRepetitiveTasks(context))
        }
    }
    val importantTasks = remember {
        mutableStateListOf<ImportantTask>().apply {
            addAll(TaskManagement.loadImportantTasks(context))
        }
    }
    val dailyPlans = remember {
        mutableStateListOf<DailyPlan>().apply {
            addAll(TaskManagement.loadDailyPlans(context))
        }
    }

    fun refreshTasks() {
        repetitiveTasks.clear()
        repetitiveTasks.addAll(TaskManagement.loadRepetitiveTasks(context))
        importantTasks.clear()
        importantTasks.addAll(TaskManagement.loadImportantTasks(context))
        dailyPlans.clear()
        dailyPlans.addAll(TaskManagement.loadDailyPlans(context))
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                currentScreen = currentScreen,
                onNavigate = { screen ->
                    currentScreen = screen
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        AppScaffold(
            currentScreen = currentScreen,
            onMenuClick = { scope.launch { drawerState.open() } },
            onTodayClick = {
                selectedMonth = today.monthValue
                selectedYear = today.year
            },
            onFabClick = { showAddTaskDialog = true }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                NavigationHost(
                    currentScreen = currentScreen,
                    repetitiveTasks = repetitiveTasks,
                    importantTasks = importantTasks,
                    dailyPlans = dailyPlans,
                    selectedMonth = selectedMonth,
                    selectedYear = selectedYear,
                    today = today,
                    showAddTaskDialog = showAddTaskDialog,
                    onDismissDialog = { showAddTaskDialog = false },
                    onMonthYearChange = { m, y -> selectedMonth = m; selectedYear = y },
                    onTasksChanged = { refreshTasks() }
                )
            }
        }
    }

    if (showAddTaskDialog) {
        TaskDialogHandler(
            currentScreen = currentScreen,
            onDismiss = { showAddTaskDialog = false },
            onTaskCreated = {
                refreshTasks()
                showAddTaskDialog = false
            }
        )
    }
}
