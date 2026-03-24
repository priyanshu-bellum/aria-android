package com.aria.ui.screens

import com.aria.data.local.dao.NoteDao
import com.aria.data.local.dao.TodoDao
import com.aria.data.local.entities.Note
import com.aria.data.local.entities.Todo
import com.aria.util.MainCoroutineRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private val todoDao = mockk<TodoDao>(relaxed = true)
    private val noteDao = mockk<NoteDao>(relaxed = true)
    private lateinit var viewModel: NotesViewModel

    // Sample fixtures
    private val sampleTodo = Todo(
        id = "todo-1",
        text = "Buy milk",
        source = "manual",
        completed = false
    )
    private val completedTodo = Todo(
        id = "todo-2",
        text = "Submit report",
        source = "manual",
        completed = true
    )
    private val sampleNote = Note(
        id = "note-1",
        text = "Meeting notes",
        source = "manual"
    )

    @Before
    fun setup() {
        every { todoDao.getAll() } returns flowOf(emptyList())
        every { noteDao.getAll() } returns flowOf(emptyList())
        viewModel = NotesViewModel(todoDao, noteDao)
    }

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    fun `initialTab_isTodos`() {
        assertEquals(
            "Default active tab should be TODOS",
            NoteTab.TODOS,
            viewModel.activeTab.value
        )
    }

    @Test
    fun `initialTodos_isEmpty`() {
        assertTrue(
            "Todos StateFlow should start empty when dao returns empty list",
            viewModel.todos.value.isEmpty()
        )
    }

    @Test
    fun `initialNotes_isEmpty`() {
        assertTrue(
            "Notes StateFlow should start empty when dao returns empty list",
            viewModel.notes.value.isEmpty()
        )
    }

    // -------------------------------------------------------------------------
    // Tab switching
    // -------------------------------------------------------------------------

    @Test
    fun `setTab_toNotes_updatesActiveTab`() {
        viewModel.setTab(NoteTab.NOTES)
        assertEquals(
            "Active tab should update to NOTES",
            NoteTab.NOTES,
            viewModel.activeTab.value
        )
    }

    @Test
    fun `setTab_toTodos_updatesActiveTab`() {
        // Switch to NOTES first, then back
        viewModel.setTab(NoteTab.NOTES)
        viewModel.setTab(NoteTab.TODOS)
        assertEquals(
            "Active tab should update back to TODOS",
            NoteTab.TODOS,
            viewModel.activeTab.value
        )
    }

    // -------------------------------------------------------------------------
    // addTodo
    // -------------------------------------------------------------------------

    @Test
    fun `addTodo_callsDaoInsert`() = runTest {
        viewModel.addTodo("Buy milk")
        coVerify {
            todoDao.insert(match { todo ->
                todo.text == "Buy milk" && todo.source == "manual" && !todo.completed
            })
        }
    }

    @Test
    fun `addTodo_withLongText_callsDaoInsert`() = runTest {
        val longText = "a".repeat(500)
        viewModel.addTodo(longText)
        coVerify { todoDao.insert(match { it.text == longText }) }
    }

    // -------------------------------------------------------------------------
    // addNote
    // -------------------------------------------------------------------------

    @Test
    fun `addNote_callsDaoInsert`() = runTest {
        viewModel.addNote("Meeting notes")
        coVerify {
            noteDao.insert(match { note ->
                note.text == "Meeting notes" && note.source == "manual"
            })
        }
    }

    // -------------------------------------------------------------------------
    // deleteTodo
    // -------------------------------------------------------------------------

    @Test
    fun `deleteTodo_callsDaoDelete`() = runTest {
        viewModel.deleteTodo(sampleTodo)
        coVerify { todoDao.delete(sampleTodo.id) }
    }

    // -------------------------------------------------------------------------
    // deleteNote
    // -------------------------------------------------------------------------

    @Test
    fun `deleteNote_callsDaoDelete`() = runTest {
        viewModel.deleteNote(sampleNote)
        coVerify { noteDao.delete(sampleNote.id) }
    }

    // -------------------------------------------------------------------------
    // toggleTodo
    // -------------------------------------------------------------------------

    @Test
    fun `toggleTodo_onIncompleteTodo_setsCompletedTrue`() = runTest {
        viewModel.toggleTodo(sampleTodo) // sampleTodo.completed = false → should flip to true
        coVerify { todoDao.updateCompleted(sampleTodo.id, true) }
    }

    @Test
    fun `toggleTodo_onCompletedTodo_setsCompletedFalse`() = runTest {
        viewModel.toggleTodo(completedTodo) // completedTodo.completed = true → should flip to false
        coVerify { todoDao.updateCompleted(completedTodo.id, false) }
    }

    // -------------------------------------------------------------------------
    // StateFlow emits DAO data
    // -------------------------------------------------------------------------

    @Test
    fun `todos_reflectsFlowFromDao`() {
        every { todoDao.getAll() } returns flowOf(listOf(sampleTodo))
        // Re-create viewModel so it picks up the updated stub
        viewModel = NotesViewModel(todoDao, noteDao)

        // UnconfinedTestDispatcher means stateIn collects synchronously
        assertFalse(
            "Todos should contain the item emitted by the DAO",
            viewModel.todos.value.isEmpty()
        )
        assertEquals(
            "Todos should have exactly one item",
            1,
            viewModel.todos.value.size
        )
        assertEquals(
            "Todo text should match",
            "Buy milk",
            viewModel.todos.value.first().text
        )
    }

    @Test
    fun `notes_reflectsFlowFromDao`() {
        every { noteDao.getAll() } returns flowOf(listOf(sampleNote))
        viewModel = NotesViewModel(todoDao, noteDao)

        assertFalse(
            "Notes should contain the item emitted by the DAO",
            viewModel.notes.value.isEmpty()
        )
        assertEquals(
            "Note text should match",
            "Meeting notes",
            viewModel.notes.value.first().text
        )
    }
}
