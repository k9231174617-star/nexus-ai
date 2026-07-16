package com.nexus.agent.core.memory

import androidx.room.*

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: MemoryEntry): Long

    @Query("SELECT * FROM memory_entries WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<MemoryEntry>

    @Query("SELECT * FROM memory_entries WHERE agentId = :agentId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentByAgent(agentId: String, limit: Int): List<MemoryEntry>

    @Query("SELECT * FROM memory_entries ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<MemoryEntry>

    @Query("SELECT * FROM memory_entries WHERE agentId = :agentId")
    suspend fun getByAgent(agentId: String): List<MemoryEntry>

    @Query("DELETE FROM memory_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM memory_entries WHERE agentId = :agentId")
    suspend fun deleteByAgent(agentId: String)

    @Query("SELECT COUNT(*) FROM memory_entries")
    suspend fun count(): Long

    @Query("SELECT agentId, COUNT(*) as count FROM memory_entries GROUP BY agentId")
    suspend fun countByAgent(): List<AgentCount>

    @Query("SELECT * FROM memory_entries WHERE importance < :minImportance ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getLowImportanceOld(minImportance: Float, limit: Int): List<MemoryEntry>

    @Query("SELECT * FROM memory_entries WHERE timestamp < :cutoff")
    suspend fun getOlderThan(cutoff: Long): List<MemoryEntry>

    @Query("SELECT * FROM memory_entries")
    suspend fun getAll(): List<MemoryEntry>

    data class AgentCount(val agentId: String, val count: Int)
}