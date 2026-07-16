package com.nexus.agent.core.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_entries")
data class MemoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val type: String = "interaction",
    val agentId: String = "main",
    val importance: Float = 0.5f,
    val metadata: String = "{}",
    val timestamp: Long = System.currentTimeMillis(),
    val accessCount: Int = 0,
    val lastAccessed: Long = System.currentTimeMillis(),
)