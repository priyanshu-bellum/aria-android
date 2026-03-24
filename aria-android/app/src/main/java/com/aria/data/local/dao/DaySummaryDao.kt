package com.aria.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aria.data.local.entities.DaySummary

@Dao
interface DaySummaryDao {

    @Query("SELECT * FROM day_summaries WHERE date = :date")
    suspend fun getByDate(date: String): DaySummary?

    @Query("SELECT * FROM day_summaries ORDER BY synthesisRanAt DESC LIMIT 1")
    suspend fun getLatest(): DaySummary?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(summary: DaySummary)
}
