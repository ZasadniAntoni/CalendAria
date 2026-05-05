package com.github.antonizasadni.calendaria.tasks

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * A professional Data Repository that provides database-like safety 
 * using a robust JSON storage system. 
 * 
 * Features:
 * - Atomic Saves: Writes to temp file first to prevent data corruption.
 * - Centralized Logic: Single source of truth for all task data.
 * - Migration Ready: Structured to easily swap with Room Database later.
 */
class DataRepository(private val context: Context) {
    private val gson = Gson()
    private val TAG = "DataRepository"

    private val REPETITIVE_FILE = "repetitive_tasks_v2.json"
    private val IMPORTANT_FILE = "important_tasks_v2.json"
    private val DAILY_PLAN_FILE = "daily_plans_v2.json"

    // --- MIGRATION LOGIC ---
    
    /**
     * Migrates data from old SharedPreferences to the new Repository system.
     * @param force If true, it will attempt to merge data even if the new files already exist.
     */
    fun performInitialMigration(force: Boolean = false) {
        // Trigger structure migration first
        com.github.antonizasadni.calendaria.completionList.FileManagement.migrateFileStructure(context)

        val legacyPrefsNames = listOf("calendaria_prefs", "CalendAriaPrefs")
        val targetPrefs = context.getSharedPreferences("CalendAriaPrefs", Context.MODE_PRIVATE)
        
        legacyPrefsNames.forEach { prefsName ->
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            var migrationHappened = false
            
            // 1. Migrate Repetitive Tasks
            if (force || !File(context.filesDir, REPETITIVE_FILE).exists()) {
                val oldJson = prefs.getString("repetitive_tasks", null)
                if (oldJson != null) {
                    Log.d(TAG, "Migrating Repetitive Tasks from $prefsName...")
                    try {
                        val type = object : TypeToken<List<RepetitiveTask>>() {}.type
                        val tasks: List<RepetitiveTask> = gson.fromJson(oldJson, type) ?: emptyList()
                        
                        val current = if (File(context.filesDir, REPETITIVE_FILE).exists()) 
                            loadRepetitiveTasks().toMutableList() else mutableListOf()
                            
                        tasks.forEach { newTask ->
                            if (current.none { it.id == newTask.id }) current.add(newTask)
                        }
                        saveRepetitiveTasks(current)
                        migrationHappened = true
                        editor.remove("repetitive_tasks")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to migrate Repetitive Tasks from $prefsName", e)
                    }
                }
            }

            // 2. Migrate Important Tasks
            if (force || !File(context.filesDir, IMPORTANT_FILE).exists()) {
                val oldJson = prefs.getString("important_tasks", null)
                if (oldJson != null) {
                    Log.d(TAG, "Migrating Important Tasks from $prefsName...")
                    try {
                        val type = object : TypeToken<List<ImportantTask>>() {}.type
                        val tasks: List<ImportantTask> = gson.fromJson(oldJson, type) ?: emptyList()
                        
                        val current = if (File(context.filesDir, IMPORTANT_FILE).exists()) 
                            loadImportantTasks().toMutableList() else mutableListOf()

                        tasks.forEach { newTask ->
                            if (current.none { it.id == newTask.id }) current.add(newTask)
                        }
                        saveImportantTasks(current)
                        migrationHappened = true
                        editor.remove("important_tasks")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to migrate Important Tasks from $prefsName", e)
                    }
                }
            }

            // 3. Migrate Daily Plans
            if (force || !File(context.filesDir, DAILY_PLAN_FILE).exists()) {
                val oldJson = prefs.getString("daily_plans", null)
                if (oldJson != null) {
                    Log.d(TAG, "Migrating Daily Plans from $prefsName...")
                    try {
                        val type = object : TypeToken<List<DailyPlan>>() {}.type
                        val plans: List<DailyPlan> = gson.fromJson(oldJson, type) ?: emptyList()
                        
                        val current = if (File(context.filesDir, DAILY_PLAN_FILE).exists()) 
                            loadDailyPlans().toMutableList() else mutableListOf()

                        plans.forEach { newPlan ->
                            if (current.none { it.id == newPlan.id }) current.add(newPlan)
                        }
                        saveDailyPlans(current)
                        migrationHappened = true
                        editor.remove("daily_plans")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to migrate Daily Plans from $prefsName", e)
                    }
                }
            }

            // 4. Migrate Settings (only if we are migrating FROM calendaria_prefs TO CalendAriaPrefs)
            if (prefsName == "calendaria_prefs") {
                val settingsKeys = listOf(
                    "last_launch_date", "use_24h_format", "reminders_current_week_enabled",
                    "reminders_previous_weeks_enabled", "important_tasks_notifications_enabled",
                    "reminders_day", "reminders_time"
                )
                
                settingsKeys.forEach { key ->
                    if (prefs.contains(key) && !targetPrefs.contains(key)) {
                        val value = prefs.all[key]
                        val targetEditor = targetPrefs.edit()
                        when (value) {
                            is Boolean -> targetEditor.putBoolean(key, value)
                            is Int -> targetEditor.putInt(key, value)
                            is String -> targetEditor.putString(key, value)
                            is Long -> targetEditor.putLong(key, value)
                            is Float -> targetEditor.putFloat(key, value)
                        }
                        targetEditor.apply()
                        editor.remove(key)
                        migrationHappened = true
                    }
                }
            }

            if (migrationHappened) {
                // Use commit() to ensure data is written immediately before file deletion
                editor.commit()
                Log.d(TAG, "Migration from $prefsName complete. Legacy data removed.")
                
                // If this is the old preference file, move any remaining items and delete it
                if (prefsName == "calendaria_prefs") {
                    val remainingPrefs = prefs.all
                    if (remainingPrefs.isNotEmpty()) {
                        Log.d(TAG, "Moving remaining settings from $prefsName to CalendAriaPrefs...")
                        val targetEditor = targetPrefs.edit()
                        remainingPrefs.forEach { (key, value) ->
                            if (!targetPrefs.contains(key)) {
                                when (value) {
                                    is Boolean -> targetEditor.putBoolean(key, value)
                                    is Int -> targetEditor.putInt(key, value)
                                    is String -> targetEditor.putString(key, value)
                                    is Long -> targetEditor.putLong(key, value)
                                    is Float -> targetEditor.putFloat(key, value)
                                }
                            }
                        }
                        targetEditor.apply()
                    }
                    
                    // Delete the physical file
                    val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/$prefsName.xml")
                    if (prefsFile.exists()) {
                        Log.d(TAG, "Deleting legacy file: $prefsFile")
                        prefsFile.delete()
                    }
                }
            }
        }
    }

    // --- REPETITIVE TASKS ---

    fun loadRepetitiveTasks(): List<RepetitiveTask> {
        return loadList(REPETITIVE_FILE, object : TypeToken<List<RepetitiveTask>>() {}.type)
    }

    fun saveRepetitiveTasks(tasks: List<RepetitiveTask>) {
        saveList(REPETITIVE_FILE, tasks)
    }

    // --- IMPORTANT TASKS ---

    fun loadImportantTasks(): List<ImportantTask> {
        return loadList(IMPORTANT_FILE, object : TypeToken<List<ImportantTask>>() {}.type)
    }

    fun saveImportantTasks(tasks: List<ImportantTask>) {
        saveList(IMPORTANT_FILE, tasks)
    }

    // --- DAILY PLANS ---

    fun loadDailyPlans(): List<DailyPlan> {
        return loadList(DAILY_PLAN_FILE, object : TypeToken<List<DailyPlan>>() {}.type)
    }

    fun saveDailyPlans(plans: List<DailyPlan>) {
        saveList(DAILY_PLAN_FILE, plans)
    }

    /**
     * Wipes all task data and history from the device.
     */
    fun clearAllData() {
        // 1. Delete Repository Files
        File(context.filesDir, REPETITIVE_FILE).delete()
        File(context.filesDir, IMPORTANT_FILE).delete()
        File(context.filesDir, DAILY_PLAN_FILE).delete()

        // 2. Delete all Task-Specific Folders and Archives
        context.filesDir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                file.deleteRecursively()
            }
        }
        
        Log.d(TAG, "All task data and archives cleared.")
    }

    // --- GENERIC ATOMIC STORAGE ENGINE ---

    private fun <T> loadList(fileName: String, type: java.lang.reflect.Type): List<T> {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return emptyList()
        
        return try {
            val json = file.readText()
            val list: List<T>? = gson.fromJson(json, type)
            list ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading $fileName", e)
            emptyList()
        }
    }

    private fun <T> saveList(fileName: String, data: List<T>) {
        val file = File(context.filesDir, fileName)
        val tempFile = File(context.filesDir, "$fileName.tmp")
        
        try {
            val json = gson.toJson(data)
            
            // Atomic Save Logic:
            // 1. Write to temp file
            FileOutputStream(tempFile).use { it.write(json.toByteArray()) }
            
            // 2. Rename temp to actual (this is an atomic OS operation)
            if (tempFile.renameTo(file)) {
                Log.d(TAG, "Successfully saved $fileName")
            } else {
                // Fallback for some Android versions
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Atomic save failed for $fileName", e)
        }
    }
}
