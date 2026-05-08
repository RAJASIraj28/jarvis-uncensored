package com.jarvis.ai.assistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jarvis_memory")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val key: String,
    val value: String,
    val timestamp: Long = System.currentTimeMillis(),
    val category: String = "general"
)
