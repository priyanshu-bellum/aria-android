package com.aria.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "conversations")
data class ConversationEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val role: String,               // "user" | "assistant"
    val content: String,
    val channel: String,            // "telegram" | "voice" | "app"
    val timestamp: Long = System.currentTimeMillis()
)
