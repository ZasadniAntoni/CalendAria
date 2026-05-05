package com.github.antonizasadni.calendaria.ArchiveData

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.core.net.toUri
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveDataScreen() {
    val context = LocalContext.current
    var folderPath by remember { 
        mutableStateOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath) 
    }
    var archiveName by remember { mutableStateOf("calendaria_user_backup.zip") }
    var isArchiving by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    // Launcher for Export (Create Document)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let {
            isArchiving = true
            context.contentResolver.openOutputStream(it)?.use { outputStream ->
                val result = ArchiveData.archiveAllData(context, outputStream)
                isArchiving = false
                result.onSuccess {
                    Toast.makeText(context, "Archive saved successfully", Toast.LENGTH_LONG).show()
                }.onFailure { error ->
                    Toast.makeText(context, "Export error: ${error.message}", Toast.LENGTH_LONG).show()
                }
            } ?: run {
                isArchiving = false
                Toast.makeText(context, "Could not open output stream", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Launcher for Import (Open Document)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            isImporting = true
            context.contentResolver.openInputStream(it)?.use { inputStream ->
                val result = ArchiveData.restoreDataFromZip(context, inputStream)
                isImporting = false
                result.onSuccess {
                    Toast.makeText(context, "Data imported successfully! Please restart the app.", Toast.LENGTH_LONG).show()
                }.onFailure { error ->
                    Toast.makeText(context, "Import error: ${error.message}", Toast.LENGTH_LONG).show()
                }
            } ?: run {
                isImporting = false
                Toast.makeText(context, "Could not open input stream", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- ARCHIVE SECTION ---
        Text(
            text = "Archive Your Data",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Create a backup .zip file. You can use the folder icon to choose where to save it.",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = archiveName,
            onValueChange = { archiveName = it },
            label = { Text("Default Archive Name") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                IconButton(onClick = { exportLauncher.launch(archiveName) }) {
                    Icon(Icons.Default.Folder, contentDescription = "Choose Location")
                }
            },
            placeholder = { Text("e.g., my_backup.zip") }
        )

        if (isArchiving) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            Button(
                onClick = {
                    // Fallback to path-based archiving if user typed a path manually and didn't use the picker
                    // Note: Direct file access might be restricted on modern Android.
                    if (folderPath.isBlank() || archiveName.isBlank()) {
                        Toast.makeText(context, "Please provide path and name or use the folder icon", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isArchiving = true
                    val result = ArchiveData.archiveAllDataToPath(context, folderPath, archiveName)
                    isArchiving = false
                    
                    result.onSuccess { file ->
                        Toast.makeText(context, "Archive created: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                    }.onFailure { error ->
                        Toast.makeText(context, "Error: ${error.message}. Try using the folder icon instead.", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Archive to Default Path")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // --- IMPORT SECTION ---
        Text(
            text = "Restore or Import Data",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Restore from a .zip backup or import legacy .json files.",
            style = MaterialTheme.typography.bodyMedium
        )

        // RESTORE FROM ZIP
        Button(
            onClick = { importLauncher.launch(arrayOf("application/zip")) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null)
            Spacer(Modifier.width(8.dp))
            Text("Restore from .zip Backup")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Note: Using the icons is recommended for compatibility with Android's secure storage.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    helperText: @Composable (() -> Unit)? = null
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        leadingIcon = leadingIcon,
        placeholder = placeholder,
        supportingText = helperText
    )
}
