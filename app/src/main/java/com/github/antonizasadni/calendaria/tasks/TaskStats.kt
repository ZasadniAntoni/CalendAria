package com.github.antonizasadni.calendaria.tasks

import android.content.Context
import com.github.antonizasadni.calendaria.completionList.FileManagement
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

data class TaskStats(
    val completedCount: Int,
    val missedCount: Int,
    val totalScheduledCount: Int,
    val completionPercentage: Int,
    val missedPercentage: Int
)

fun calculateTaskStats(context: Context, task: RepetitiveTask): TaskStats {
    val today = LocalDate.now()
    val startDate = try {
        if (task.createdAt.isNullOrEmpty()) LocalDate.of(2024, 1, 1)
        else LocalDate.parse(task.createdAt)
    } catch (e: Exception) {
        LocalDate.of(2024, 1, 1)
    }

    if (startDate.isAfter(today)) {
        return TaskStats(0, 0, 0, 0, 0)
    }

    var completed = 0
    var missed = 0

    val daysBetween = ChronoUnit.DAYS.between(startDate, today).toInt()

    for (i in 0..daysBetween) {
        val checkDate = startDate.plusDays(i.toLong())
        if (task.selectedDays.contains(checkDate.dayOfWeek.value)) {
            val history = FileManagement.loadHistory(context, task.id, checkDate)
            val isDone = history[checkDate.dayOfMonth] ?: false
            if (isDone) completed++ else missed++
        }
    }

    val total = completed + missed
    val compPercent = if (total > 0) ((completed.toFloat() / total) * 100).roundToInt() else 0
    val missPercent = if (total > 0) 100 - compPercent else 0

    return TaskStats(completed, missed, total, compPercent, missPercent)
}
