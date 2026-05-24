package com.github.antonizasadni.calendaria.notesView

import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.animation.core.tween

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
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { 
                fullScreenNote = null
                onDismissAdd()
            },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
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
        }
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
    var contentValue by remember { mutableStateOf(TextFieldValue(note.content)) }
    var isPinned by remember { mutableStateOf(note.isPinned) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isMenuExpanded by remember { mutableStateOf(false) }
    
    val colorResIds = listOf(
        R.color.plan_pink, R.color.plan_orange, R.color.plan_brown,
        R.color.plan_grey, R.color.plan_purple, R.color.plan_teal, R.color.plan_green
    )
    val colors = colorResIds.map { colorResource(it).toArgb().toLong() }
    var selectedColor by remember { mutableLongStateOf(note.color) }

    val scrollState = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
    var textLayoutResultState by remember { mutableStateOf<TextLayoutResult?>(null) }
    var lastJumpSelection by remember { mutableStateOf(-1) }

    LaunchedEffect(contentValue.text, selectedColor, isPinned) {
        if (contentValue.text == note.content && 
            selectedColor == note.color && isPinned == note.isPinned) return@LaunchedEffect
        
        delay(500)
        onSave(note.copy(content = contentValue.text, color = selectedColor, isPinned = isPinned, lastModified = System.currentTimeMillis()))
    }

    // Automatically jump to the current line (cursor) when keyboard opens or focus changes
    LaunchedEffect(isKeyboardVisible) {
        if (isEditMode && isKeyboardVisible) {
            delay(150) // Small delay to let keyboard slide up
            textLayoutResultState?.let { layout ->
                val cursorRect = layout.getCursorRect(contentValue.selection.start)
                scrollState.animateScrollTo(
                    value = (cursorRect.top - with(density) { 16.dp.toPx() }).toInt().coerceAtLeast(0),
                    animationSpec = tween(durationMillis = 400)
                )
            }
        }
    }

    // Follow cursor while typing
    LaunchedEffect(contentValue.selection, textLayoutResultState) {
        if (isEditMode && isKeyboardVisible && textLayoutResultState != null) {
            val layout = textLayoutResultState!!
            val cursorRect = layout.getCursorRect(contentValue.selection.start)
            
            // If the cursor is moving further down than our current scroll, 
            // adjust scroll to keep it in view.
            val threshold = with(density) { 100.dp.toPx() } // Aesthetic buffer
            val targetScroll = (cursorRect.bottom - threshold).toInt().coerceAtLeast(0)
            
            if (targetScroll > scrollState.value) {
                scrollState.animateScrollTo(targetScroll, animationSpec = tween(durationMillis = 100))
            }
        }
    }

    Scaffold(
        modifier = Modifier.navigationBarsPadding(),
        topBar = {
            Surface(
                color = Color(selectedColor).copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .height(48.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (isEditMode) {
                            onSave(note.copy(content = contentValue.text, color = selectedColor, isPinned = isPinned, lastModified = System.currentTimeMillis()))
                        }
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(onClick = { isPinned = !isPinned }) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pin",
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isEditMode && isMenuExpanded) {
                    // Bubble 1: Delete
                    FloatingActionButton(
                        onClick = { 
                            showDeleteConfirm = true
                            isMenuExpanded = false
                        },
                        containerColor = Color(0xFF6B2424), // Dark, muted burgundy red
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Delete, "Delete", tint = Color.White)
                    }
                    
                    // Bubble 2: Edit
                    FloatingActionButton(
                        onClick = { 
                            isEditMode = true
                            isMenuExpanded = false
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Edit, "Edit", tint = Color.White)
                    }
                }

                FloatingActionButton(
                    onClick = { 
                        if (isEditMode) {
                            onSave(note.copy(content = contentValue.text, color = selectedColor, isPinned = isPinned, lastModified = System.currentTimeMillis()))
                            isEditMode = false
                        } else {
                            isMenuExpanded = !isMenuExpanded
                        }
                    },
                    containerColor = if (isEditMode) Color(0xFF4CAF50) else MaterialTheme.colorScheme.secondary,
                    // Removed imePadding here so it anchors relative to the bottom bar
                ) {
                    Icon(
                        imageVector = if (isEditMode) Icons.Default.Check 
                                     else if (isMenuExpanded) Icons.Default.Close 
                                     else Icons.Default.KeyboardArrowUp,
                        contentDescription = "Toggle Edit",
                        tint = Color.White
                    )
                }
            }
        },
        bottomBar = {
            if (isEditMode) {
                Surface(
                    color = Color(selectedColor).copy(alpha = 0.8f),
                    modifier = Modifier.imePadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
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
        },
        containerColor = Color(selectedColor).copy(alpha = 0.4f),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(selectedColor).copy(alpha = 0.4f))
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            if (isEditMode) {
                BasicTextField(
                    value = contentValue,
                    onValueChange = { 
                        contentValue = it 
                    },
                    onTextLayout = { textLayoutResultState = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField: @Composable () -> Unit ->
                        if (contentValue.text.isEmpty()) {
                            Text("Note content", color = Color.Gray.copy(alpha = 0.5f))
                        }
                        innerTextField()
                    }
                )
                // Bottom spacer to allow any line to scroll to the top
                Spacer(modifier = Modifier.height(128.dp))
            } else {
                val annotatedString = MarkdownParser.parse(contentValue.text)
                var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                Text(
                    text = annotatedString,
                    style = MaterialTheme.typography.bodyLarge,
                    onTextLayout = { textLayoutResult = it },
                    modifier = Modifier.pointerInput(contentValue.text) {
                        detectTapGestures { offset ->
                            textLayoutResult?.let { layoutResult ->
                                val position = layoutResult.getOffsetForPosition(offset)
                                annotatedString.getStringAnnotations(tag = "CHECKBOX", start = position, end = position)
                                    .firstOrNull()?.let { annotation ->
                                        val lineIndex = annotation.item.toIntOrNull() ?: return@let
                                        val lines = contentValue.text.split("\n").toMutableList()
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
                                            contentValue = contentValue.copy(text = lines.joinToString("\n"))
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
