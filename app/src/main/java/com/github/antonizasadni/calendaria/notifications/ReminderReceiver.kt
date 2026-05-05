package com.github.antonizasadni.calendaria.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.antonizasadni.calendaria.completionList.FileManagement
import com.github.antonizasadni.calendaria.tasks.TaskManagement
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            ReminderManager.scheduleReminders(context)
            return
        }

        val type = intent.getStringExtra("type") ?: "both"

        when (type) {
            "important" -> {
                val title = intent.getStringExtra("task_title") ?: "Pending Task"
                val desc = intent.getStringExtra("task_desc") ?: ""
                NotificationHelper.showNotification(context, title, desc)
            }
            "current" -> {
                handleWeeklySummary(context, true, false)
            }
            "previous" -> {
                handleWeeklySummary(context, false, true)
            }
            "both" -> {
                handleWeeklySummary(context, true, true)
                checkAllImportantTasks(context)
            }
        }
    }

    private fun handleWeeklySummary(context: Context, checkCurrent: Boolean, checkPrevious: Boolean) {
        val prefs = context.getSharedPreferences("CalendAriaPrefs", Context.MODE_PRIVATE)
        val currentEnabled = prefs.getBoolean("reminders_current_week_enabled", true)
        val previousEnabled = prefs.getBoolean("reminders_previous_weeks_enabled", true)
        val tasks = TaskManagement.loadRepetitiveTasks(context)

        if (checkCurrent && currentEnabled) {
            val missingCurrent = mutableListOf<String>()
            tasks.forEach { task ->
                val summaries = FileManagement.calculateWeeklySummaries(context, task)
                val notes = FileManagement.loadWeeklyNotes(context, task.id)
                summaries.firstOrNull()?.let { currentWeek ->
                    if (notes[currentWeek.weekNumber].isNullOrBlank()) {
                        missingCurrent.add(task.title)
                    }
                }
            }
            if (missingCurrent.isNotEmpty()) {
                NotificationHelper.showNotification(
                    context,
                    "Weekly Summary Reminder",
                    "Don't forget to fill the summary for this week's tasks: ${missingCurrent.joinToString(", ")}"
                )
            }
        }

        if (checkPrevious && previousEnabled) {
            val missingPrevious = mutableListOf<String>()
            tasks.forEach { task ->
                val summaries = FileManagement.calculateWeeklySummaries(context, task)
                val notes = FileManagement.loadWeeklyNotes(context, task.id)
                val prevWeeksMissing = summaries.drop(1).filter { week ->
                    notes[week.weekNumber].isNullOrBlank()
                }
                if (prevWeeksMissing.isNotEmpty()) {
                    missingPrevious.add(task.title)
                }
            }
            if (missingPrevious.isNotEmpty()) {
                NotificationHelper.showNotification(
                    context,
                    "Previous Weeks Summary",
                    "Some tasks need summaries from previous weeks: ${missingPrevious.joinToString(", ")}"
                )
            }
        }
    }

    private fun checkAllImportantTasks(context: Context) {
        val prefs = context.getSharedPreferences("CalendAriaPrefs", Context.MODE_PRIVATE)
        val globalEnabled = prefs.getBoolean("important_tasks_notifications_enabled", true)
        if (!globalEnabled) return

        val importantTasks = TaskManagement.loadImportantTasks(context)
        val today = LocalDate.now()
        val todayStr = today.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

        importantTasks.forEach { task ->
            if (task.notificationsEnabled && !task.isCompleted && task.dueDate == todayStr) {
                val currentTime = java.time.LocalTime.now()
                val taskTime = if (task.time == "Whole Day") {
                    java.time.LocalTime.of(8, 0)
                } else {
                    parseTaskTime(task.time)
                }

                if (currentTime.isAfter(taskTime) || currentTime.equals(taskTime)) {
                    NotificationHelper.showNotification(
                        context,
                        "Pending Task",
                        task.description.ifBlank { task.title }
                    )
                }
            }
        }
    }

    private fun parseTaskTime(timeStr: String): java.time.LocalTime {
        return try {
            val parts = timeStr.trim().split(Regex("[:\\s]+"))
            var hour = parts[0].toInt()
            val minute = parts[1].toInt()

            if (parts.size > 2) {
                val amPm = parts[2].uppercase()
                if (amPm == "PM" && hour < 12) hour += 12
                if (amPm == "AM" && hour == 12) hour = 0
            }

            java.time.LocalTime.of(hour, minute)
        } catch (e: Exception) {
            java.time.LocalTime.of(8, 0)
        }
    }
}
