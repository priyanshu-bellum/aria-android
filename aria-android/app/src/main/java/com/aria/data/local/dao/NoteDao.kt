package com.aria.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aria.data.local.entities.Note
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun getAll(): Flow<List<Note>>

    @Query("SELECT * FROM notes ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 10): List<Note>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)
}
