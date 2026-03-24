package com.aria.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "day_summaries")
data class DaySummary(
    @PrimaryKey val date: String,   // "2026-03-24"
    val summary: String,
    val rawJsonlPath: String,       // path to that day's log file
    val synthesisRanAt: Long = System.currentTimeMillis()
)
