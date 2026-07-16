package com.nexus.agent.data.local

import androidx.room.*
import com.nexus.agent.core.chat.MessageModel

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageModel)

    @Query("SELECT * FROM messages WHERE agentType = :agent ORDER BY timestamp ASC")
    suspend fun getByAgent(agent: String): List<MessageModel>

    @Query("SELECT * FROM messages WHERE agentType = :agent ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentByAgent(agent: String, limit: Int): List<MessageModel>

    @Query("DELETE FROM messages WHERE agentType = :agent")
    suspend fun deleteByAgent(agent: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM messages WHERE agentType = :agent")
    suspend fun countByAgent(agent: String): Int

    @Query("SELECT SUM(tokenCount) FROM messages WHERE agentType = :agent")
    suspend fun totalTokensByAgent(agent: String): Long?
}