package com.github.antonizasadni.calendaria.tasks

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun filterTasks(tasks: List<RepetitiveTask>, query: String): List<RepetitiveTask> {
    if (query.isBlank()) return tasks

    val queryTokens = query.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }

    return tasks.filter { task ->
        queryTokens.all { token ->
            task.title.contains(token, ignoreCase = true)
        }
    }
}

@Composable
fun SearchableTopBar(
    title: String,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onFilterClick: () -> Unit
) {
    var isSearchActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .animateContentSize()
                .fillMaxHeight(),
            contentAlignment = Alignment.CenterStart) {
            if (!isSearchActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        isSearchActive = true
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Open Search")
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isSearchActive,
                enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .focusRequester(focusRequester),
                    placeholder = { Text("Search...") },
                    leadingIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            onQueryChange("")
                        }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                )

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
            }
        }

        IconButton(onClick = onFilterClick) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Filter Options",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

data class TaskFilter(
    val showCompleted: Boolean = true,
    val showIncomplete: Boolean = true,
    val selectedDate: LocalDate? = null,
    val selectedDays: Set<Int> = emptySet()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    isRepetitiveType: Boolean,
    currentFilter: TaskFilter,
    onDismiss: () -> Unit,
    onApply: (TaskFilter) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var tempFilter by remember { mutableStateOf(currentFilter) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = tempFilter.selectedDate?.atStartOfDay(ZoneId.systemDefault())
            ?.toInstant()?.toEpochMilli() ?: System.currentTimeMillis()
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text("Filter Tasks", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            // --- COMMON: Status Filters ---
            if (!isRepetitiveType) {
                Text("Status", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val statusOptions = listOf("All", "Pending", "Done")

                    statusOptions.forEach { option ->
                        val isSelected = when (option) {
                            "All" -> tempFilter.showCompleted && tempFilter.showIncomplete
                            "Pending" -> !tempFilter.showCompleted && tempFilter.showIncomplete
                            "Done" -> tempFilter.showCompleted && !tempFilter.showIncomplete
                            else -> false
                        }

                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = isSelected,
                            onClick = {
                                tempFilter = when (option) {
                                    "All" -> tempFilter.copy(
                                        showCompleted = true,
                                        showIncomplete = true
                                    )

                                    "Pending" -> tempFilter.copy(
                                        showCompleted = false,
                                        showIncomplete = true
                                    )

                                    "Done" -> tempFilter.copy(
                                        showCompleted = true,
                                        showIncomplete = false
                                    )

                                    else -> tempFilter
                                }
                            },
                            label = {
                                Text(
                                    text = option,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            },
                            shape = CircleShape
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // --- TYPE SPECIFIC: Days (Repetitive) vs Date (Important) ---
            if (isRepetitiveType) {
                Text("Repeat Days", style = MaterialTheme.typography.labelLarge)
                val days = listOf("M", "T", "W", "T", "F", "S", "S")
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    days.forEachIndexed { index, day ->
                        val dayNum = index + 1
                        val isSelected = tempFilter.selectedDays.contains(dayNum)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                                .clickable {
                                    val newDays = if (isSelected)
                                        tempFilter.selectedDays - dayNum else tempFilter.selectedDays + dayNum
                                    tempFilter = tempFilter.copy(selectedDays = newDays)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            } else {
                Text("Due Date", style = MaterialTheme.typography.labelLarge)
                OutlinedCard(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DateRange, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = tempFilter.selectedDate?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                                    ?: "Show All Dates",
                                color = if (tempFilter.selectedDate == null) Color.Gray else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (tempFilter.selectedDate != null) {
                            // Option to clear the date filter within the sheet
                            TextButton(onClick = { tempFilter = tempFilter.copy(selectedDate = null) }) {
                                Text("Clear")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        tempFilter = TaskFilter() // Reset to default
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear All")
                }
                Button(onClick = { onApply(tempFilter) }, modifier = Modifier.weight(1f)) {
                    Text("Apply")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val pickedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        // Update the temp filter so it shows in the BottomSheet immediately
                        tempFilter = tempFilter.copy(selectedDate = pickedDate)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
