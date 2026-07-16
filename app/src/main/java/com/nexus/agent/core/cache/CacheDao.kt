package com.nexus.agent.core.cache

import androidx.room.*

@Dao
interface CacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CacheEntry)

    @Query("SELECT * FROM response_cache WHERE promptHash = :hash AND agentType = :agent LIMIT 1")
    suspend fun getExact(hash: String, agent: String): CacheEntry?

    @Query("DELETE FROM response_cache WHERE agentType = :agent")
    suspend fun deleteByAgent(agent: String)

    @Query("DELETE FROM response_cache WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long)

    @Query("SELECT COUNT(*) FROM response_cache")
    suspend fun count(): Int
}