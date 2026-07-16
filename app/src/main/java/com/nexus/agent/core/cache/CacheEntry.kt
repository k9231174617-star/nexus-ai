package com.nexus.agent.core.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "response_cache")
data class CacheEntry(
    @PrimaryKey val promptHash: String,
    val prompt: String,
    val response: String,
    val agentType: String,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = createdAt + 3_600_000L,
    val hitCount: Int = 0,
)