package com.aria.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aria.data.local.entities.Note
import com.aria.data.local.entities.Todo
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SourceChip(source: String) {
    val color = when (source) {
        "voice" -> Color(0xFF00BCD4)
        "telegram" -> Color(0xFF7C5CBF)
        else -> Color.Gray
    }
    Surface(color = color.copy(alpha = 0.2f), shape = RoundedCornerShape(50)) {
        Text(
            text = source,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(viewModel: NotesViewModel = hiltViewModel()) {
    val todos by viewModel.todos.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var newItemText by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = activeTab.ordinal) {
                Tab(
                    selected = activeTab == NoteTab.TODOS,
                    onClick = { viewModel.setTab(NoteTab.TODOS) },
                    text = { Text("Todos") }
                )
                Tab(
                    selected = activeTab == NoteTab.NOTES,
                    onClick = { viewModel.setTab(NoteTab.NOTES) },
                    text = { Text("Notes") }
                )
            }

            when (activeTab) {
                NoteTab.TODOS -> TodosTabContent(
                    todos = todos,
                    onToggle = { todo -> viewModel.toggleTodo(todo) },
                    onDelete = { todo -> viewModel.deleteTodo(todo) }
                )
                NoteTab.NOTES -> NotesTabContent(
                    notes = notes,
                    onDelete = { note -> viewModel.deleteNote(note) }
                )
            }
        }
    }

    if (showAddDialog) {
        val tabLabel = if (activeTab == NoteTab.TODOS) "Todo" else "Note"
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                newItemText = ""
            },
            title = { Text("Add $tabLabel") },
            text = {
                OutlinedTextField(
                    value = newItemText,
                    onValueChange = { newItemText = it },
                    label = { Text("Enter $tabLabel text") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 6
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newItemText.isNotBlank()) {
                            if (activeTab == NoteTab.TODOS) {
                                viewModel.addTodo(newItemText.trim())
                            } else {
                                viewModel.addNote(newItemText.trim())
                            }
                            newItemText = ""
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    newItemText = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodosTabContent(
    todos: List<Todo>,
    onToggle: (Todo) -> Unit,
    onDelete: (Todo) -> Unit
) {
    if (todos.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No todos yet. Tap + to add one.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(todos, key = { it.id }) { todo ->
                var dismissed by remember { mutableStateOf(false) }
                if (!dismissed) {
                    val swipeState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                onDelete(todo)
                                dismissed = true
                                true
                            } else {
                                false
                            }
                        }
                    )
                    SwipeToDismissBox(
                        state = swipeState,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFFEF5350), RoundedCornerShape(12.dp))
                                    .padding(end = 16.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color.White
                                )
                            }
                        },
                        enableDismissFromStartToEnd = false
                    ) {
                        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = todo.completed,
                                    onCheckedChange = { onToggle(todo) }
                                )
                                Text(
                                    text = todo.text,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textDecoration = if (todo.completed) TextDecoration.LineThrough else null,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.width(8.dp))
                                SourceChip(source = todo.source)
                                Spacer(Modifier.width(4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesTabContent(
    notes: List<Note>,
    onDelete: (Note) -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    if (notes.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No notes yet. Tap + to add one.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(notes, key = { it.id }) { note ->
                var dismissed by remember { mutableStateOf(false) }
                if (!dismissed) {
                    val swipeState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                onDelete(note)
                                dismissed = true
                                true
                            } else {
                                false
                            }
                        }
                    )
                    SwipeToDismissBox(
                        state = swipeState,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFFEF5350), RoundedCornerShape(12.dp))
                                    .padding(end = 16.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color.White
                                )
                            }
                        },
                        enableDismissFromStartToEnd = false
                    ) {
                        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = note.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SourceChip(source = note.source)
                                    Text(
                                        text = dateFormatter.format(Date(note.createdAt)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
