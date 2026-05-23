package com.github.antonizasadni.calendaria.notesView

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.antonizasadni.calendaria.R
import com.github.antonizasadni.calendaria.tasks.Note
import com.github.antonizasadni.calendaria.tasks.SearchableTopBar
import com.github.antonizasadni.calendaria.tasks.TaskManagement
import kotlinx.coroutines.delay
import java.util.UUID

@Composable
fun NotesScreen(
    notes: MutableList<Note>,
    showAddNote: Boolean,
    onDismissAdd: () -> Unit,
    onNotesChanged: () -> Unit,
    onFabVisibilityChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var fullScreenNote by remember { mutableStateOf<Note?>(null) }

    val colorResIds = listOf(
        R.color.plan_pink, R.color.plan_orange, R.color.plan_brown,
        R.color.plan_grey, R.color.plan_purple, R.color.plan_teal, R.color.plan_green
    )
    val defaultColor = colorResource(colorResIds[3]).toArgb().toLong()

    val activeNote = fullScreenNote ?: if (showAddNote) {
        remember { Note(id = UUID.randomUUID().toString(), content = "", color = defaultColor) }
    } else null

    LaunchedEffect(activeNote) {
        onFabVisibilityChange(activeNote == null)
    }

    if (activeNote != null) {
        SingleNoteScreen(
            note = activeNote,
            startInEditMode = showAddNote || activeNote.content.isEmpty(),
            onBack = { 
                fullScreenNote = null
                onDismissAdd()
            },
            onSave = { updatedNote ->
                val index = notes.indexOfFirst { it.id == updatedNote.id }
                if (index != -1) {
                    notes[index] = updatedNote
                } else {
                    notes.add(updatedNote)
                }
                TaskManagement.saveNotes(context, notes)
                onNotesChanged()
                
                if (fullScreenNote == null) {
                    fullScreenNote = updatedNote
                }
            },
            onDelete = {
                notes.removeIf { it.id == activeNote.id }
                TaskManagement.saveNotes(context, notes)
                onNotesChanged()
                fullScreenNote = null
                onDismissAdd()
            }
        )
        return
    }

    val filteredNotes by remember(searchQuery, notes) {
        derivedStateOf {
            notes
                .asSequence()
                .filter { 
                    it.content.contains(searchQuery, ignoreCase = true) 
                }
                .sortedWith(compareByDescending<Note> { it.isPinned }.thenByDescending { it.lastModified })
                .toList()
        }
    }

    val pinnedNotes = filteredNotes.filter { it.isPinned }
    val otherNotes = filteredNotes.filter { !it.isPinned }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchableTopBar(
            title = "Notes",
            searchQuery = searchQuery,
            onQueryChange = { searchQuery = it },
        ) { 
            /* Not implemented yet */ 
        }

        if (notes.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No notes yet.", color = Color.Gray)
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp
            ) {
                if (pinnedNotes.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Text(
                            "Pinned",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                    }
                    items(pinnedNotes, key = { it.id }) { note ->
                        NoteItem(note = note, onClick = { fullScreenNote = note })
                    }
                    if (otherNotes.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Text(
                                "Others",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                            )
                        }
                    }
                }
                
                items(otherNotes, key = { it.id }) { note ->
                    NoteItem(note = note, onClick = { fullScreenNote = note })
                }
            }
        }
    }
}

@Composable
fun NoteItem(
    note: Note,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color(note.color).copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, Color(note.color).copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = MarkdownParser.parse(note.content),
                maxLines = 10,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleNoteScreen(
    note: Note,
    startInEditMode: Boolean,
    onBack: () -> Unit,
    onSave: (Note) -> Unit,
    onDelete: () -> Unit
) {
    var isEditMode by remember { mutableStateOf(startInEditMode) }
    var content by remember { mutableStateOf(note.content) }
    var isPinned by remember { mutableStateOf(note.isPinned) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    val colorResIds = listOf(
        R.color.plan_pink, R.color.plan_orange, R.color.plan_brown,
        R.color.plan_grey, R.color.plan_purple, R.color.plan_teal, R.color.plan_green
    )
    val colors = colorResIds.map { colorResource(it).toArgb().toLong() }
    var selectedColor by remember { mutableLongStateOf(note.color) }

    LaunchedEffect(content, selectedColor, isPinned) {
        if (content == note.content && 
            selectedColor == note.color && isPinned == note.isPinned) return@LaunchedEffect
        
        delay(500) // 500ms debounce
        onSave(note.copy(
            content = content, 
            color = selectedColor, 
            isPinned = isPinned, 
            lastModified = System.currentTimeMillis()
        ))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isEditMode) {
                            onSave(note.copy(content = content, color = selectedColor, isPinned = isPinned, lastModified = System.currentTimeMillis()))
                        }
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isPinned = !isPinned }) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pin",
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(selectedColor).copy(alpha = 0.6f)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    if (isEditMode) {
                        onSave(note.copy(content = content, color = selectedColor, isPinned = isPinned, lastModified = System.currentTimeMillis()))
                    }
                    isEditMode = !isEditMode 
                },
                containerColor = if (isEditMode) Color(0xFF4CAF50) else MaterialTheme.colorScheme.secondary,
            ) {
                Icon(
                    imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = "Toggle Edit",
                    tint = Color.White
                )
            }
        },
        bottomBar = {
            if (isEditMode) {
                Surface(
                    color = Color(selectedColor).copy(alpha = 0.8f),
                    tonalElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Color Picker
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            colors.forEach { colorVal ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color(colorVal), CircleShape)
                                        .border(
                                            width = if (selectedColor == colorVal) 2.dp else 0.dp,
                                            color = if (selectedColor == colorVal) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColor = colorVal }
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = Color(selectedColor).copy(alpha = 0.4f)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (isEditMode) {
                TextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = { Text("Note content") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
            } else {
                val annotatedString = MarkdownParser.parse(content)
                var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                Text(
                    text = annotatedString,
                    style = MaterialTheme.typography.bodyLarge,
                    onTextLayout = { textLayoutResult = it },
                    modifier = Modifier.pointerInput(content) {
                        detectTapGestures { offset ->
                            textLayoutResult?.let { layoutResult ->
                                val position = layoutResult.getOffsetForPosition(offset)
                                annotatedString.getStringAnnotations(tag = "CHECKBOX", start = position, end = position)
                                    .firstOrNull()?.let { annotation ->
                                        val lineIndex = annotation.item.toIntOrNull() ?: return@let
                                        val lines = content.split("\n").toMutableList()
                                        if (lineIndex < lines.size) {
                                            val line = lines[lineIndex]
                                            lines[lineIndex] = when {
                                                line.startsWith("- [ ] ") -> line.replaceFirst("- [ ] ", "- [x] ")
                                                line.startsWith("* [ ] ") -> line.replaceFirst("* [ ] ", "* [x] ")
                                                line.startsWith("- [x] ") -> line.replaceFirst("- [x] ", "- [ ] ")
                                                line.startsWith("* [x] ") -> line.replaceFirst("* [x] ", "* [ ] ")
                                                line.startsWith("- [X] ") -> line.replaceFirst("- [X] ", "- [ ] ")
                                                line.startsWith("* [X] ") -> line.replaceFirst("* [X] ", "* [ ] ")
                                                else -> line
                                            }
                                            content = lines.joinToString("\n")
                                        }
                                    }
                            }
                        }
                    }
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Note") },
            text = { Text("Are you sure you want to permanently delete this note?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
