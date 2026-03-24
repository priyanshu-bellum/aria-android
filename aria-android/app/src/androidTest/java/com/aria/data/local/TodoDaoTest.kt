package com.aria.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aria.data.local.dao.TodoDao
import com.aria.data.local.entities.Todo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodoDaoTest {

    private lateinit var db: AriaDatabase
    private lateinit var todoDao: TodoDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AriaDatabase::class.java
        ).allowMainThreadQueries().build()
        todoDao = db.todoDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private fun makeTodo(
        id: String = "id-${System.nanoTime()}",
        text: String = "Sample todo",
        source: String = "manual",
        completed: Boolean = false,
        dueDate: String? = null
    ) = Todo(
        id = id,
        text = text,
        source = source,
        dueDate = dueDate,
        completed = completed,
        createdAt = System.currentTimeMillis()
    )

    // -------------------------------------------------------------------------
    // insert + getAll
    // -------------------------------------------------------------------------

    @Test
    fun insertAndGetAll_returnsSingleTodo() = runBlocking {
        val todo = makeTodo(id = "todo-1", text = "Buy milk")
        todoDao.insert(todo)

        val all = todoDao.getAll().first()

        assertEquals("getAll should return exactly one todo", 1, all.size)
        assertEquals("Todo id should match", "todo-1", all.first().id)
        assertEquals("Todo text should match", "Buy milk", all.first().text)
        assertFalse("Todo should not be completed", all.first().completed)
    }

    @Test
    fun insertMultiple_getAllReturnsAll() = runBlocking {
        todoDao.insert(makeTodo(id = "a", text = "Task A"))
        todoDao.insert(makeTodo(id = "b", text = "Task B"))
        todoDao.insert(makeTodo(id = "c", text = "Task C"))

        val all = todoDao.getAll().first()

        assertEquals("getAll should return 3 todos", 3, all.size)
        val ids = all.map { it.id }.toSet()
        assertTrue("All inserted ids should be present", ids.containsAll(listOf("a", "b", "c")))
    }

    @Test
    fun insert_withReplaceConflict_updatesExistingRow() = runBlocking {
        val original = makeTodo(id = "dup", text = "Original text")
        todoDao.insert(original)

        val updated = original.copy(text = "Updated text")
        todoDao.insert(updated)

        val all = todoDao.getAll().first()
        assertEquals("Should still have 1 row after upsert", 1, all.size)
        assertEquals("Text should be updated after replace", "Updated text", all.first().text)
    }

    // -------------------------------------------------------------------------
    // getPending
    // -------------------------------------------------------------------------

    @Test
    fun getPending_returnsOnlyIncompleteTodos() = runBlocking {
        todoDao.insert(makeTodo(id = "done", text = "Done task", completed = true))
        todoDao.insert(makeTodo(id = "open1", text = "Open task 1", completed = false))
        todoDao.insert(makeTodo(id = "open2", text = "Open task 2", completed = false))

        val pending = todoDao.getPending().first()

        assertEquals("getPending should return 2 incomplete todos", 2, pending.size)
        assertTrue("All pending items should be incomplete", pending.all { !it.completed })
        val pendingIds = pending.map { it.id }.toSet()
        assertFalse("Completed todo should not appear in pending", pendingIds.contains("done"))
    }

    @Test
    fun getPending_whenAllCompleted_returnsEmpty() = runBlocking {
        todoDao.insert(makeTodo(id = "c1", completed = true))
        todoDao.insert(makeTodo(id = "c2", completed = true))

        val pending = todoDao.getPending().first()

        assertTrue("getPending should be empty when all todos are completed", pending.isEmpty())
    }

    @Test
    fun getPending_withDueDates_ordersByDueDateAsc() = runBlocking {
        todoDao.insert(makeTodo(id = "p1", text = "Later", completed = false, dueDate = "2026-12-31"))
        todoDao.insert(makeTodo(id = "p2", text = "Sooner", completed = false, dueDate = "2026-03-26"))

        val pending = todoDao.getPending().first()

        assertEquals("Sooner due date should come first", "2026-03-26", pending.first().dueDate)
    }

    // -------------------------------------------------------------------------
    // getTopPending
    // -------------------------------------------------------------------------

    @Test
    fun getTopPending_respectsLimit() = runBlocking {
        repeat(10) { i ->
            todoDao.insert(makeTodo(id = "t$i", text = "Task $i", completed = false))
        }

        val top = todoDao.getTopPending(3)

        assertEquals("getTopPending(3) should return at most 3 items", 3, top.size)
    }

    @Test
    fun getTopPending_returnsOnlyIncompleteItems() = runBlocking {
        todoDao.insert(makeTodo(id = "done", completed = true))
        todoDao.insert(makeTodo(id = "open", completed = false))

        val top = todoDao.getTopPending(5)

        assertTrue("getTopPending should only return incomplete todos", top.all { !it.completed })
    }

    @Test
    fun getTopPending_whenEmpty_returnsEmptyList() = runBlocking {
        val top = todoDao.getTopPending(5)
        assertTrue("getTopPending on empty DB should return empty list", top.isEmpty())
    }

    // -------------------------------------------------------------------------
    // updateCompleted
    // -------------------------------------------------------------------------

    @Test
    fun updateCompleted_setsCompletedTrue() = runBlocking {
        val todo = makeTodo(id = "update-me", completed = false)
        todoDao.insert(todo)

        todoDao.updateCompleted("update-me", true)

        val all = todoDao.getAll().first()
        val updated = all.first { it.id == "update-me" }
        assertTrue("Todo should be marked completed after updateCompleted(true)", updated.completed)
    }

    @Test
    fun updateCompleted_setsCompletedFalse() = runBlocking {
        val todo = makeTodo(id = "un-complete", completed = true)
        todoDao.insert(todo)

        todoDao.updateCompleted("un-complete", false)

        val all = todoDao.getAll().first()
        val updated = all.first { it.id == "un-complete" }
        assertFalse("Todo should be incomplete after updateCompleted(false)", updated.completed)
    }

    @Test
    fun updateCompleted_onNonExistentId_noException() = runBlocking {
        // Updating a non-existent row should not throw
        todoDao.updateCompleted("ghost-id", true)
        // Verify the table is still empty (no rows were created)
        assertTrue("DB should still be empty", todoDao.getAll().first().isEmpty())
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Test
    fun delete_removesTodo() = runBlocking {
        val todo = makeTodo(id = "delete-me", text = "To be deleted")
        todoDao.insert(todo)

        todoDao.delete("delete-me")

        val all = todoDao.getAll().first()
        assertTrue("Todo should be removed after delete", all.none { it.id == "delete-me" })
    }

    @Test
    fun delete_onlyDeletesTargetedRow() = runBlocking {
        todoDao.insert(makeTodo(id = "keep", text = "Keep me"))
        todoDao.insert(makeTodo(id = "remove", text = "Remove me"))

        todoDao.delete("remove")

        val all = todoDao.getAll().first()
        assertEquals("Only one todo should remain", 1, all.size)
        assertEquals("Remaining todo should be 'keep'", "keep", all.first().id)
    }

    @Test
    fun delete_nonExistentId_noException() = runBlocking {
        todoDao.insert(makeTodo(id = "real", text = "Real todo"))

        // Deleting a row that does not exist should not throw
        todoDao.delete("fake-id")

        val all = todoDao.getAll().first()
        assertEquals("Original todo should still exist", 1, all.size)
    }

    // -------------------------------------------------------------------------
    // source field preserved
    // -------------------------------------------------------------------------

    @Test
    fun insert_preservesSourceField() = runBlocking {
        todoDao.insert(makeTodo(id = "voice-todo", text = "Call dentist", source = "voice"))

        val all = todoDao.getAll().first()

        assertEquals("Source field should be persisted correctly", "voice", all.first().source)
    }

    // -------------------------------------------------------------------------
    // Flow reactivity: getAll emits after insert
    // -------------------------------------------------------------------------

    @Test
    fun getAll_emitsUpdatedList_afterInsert() = runBlocking {
        // First collect: empty
        val empty = todoDao.getAll().first()
        assertTrue("Should start empty", empty.isEmpty())

        // Insert then collect again
        todoDao.insert(makeTodo(id = "reactive", text = "Reactive task"))
        val updated = todoDao.getAll().first()
        assertEquals("Flow should emit updated list after insert", 1, updated.size)
    }
}
