package com.nexus.agent.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sandbox_sessions")
data class SandboxSessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val language: String, // python, javascript, kotlin, etc.
    val code: String = "",
    val output: String = "",
    val status: String = "idle", // idle, running, completed, error
    val createdAt: Long = System.currentTimeMillis(),
    val lastRunAt: Long? = null,
    val executionTimeMs: Long = 0,
    val memoryUsedMb: Long = 0,
    val isFavorite: Boolean = false,
    val tags: String = ""
)

@Entity(tableName = "sandbox_history")
data class SandboxHistoryEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val code: String,
    val output: String,
    val status: String,
    val executedAt: Long = System.currentTimeMillis(),
    val executionTimeMs: Long = 0,
    val errorMessage: String? = null
)

@Dao
interface SandboxDao {
    // Session operations
    @Query("SELECT * FROM sandbox_sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<SandboxSessionEntity>>

    @Query("SELECT * FROM sandbox_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): SandboxSessionEntity?

    @Query("SELECT * FROM sandbox_sessions WHERE language = :language ORDER BY lastRunAt DESC")
    fun getSessionsByLanguage(language: String): Flow<List<SandboxSessionEntity>>

    @Query("SELECT * FROM sandbox_sessions WHERE isFavorite = 1")
    fun getFavoriteSessions(): Flow<List<SandboxSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SandboxSessionEntity)

    @Update
    suspend fun updateSession(session: SandboxSessionEntity)

    @Query("UPDATE sandbox_sessions SET code = :code WHERE id = :sessionId")
    suspend fun updateSessionCode(sessionId: String, code: String)

    @Query("UPDATE sandbox_sessions SET output = :output, status = :status, lastRunAt = :timestamp WHERE id = :sessionId")
    suspend fun updateSessionOutput(sessionId: String, output: String, status: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE sandbox_sessions SET isFavorite = :favorite WHERE id = :sessionId")
    suspend fun setFavorite(sessionId: String, favorite: Boolean)

    @Delete
    suspend fun deleteSession(session: SandboxSessionEntity)

    @Query("DELETE FROM sandbox_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    // History operations
    @Query("SELECT * FROM sandbox_history WHERE sessionId = :sessionId ORDER BY executedAt DESC")
    fun getSessionHistory(sessionId: String): Flow<List<SandboxHistoryEntity>>

    @Query("SELECT * FROM sandbox_history ORDER BY executedAt DESC LIMIT :limit")
    suspend fun getRecentHistory(limit: Int = 50): List<SandboxHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryEntry(entry: SandboxHistoryEntity)

    @Query("DELETE FROM sandbox_history WHERE sessionId = :sessionId")
    suspend fun clearSessionHistory(sessionId: String)

    @Query("DELETE FROM sandbox_history WHERE executedAt < :olderThan")
    suspend fun deleteOldHistory(olderThan: Long)

    @Query("SELECT COUNT(*) FROM sandbox_sessions")
    fun getSessionCount(): Flow<Int>
}
