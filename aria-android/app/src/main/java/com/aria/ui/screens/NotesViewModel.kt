package com.aria.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aria.data.local.dao.NoteDao
import com.aria.data.local.dao.TodoDao
import com.aria.data.local.entities.Note
import com.aria.data.local.entities.Todo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class NoteTab { TODOS, NOTES }

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val todoDao: TodoDao,
    private val noteDao: NoteDao
) : ViewModel() {

    val todos: StateFlow<List<Todo>> = todoDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<Note>> = noteDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeTab: MutableStateFlow<NoteTab> = MutableStateFlow(NoteTab.TODOS)

    fun addTodo(text: String) {
        viewModelScope.launch {
            todoDao.insert(
                Todo(
                    id = UUID.randomUUID().toString(),
                    text = text,
                    source = "manual",
                    completed = false,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun addNote(text: String) {
        viewModelScope.launch {
            noteDao.insert(
                Note(
                    id = UUID.randomUUID().toString(),
                    text = text,
                    source = "manual",
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteTodo(todo: Todo) {
        viewModelScope.launch {
            todoDao.delete(todo.id)
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            noteDao.delete(note.id)
        }
    }

    fun toggleTodo(todo: Todo) {
        viewModelScope.launch {
            todoDao.updateCompleted(todo.id, !todo.completed)
        }
    }

    fun setTab(tab: NoteTab) {
        activeTab.value = tab
    }
}
