package com.github.antonizasadni.calendaria.ArchiveData

import android.content.Context
import android.net.Uri
import com.github.antonizasadni.calendaria.tasks.DataRepository
import com.github.antonizasadni.calendaria.tasks.TaskManagement
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ArchiveData {
    fun archiveAllData(context: Context, outputStream: OutputStream): Result<Unit> {
        return try {
            ZipOutputStream(outputStream).use { zos ->
                // 1. Archive SharedPreferences
                val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
                if (prefsDir.exists()) {
                    prefsDir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            addToZip(file, "shared_prefs/${file.name}", zos)
                        }
                    }
                }

                // 2. Archive Internal Files (History, Weekly Notes, etc.)
                val filesDir = context.filesDir
                if (filesDir.exists()) {
                    filesDir.walkTopDown().forEach { file ->
                        if (file.isFile) {
                            val relativePath = file.relativeTo(filesDir).path
                            addToZip(file, "files/$relativePath", zos)
                        }
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun restoreDataFromZip(context: Context, inputStream: InputStream): Result<Unit> {
        return try {
            ZipInputStream(inputStream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val targetFile = when {
                        entry.name.startsWith("shared_prefs/") -> {
                            val fileName = entry.name.removePrefix("shared_prefs/")
                            // Ensure we use the correct directory for shared_prefs
                            File(context.applicationInfo.dataDir, "shared_prefs/$fileName")
                        }
                        entry.name.startsWith("files/") -> {
                            val fileName = entry.name.removePrefix("files/")
                            File(context.filesDir, fileName)
                        }
                        else -> null
                    }

                    targetFile?.let {
                        it.parentFile?.mkdirs()
                        FileOutputStream(it).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            // After restore, we should trigger a migration to ensure any restored old-format data 
            // is moved to the new repository system and file structure.
            com.github.antonizasadni.calendaria.completionList.FileManagement.migrateFileStructure(context)
            TaskManagement.triggerMigration(context)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun addToZip(file: File, zipPath: String, zos: ZipOutputStream) {
        file.inputStream().use { fis ->
            val entry = ZipEntry(zipPath)
            zos.putNextEntry(entry)
            fis.copyTo(zos)
            zos.closeEntry()
        }
    }

    // Keeping the path-based methods for backward compatibility or simple use-cases if needed, 
    // but refactored to use the stream-based ones.
    fun archiveAllDataToPath(context: Context, targetFolder: String, archiveName: String): Result<File> {
        return try {
            val fileName = if (archiveName.endsWith(".zip", ignoreCase = true)) archiveName else "$archiveName.zip"
            val targetDir = File(targetFolder)
            if (!targetDir.exists()) targetDir.mkdirs()
            val zipFile = File(targetDir, fileName)
            
            archiveAllData(context, FileOutputStream(zipFile)).map { zipFile }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
