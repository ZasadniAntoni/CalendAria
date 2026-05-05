package com.github.antonizasadni.calendaria.completionList

import android.content.Context
import com.github.antonizasadni.calendaria.tasks.RepetitiveTask
import com.github.antonizasadni.calendaria.tasks.TaskManagement
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.time.LocalDate
import kotlinx.serialization.json.Json
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import java.time.temporal.ChronoUnit

object FileManagement {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        allowStructuredMapKeys = true
    }

    private fun getHistoryFile(context: Context, taskId: String, date: LocalDate): File {
        val today = LocalDate.now()
        val month = date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val year = date.year
        
        val baseDir = if (year == today.year) {
            File(context.filesDir, taskId)
        } else {
            File(File(context.filesDir, "archives"), taskId)
        }
        
        if (!baseDir.exists()) baseDir.mkdirs()
        return File(baseDir, "$month-$year.json")
    }

    fun hasArchivedData(context: Context): Boolean {
        val archiveDir = File(context.filesDir, "archives")
        return archiveDir.exists() && (archiveDir.listFiles()?.any { it.isDirectory && it.listFiles()?.isNotEmpty() == true } ?: false)
    }

    fun loadHistory(context: Context, taskId: String, date: LocalDate): MutableMap<Int, Boolean> {
        val file = getHistoryFile(context, taskId, date)

        if (!file.exists()) {
            // Check legacy location just in case
            val month = date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            val legacyFile = File(context.filesDir, "${taskId}_$month-${date.year}.json")
            if (legacyFile.exists()) return try {
                json.decodeFromString<Map<Int, Boolean>>(legacyFile.readText()).toMutableMap()
            } catch (e: Exception) { mutableMapOf() }
            
            return mutableMapOf()
        }

        return try {
            val content = file.readText()
            json.decodeFromString<Map<Int, Boolean>>(content).toMutableMap()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    fun saveHistory(context: Context, taskId: String, date: LocalDate, history: Map<Int, Boolean>) {
        val file = getHistoryFile(context, taskId, date)
        try {
            val content = json.encodeToString(history)
            file.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun migrateFileStructure(context: Context) {
        val rootFiles = context.filesDir.listFiles() ?: return
        val today = LocalDate.now()

        // 1. Migrate files from root to task-specific folders
        rootFiles.forEach { file ->
            if (file.isFile && file.name.endsWith(".json")) {
                try {
                    if (file.name.contains("_weekly_notes")) {
                        // taskId_weekly_notes.json -> taskId/weekly_notes.json
                        val taskId = file.name.substringBefore("_weekly_notes")
                        val targetDir = File(context.filesDir, taskId)
                        targetDir.mkdirs()
                        file.renameTo(File(targetDir, "weekly_notes.json"))
                    } else if (file.name.contains("_")) {
                        // Expected pattern: taskId_Month-Year.json
                        val taskId = file.name.substringBefore("_")
                        val datePart = file.name.substringAfter("_").removeSuffix(".json")
                        val parts = datePart.split("-")
                        if (parts.size == 2) {
                            val year = parts[1].toInt()
                            val targetDir = if (year == today.year) {
                                File(context.filesDir, taskId)
                            } else {
                                File(File(context.filesDir, "archives"), taskId)
                            }
                            targetDir.mkdirs()
                            val targetFile = File(targetDir, "${parts[0]}-$year.json")
                            file.renameTo(targetFile)
                        }
                    }
                } catch (e: Exception) { }
            }
        }

        // 2. Migrate old archives to new task-specific archive folders
        val oldArchiveDir = File(context.filesDir, "archives")
        if (oldArchiveDir.exists()) {
            oldArchiveDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".json")) {
                    // Could be old taskId_Month-Year.json or task_history_...
                    try {
                        val taskId = if (file.name.startsWith("task_history_")) {
                            file.name.substringAfter("task_history_").substringBeforeLast("_")
                        } else if (file.name.contains("_")) {
                            file.name.substringBefore("_")
                        } else null

                        taskId?.let {
                            val targetDir = File(oldArchiveDir, it)
                            targetDir.mkdirs()
                            file.renameTo(File(targetDir, file.name.substringAfter("_", file.name)))
                        }
                    } catch (e: Exception) { }
                }
            }
        }
    }

    fun updateTaskHistory(context: Context, taskId: String, newStartDate: LocalDate) {
        val currentHistory = loadHistory(context, taskId, newStartDate).toMutableMap()
        val iterator = currentHistory.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (newStartDate.withDayOfMonth(entry.key).isBefore(newStartDate)) {
                iterator.remove()
            }
        }
        saveHistory(context, taskId, newStartDate, currentHistory)

        val rootFiles = context.filesDir.listFiles()
        val archiveDir = File(context.filesDir, "archives")
        val archiveFiles = if (archiveDir.exists()) archiveDir.listFiles() else emptyArray()

        val allFiles = (rootFiles ?: emptyArray()) + (archiveFiles ?: emptyArray())

        allFiles.forEach { file ->
            if (file.name.startsWith("${taskId}_") && file.name.endsWith(".json") && !file.name.contains("weekly_notes")) {
                try {
                    val datePart = file.name.substringAfter("${taskId}_").removeSuffix(".json")
                    val parts = datePart.split("-")
                    if (parts.size == 2) {
                        val monthName = parts[0].uppercase()
                        val year = parts[1].toInt()
                        val fileYearMonth = YearMonth.of(year, Month.valueOf(monthName))
                        val startYearMonth = YearMonth.from(newStartDate)

                        if (fileYearMonth.isBefore(startYearMonth)) {
                            file.delete()
                        }
                    }
                } catch (e: Exception) { }
            }
        }
    }

    fun archivePastYearsTasks(context: Context) {
        val today = LocalDate.now()
        val currentYear = today.year
        val archiveDir = File(context.filesDir, "archives").apply { mkdirs() }
        val tasks = TaskManagement.loadRepetitiveTasks(context)

        val allArchiveFiles = archiveDir.listFiles() ?: emptyArray()
        allArchiveFiles.forEach { file ->
            val fileName = file.name
            try {
                val taskId = fileName.substringAfter("task_history_").substringBeforeLast("_")
                val fileYear = fileName.substringAfterLast("-").removeSuffix(".json").toInt()

                val associatedTask = tasks.find { it.id == taskId }

                if (associatedTask == null) {
                    file.delete()
                } else {
                    val taskStartYear = LocalDate.parse(associatedTask.createdAt).year
                    if (fileYear < taskStartYear) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
            }
        }

        context.filesDir.listFiles()?.forEach { file ->
            val fileName = file.name
            if (fileName.endsWith(".json") &&
                !fileName.contains("important_tasks") &&
                !fileName.contains("weekly_notes") &&
                !fileName.startsWith("repetitive_tasks")
            ) {
                try {
                    val year = fileName.substringAfterLast("-").removeSuffix(".json").toIntOrNull()
                    if (year != null && year < currentYear) {
                        val destination = File(archiveDir, fileName)
                        file.renameTo(destination)
                    }
                } catch (e: Exception) { }
            }
        }

        tasks.forEach { task ->
            val startYear = LocalDate.parse(task.createdAt).year
            for (year in startYear until currentYear) {
                val fileName = "task_history_${task.id}_${task.title.replace(" ", "_")}-$year.json"
                val archiveFile = File(archiveDir, fileName)

                if (!archiveFile.exists()) {
                    saveArchiveFile(context, fileName, emptyMap())
                }
            }
        }
    }

    private fun saveArchiveFile(context: Context, fileName: String, data: Map<Int, Boolean>) {
        try {
            val archiveDir = File(context.filesDir, "archives")
            val file = File(archiveDir, fileName)
            val json = org.json.JSONObject()
            data.forEach { (day, completed) -> json.put(day.toString(), completed) }
            file.writeText(json.toString())
        } catch (e: Exception) { }
    }

    fun getMonthSummary(context: Context, task: RepetitiveTask, date: LocalDate): Pair<Int, Int> {
        val history = loadHistory(context, task.id, date)
        val completedCount = history.values.count { it }
        val yearMonth = YearMonth.of(date.year, date.month)
        val daysInMonth = yearMonth.lengthOfMonth()
        var scheduledCount = 0

        val taskStart = try {
            LocalDate.parse(task.createdAt)
        } catch (e: Exception) {
            LocalDate.of(2024, 1, 1)
        }

        for (day in 1..daysInMonth) {
            val checkDate = date.withDayOfMonth(day)
            val isAfterCreation = !checkDate.isBefore(taskStart)
            val isNotFuture = !checkDate.isAfter(LocalDate.now())
            val isSelectedDay = task.selectedDays.contains(checkDate.dayOfWeek.value)

            if (isAfterCreation && isNotFuture && isSelectedDay) {
                scheduledCount++
            }
        }
        return Pair(completedCount, scheduledCount)
    }

    fun getYearlySummary(context: Context, task: RepetitiveTask, year: Int): Pair<Int, Int> {
        var totalDone = 0
        var totalScheduled = 0

        for (m in 1..12) {
            val date = LocalDate.of(year, m, 1)
            val (done, scheduled) = getMonthSummary(context, task, date)
            totalDone += done
            totalScheduled += scheduled
        }

        return Pair(totalDone, totalScheduled)
    }

    fun getArchivedYears(context: Context): List<Int> {
        val archiveDir = File(context.filesDir, "archives")
        if (!archiveDir.exists()) return emptyList()

        val years = mutableSetOf<Int>()
        archiveDir.listFiles()?.filter { it.isDirectory }?.forEach { taskDir ->
            taskDir.listFiles()?.forEach { file ->
                try {
                    val year = file.name.removeSuffix(".json").substringAfterLast("-").toInt()
                    years.add(year)
                } catch (e: Exception) { }
            }
        }
        return years.toList().sortedDescending()
    }

    fun calculateStatsFromMap(history: Map<Int, Boolean>, task: RepetitiveTask, monthDate: LocalDate): Pair<Int, Int> {
        val startDate = try { LocalDate.parse(task.createdAt) } catch(e: Exception) { LocalDate.of(2024,1,1) }
        val today = LocalDate.now()

        val scheduledCount = (1..monthDate.lengthOfMonth()).count { day ->
            val current = monthDate.withDayOfMonth(day)
            val isTaskDay = task.selectedDays.contains(current.dayOfWeek.value)
            val isWithinBounds = !current.isBefore(startDate) && !current.isAfter(today)
            isTaskDay && isWithinBounds
        }

        val completedCount = history.values.count { it }
        return Pair(completedCount, scheduledCount)
    }

    fun toggleDay(context: Context, taskId: String, date: LocalDate) {
        val history = loadHistory(context, taskId, date)
        val currentStatus = history[date.dayOfMonth] ?: false
        history[date.dayOfMonth] = !currentStatus
        saveHistory(context, taskId, date, history)
    }

    data class TaskStatsResult(
        val label: String,
        val completedCount: Int,
        val totalScheduledCount: Int,
        val completionPercentage: Int,
        val missedCount: Int,
        val missedPercentage: Int
    )

    data class WeeklySummary(
        val weekNumber: Int,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val completedCount: Int,
        val scheduledCount: Int,
        val percentage: Int
    )

    fun calculateWeeklySummaries(context: Context, task: RepetitiveTask): List<WeeklySummary> {
        val summaries = mutableListOf<WeeklySummary>()
        val startDate = try { LocalDate.parse(task.createdAt) } catch (e: Exception) { LocalDate.now() }
        val today = LocalDate.now()

        var currentWeekStart = startDate
        var weekIndex = 1

        while (!currentWeekStart.isAfter(today)) {
            val daysUntilSunday = 7 - currentWeekStart.dayOfWeek.value
            val currentWeekEnd = currentWeekStart.plusDays(daysUntilSunday.toLong())

            var completedInWeek = 0
            var scheduledInWeek = 0

            var dateToCheck = currentWeekStart
            while (!dateToCheck.isAfter(currentWeekEnd) && !dateToCheck.isAfter(today)) {
                if (task.selectedDays.contains(dateToCheck.dayOfWeek.value)) {
                    scheduledInWeek++
                    val history = loadHistory(context, task.id, dateToCheck)
                    if (history[dateToCheck.dayOfMonth] == true) {
                        completedInWeek++
                    }
                }
                dateToCheck = dateToCheck.plusDays(1)
            }

            if (scheduledInWeek > 0) {
                val percent = (completedInWeek.toFloat() / scheduledInWeek * 100).toInt()
                summaries.add(
                    WeeklySummary(
                        weekNumber = weekIndex,
                        startDate = currentWeekStart,
                        endDate = if (currentWeekEnd.isAfter(today)) today else currentWeekEnd,
                        completedCount = completedInWeek,
                        scheduledCount = scheduledInWeek,
                        percentage = percent
                    )
                )
            }
            currentWeekStart = currentWeekEnd.plusDays(1)
            weekIndex++
        }
        return summaries.reversed()
    }

    fun loadWeeklyNotes(context: Context, taskId: String): MutableMap<Int, String> {
        val taskDir = File(context.filesDir, taskId)
        val file = File(taskDir, "weekly_notes.json")
        
        if (!file.exists()) {
            // Check legacy location
            val legacyFile = File(context.filesDir, "${taskId}_weekly_notes.json")
            if (legacyFile.exists()) return try {
                val jsonText = legacyFile.readText()
                val type = object : TypeToken<MutableMap<Int, String>>() {}.type
                Gson().fromJson(jsonText, type) ?: mutableMapOf()
            } catch (e: Exception) { mutableMapOf() }
            
            return mutableMapOf()
        }

        return try {
            val jsonText = file.readText()
            val type = object : TypeToken<MutableMap<Int, String>>() {}.type
            Gson().fromJson(jsonText, type) ?: mutableMapOf()
        } catch (e: Exception) { mutableMapOf() }
    }

    fun saveWeeklyNote(context: Context, taskId: String, weekNumber: Int, note: String) {
        val notes = loadWeeklyNotes(context, taskId)
        val words = note.trim().split(Regex("\\s+"))
        val limitedNote = if (words.size > 300) words.take(300).joinToString(" ") else note
        notes[weekNumber] = limitedNote
        
        val taskDir = File(context.filesDir, taskId)
        if (!taskDir.exists()) taskDir.mkdirs()
        val file = File(taskDir, "weekly_notes.json")
        
        val gson = GsonBuilder().disableHtmlEscaping().create()
        file.writeText(gson.toJson(notes))
    }

    fun updateWeeklyNotes(context: Context, taskId: String, oldStartDate: LocalDate, newStartDate: LocalDate) {
        val notes = loadWeeklyNotes(context, taskId).toMutableMap()
        if (notes.isEmpty()) return
        val oldMonday = oldStartDate.minusDays((oldStartDate.dayOfWeek.value - 1).toLong())
        val newMonday = newStartDate.minusDays((newStartDate.dayOfWeek.value - 1).toLong())
        val weeksDiff = ChronoUnit.WEEKS.between(newMonday, oldMonday).toInt()
        if (weeksDiff != 0) {
            val shiftedNotes = mutableMapOf<Int, String>()
            notes.forEach { (weekNum, text) ->
                val newWeekNum = weekNum + weeksDiff
                if (newWeekNum >= 1) shiftedNotes[newWeekNum] = text
            }
            
            val taskDir = File(context.filesDir, taskId)
            if (!taskDir.exists()) taskDir.mkdirs()
            val file = File(taskDir, "weekly_notes.json")

            val gson = GsonBuilder().disableHtmlEscaping().create()
            file.writeText(gson.toJson(shiftedNotes))
        }
    }
}
