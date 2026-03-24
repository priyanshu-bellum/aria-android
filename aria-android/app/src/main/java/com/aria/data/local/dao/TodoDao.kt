package com.aria.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aria.data.local.entities.Todo
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    @Query("SELECT * FROM todos ORDER BY createdAt DESC")
    fun getAll(): Flow<List<Todo>>

    @Query("SELECT * FROM todos WHERE completed = 0 ORDER BY dueDate ASC, createdAt DESC")
    fun getPending(): Flow<List<Todo>>

    @Query("SELECT * FROM todos WHERE completed = 0 ORDER BY dueDate ASC LIMIT :limit")
    suspend fun getTopPending(limit: Int = 5): List<Todo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(todo: Todo)

    @Query("UPDATE todos SET completed = :completed WHERE id = :id")
    suspend fun updateCompleted(id: String, completed: Boolean)

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun delete(id: String)
}
