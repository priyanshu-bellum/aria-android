package com.aria.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "todos")
data class Todo(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val text: String,
    val source: String,       // "voice" | "telegram" | "manual" | "assistant"
    val dueDate: String? = null,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
