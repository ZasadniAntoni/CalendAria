package com.github.antonizasadni.calendaria.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.github.antonizasadni.calendaria.tasks.ImportantTask
import com.github.antonizasadni.calendaria.tasks.TaskManagement
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

object ReminderManager {
    private const val ID_REMINDER = 100
    private const val IMPORTANT_TASK_BASE_ID = 2000

    fun scheduleReminders(context: Context) {
        scheduleWeeklyReminders(context)
        scheduleAllImportantTasks(context)
    }

    private fun scheduleWeeklyReminders(context: Context) {
        val prefs = context.getSharedPreferences("CalendAriaPrefs", Context.MODE_PRIVATE)
        val currentEnabled = prefs.getBoolean("reminders_current_week_enabled", true)
        val previousEnabled = prefs.getBoolean("reminders_previous_weeks_enabled", true)

        if (!currentEnabled && !previousEnabled) {
            cancelWeeklyReminders(context)
            return
        }

        val day = prefs.getInt("reminders_day", Calendar.SUNDAY)
        val timeStr = prefs.getString("reminders_time", "08:00 AM") ?: "08:00 AM"
        val (h, m) = parseTime(timeStr)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("type", "both")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ID_REMINDER,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, day)
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.WEEK_OF_YEAR, 1)
            }
        }

        setAlarm(alarmManager, calendar.timeInMillis, pendingIntent)
    }

    fun scheduleAllImportantTasks(context: Context) {
        val tasks = TaskManagement.loadImportantTasks(context)
        tasks.forEach { task ->
            scheduleImportantTask(context, task)
        }
    }

    fun scheduleImportantTask(context: Context, task: ImportantTask) {
        val prefs = context.getSharedPreferences("CalendAriaPrefs", Context.MODE_PRIVATE)
        val globalEnabled = prefs.getBoolean("important_tasks_notifications_enabled", true)

        if (!globalEnabled || !task.notificationsEnabled || task.isCompleted) {
            cancelImportantTask(context, task)
            return
        }

        try {
            val date = LocalDate.parse(task.dueDate, DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            val time = if (task.time == "Whole Day") {
                LocalTime.of(8, 0)
            } else {
                val p = parseTime(task.time)
                LocalTime.of(p.first, p.second)
            }

            val triggerTime = LocalDateTime.of(date, time)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            if (triggerTime <= System.currentTimeMillis()) return

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("type", "important")
                putExtra("task_id", task.id)
                putExtra("task_title", task.title)
                putExtra("task_desc", task.description)
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                IMPORTANT_TASK_BASE_ID + task.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            setAlarm(alarmManager, triggerTime, pendingIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancelImportantTask(context: Context, task: ImportantTask) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            IMPORTANT_TASK_BASE_ID + task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun setAlarm(alarmManager: AlarmManager, triggerTime: Long, pendingIntent: PendingIntent) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    private fun cancelWeeklyReminders(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ID_REMINDER,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun cancelReminders(context: Context) {
        cancelWeeklyReminders(context)
        val tasks = TaskManagement.loadImportantTasks(context)
        tasks.forEach { cancelImportantTask(context, it) }
    }

    private fun parseTime(timeStr: String): Pair<Int, Int> {
        return try {
            val parts = timeStr.trim().split(Regex("[:\\s]+"))
            var hour = parts[0].toInt()
            val minute = parts[1].toInt()
            
            if (parts.size > 2) {
                val amPm = parts[2].uppercase()
                if (amPm == "PM" && hour < 12) hour += 12
                if (amPm == "AM" && hour == 12) hour = 0
            }
            hour to minute
        } catch (e: Exception) {
            8 to 0
        }
    }
}
