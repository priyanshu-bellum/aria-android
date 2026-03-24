package com.aria.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.aria.data.local.dao.ConversationDao
import com.aria.data.local.dao.DaySummaryDao
import com.aria.data.local.dao.NoteDao
import com.aria.data.local.dao.TodoDao
import com.aria.data.local.entities.ConversationEntry
import com.aria.data.local.entities.DaySummary
import com.aria.data.local.entities.Note
import com.aria.data.local.entities.Todo

@Database(
    entities = [Todo::class, Note::class, DaySummary::class, ConversationEntry::class],
    version = 1,
    exportSchema = false
)
abstract class AriaDatabase : RoomDatabase() {

    abstract fun todoDao(): TodoDao
    abstract fun noteDao(): NoteDao
    abstract fun daySummaryDao(): DaySummaryDao
    abstract fun conversationDao(): ConversationDao

    companion object {
        fun create(context: Context): AriaDatabase =
            Room.databaseBuilder(context, AriaDatabase::class.java, "aria.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
