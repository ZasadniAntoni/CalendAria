package com.github.antonizasadni.calendaria.tasks

import android.content.Context
import com.github.antonizasadni.calendaria.completionList.FileManagement
import com.google.gson.Gson
import java.util.UUID
import java.time.LocalDate

import com.google.gson.annotations.SerializedName

data class RepetitiveTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val selectedDays: List<Int>,
    val time: String = "Whole Day",
    val durationMinutes: Int = 60,
    val type: String = "Repetitive",
    val isCompleted: Boolean = false,
    val createdAt: String = LocalDate.now().toString(),
    val notificationsEnabled: Boolean = true
)

data class ImportantTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    @SerializedName("taskDate", alternate = ["dueDate"])
    val taskDate: String,
    @SerializedName("taskTime", alternate = ["time"])
    val taskTime: String = "Whole Day",
    val durationMinutes: Int = 60,
    val type: String = "Important",
    val isCompleted: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val reminderDate: String? = null,
    val reminderTime: String? = null
)

data class DailyPlan(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val time: String = "Whole Day",
    val durationMinutes: Int = 60,
    val date: String,
    val isCompleted: Boolean = false,
    val color: Long = 0xB21B3A4B, // Toned out Deep Navy (70% alpha)
    val notificationsEnabled: Boolean = true
)

object TaskManagement {
    private const val PREFS_NAME = "CalendAriaPrefs"

    private var repository: DataRepository? = null

    private fun getRepo(context: Context): DataRepository {
        if (repository == null) {
            // Using applicationContext to avoid memory leaks
            repository = DataRepository(context.applicationContext)
            repository?.performInitialMigration()
        }
        return repository!!
    }

    fun saveRepetitiveTasks(context: Context, tasks: List<RepetitiveTask>) {
        getRepo(context).saveRepetitiveTasks(tasks)
    }

    fun loadRepetitiveTasks(context: Context): List<RepetitiveTask> {
        return getRepo(context).loadRepetitiveTasks()
    }

    fun toggleRepetitiveTaskCompletion(context: Context, taskId: String) {
        val today = LocalDate.now()

        FileManagement.toggleDay(context, taskId, today)

        val currentTasks = loadRepetitiveTasks(context).map {
            if (it.id == taskId) it.copy(isCompleted = !it.isCompleted) else it
        }
        saveRepetitiveTasks(context, currentTasks)
    }

    fun saveImportantTasks(context: Context, tasks: List<ImportantTask>) {
        getRepo(context).saveImportantTasks(tasks)
    }

    fun loadImportantTasks(context: Context): List<ImportantTask> {
        return getRepo(context).loadImportantTasks()
    }

    fun toggleImportantTaskCompletion(context: Context, taskId: String) {
        val currentTasks = loadImportantTasks(context).map {
            if (it.id == taskId) it.copy(isCompleted = !it.isCompleted) else it
        }
        saveImportantTasks(context, currentTasks)
    }

    data class DailyUpdateResult(
        val currentDate: LocalDate,
        val tasks: List<RepetitiveTask>
    )
    fun checkAndResetDailyTasks(context: Context): DailyUpdateResult {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastDateStr = prefs.getString("last_launch_date", null)
        val now = LocalDate.now()
        val currentDateStr = now.toString()

        var tasks = loadRepetitiveTasks(context)

        if (lastDateStr != currentDateStr) {
            tasks = tasks.map { it.copy(isCompleted = false) }
            saveRepetitiveTasks(context, tasks)
            prefs.edit().putString("last_launch_date", currentDateStr).apply()
        }

        return DailyUpdateResult(currentDate = now, tasks = tasks)
    }

    fun formatTime(context: Context, time: String): String {
        if (time == "Whole Day" || time.isBlank()) return time

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val use24h = prefs.getBoolean("use_24h_format", false)

        return try {
            val regex = Regex("(\\d+):(\\d+)\\s*(AM|PM)?", RegexOption.IGNORE_CASE)
            val match = regex.find(time) ?: return time

            val (h, m, ap) = match.destructured
            var hour = h.toInt()
            val minute = m.toInt()

            if (use24h) {
                if (ap.isNotBlank()) {
                    if (ap.uppercase() == "PM" && hour < 12) hour += 12
                    if (ap.uppercase() == "AM" && hour == 12) hour = 0
                }
                String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
            } else {
                var displayAmPm = ap.uppercase()
                var displayHour = hour
                if (displayAmPm.isBlank()) {
                    // Convert from 24h to AM/PM
                    displayAmPm = if (hour < 12) "AM" else "PM"
                    displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
                }
                String.format(java.util.Locale.US, "%d:%02d %s", displayHour, minute, displayAmPm)
            }
        } catch (e: Exception) {
            time
        }
    }

    fun convertToUniformTime(hour: Int, minute: Int, amPm: String): String {
        val formattedMinute = minute.toString().padStart(2, '0')
        val finalAmPm = if (amPm.isEmpty()) {
            if (hour < 12) "AM" else "PM"
        } else amPm
        val finalHour = if (amPm.isEmpty()) {
            if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        } else hour
        return "$finalHour:$formattedMinute $finalAmPm"
    }

    fun parseTimeToMinutes(time: String): Int {
        if (time == "Whole Day") return -1

        return try {
            val parts = time.trim().split(" ")
            val clockParts = parts[0].split(":")
            var hours = clockParts[0].toInt()
            val minutes = clockParts[1].toInt()
            val amPm = parts[1].uppercase()

            if (amPm == "PM" && hours < 12) hours += 12
            if (amPm == "AM" && hours == 12) hours = 0

            hours * 60 + minutes
        } catch (e: Exception) {
            Int.MAX_VALUE
        }
    }

    fun isTaskActiveOnDate(task: RepetitiveTask, date: LocalDate): Boolean {
        val createdDate = try {
            LocalDate.parse(task.createdAt)
        } catch (e: Exception) {
            LocalDate.MIN
        }

        return (date.isAfter(createdDate) || date.isEqual(createdDate)) &&
                task.selectedDays.contains(date.dayOfWeek.value)
    }

    fun updateRepetitiveTaskCreationDate(context: Context, taskId: String, newDate: LocalDate) {
        val currentTasks = loadRepetitiveTasks(context).map { task ->
            if (task.id == taskId) {
                task.copy(createdAt = newDate.toString())
            } else {
                task
            }
        }
        saveRepetitiveTasks(context, currentTasks)
    }

    fun saveDailyPlans(context: Context, plans: List<DailyPlan>) {
        getRepo(context).saveDailyPlans(plans)
    }

    fun triggerMigration(context: Context) {
        getRepo(context).performInitialMigration(force = true)
    }

    fun clearAllData(context: Context) {
        getRepo(context).clearAllData()
    }

    fun loadDailyPlans(context: Context): List<DailyPlan> {
        return getRepo(context).loadDailyPlans()
    }

    fun toggleDailyPlanCompletion(context: Context, planId: String) {
        val currentPlans = loadDailyPlans(context).map {
            if (it.id == planId) it.copy(isCompleted = !it.isCompleted) else it
        }
        saveDailyPlans(context, currentPlans)
    }
}
