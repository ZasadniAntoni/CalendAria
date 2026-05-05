package com.github.antonizasadni.calendaria.tasks

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object DataMigrationManager {
    private const val TAG = "DataMigrationManager"
    private const val OLD_PACKAGE = "com.example.calendaria"
    private const val MIGRATION_DONE_FLAG = "migration_done.flag"

    fun checkAndMigrate(context: Context) {
        val flagFile = File(context.filesDir, MIGRATION_DONE_FLAG)
        if (flagFile.exists()) {
            return
        }

        Log.d(TAG, "Starting migration from $OLD_PACKAGE")
        
        val dataRootDir = context.filesDir.parentFile?.parentFile
        val possiblePaths = mutableListOf<File>()
        // Common paths on various Android versions/emulators
        possiblePaths.add(File("/data/data/$OLD_PACKAGE"))
        possiblePaths.add(File("/data/user/0/$OLD_PACKAGE"))
        dataRootDir?.let { possiblePaths.add(File(it, OLD_PACKAGE)) }

        var sourceDir: File? = null
        for (dir in possiblePaths.distinct()) {
            if (dir.exists()) {
                sourceDir = dir
                break
            }
        }

        if (sourceDir == null) {
            Log.d(TAG, "No old data directory found at any expected location")
            markMigrationDone(flagFile)
            return
        }

        Log.d(TAG, "Found old data directory at ${sourceDir.absolutePath}")
        
        try {
            val migrated = performMigration(context, sourceDir)
            if (migrated) {
                Log.d(TAG, "Migration completed successfully")
            } else {
                Log.d(TAG, "Migration found source but could not read/copy files. Check permissions.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during migration", e)
        }

        markMigrationDone(flagFile)
    }

    private fun markMigrationDone(flagFile: File) {
        try {
            flagFile.createNewFile()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create migration flag", e)
        }
    }

    private fun performMigration(context: Context, oldDataDir: File): Boolean {
        var copiedSomething = false

        // 1. Migrate SharedPreferences
        val oldPrefsDir = File(oldDataDir, "shared_prefs")
        if (oldPrefsDir.exists()) {
            val newPrefsDir = File(context.filesDir.parentFile, "shared_prefs")
            newPrefsDir.mkdirs()
            oldPrefsDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".xml")) {
                    if (copyFile(file, File(newPrefsDir, file.name))) {
                        Log.d(TAG, "Migrated pref: ${file.name}")
                        copiedSomething = true
                    }
                }
            }
        }

        // 2. Migrate files directory (includes JSON tasks)
        val oldFilesDir = File(oldDataDir, "files")
        if (oldFilesDir.exists()) {
            if (copyDirectory(oldFilesDir, context.filesDir)) {
                Log.d(TAG, "Migrated files directory")
                copiedSomething = true
            }
        }
        
        // 3. Migrate databases (if any)
        val oldDbDir = File(oldDataDir, "databases")
        if (oldDbDir.exists()) {
            val newDbDir = File(context.filesDir.parentFile, "databases")
            if (copyDirectory(oldDbDir, newDbDir)) {
                Log.d(TAG, "Migrated databases directory")
                copiedSomething = true
            }
        }

        return copiedSomething
    }

    private fun copyFile(source: File, destination: File): Boolean {
        return try {
            if (!source.canRead()) return false
            FileInputStream(source).use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy file ${source.name}: ${e.message}")
            false
        }
    }

    private fun copyDirectory(source: File, destination: File): Boolean {
        if (!source.exists() || !source.canRead()) return false
        if (!destination.exists()) {
            destination.mkdirs()
        }
        var success = false
        source.listFiles()?.forEach { file ->
            val targetFile = File(destination, file.name)
            if (file.isDirectory) {
                if (copyDirectory(file, targetFile)) success = true
            } else {
                if (copyFile(file, targetFile)) success = true
            }
        }
        return success
    }
}
