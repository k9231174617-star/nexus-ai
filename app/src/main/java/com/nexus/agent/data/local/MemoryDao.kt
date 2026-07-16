package com.nexus.agent.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val content: String,
    val embedding: ByteArray?, // serialized float array
    val importanceScore: Float = 0.5f,
    val category: String = "general", // general, code, conversation, system
    val source: String? = null, // where this memory came from
    val createdAt: Long = System.currentTimeMillis(),
    val accessedAt: Long = System.currentTimeMillis(),
    val accessCount: Int = 0,
    val isPinned: Boolean = false,
    val tags: String = "", // comma-separated tags
    val relatedProjectId: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MemoryEntity
        return id == other.id
    }
    override fun hashCode(): Int = id.hashCode()
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY importanceScore DESC, accessedAt DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE category = :category ORDER BY importanceScore DESC")
    fun getMemoriesByCategory(category: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE id = :memoryId")
    suspend fun getMemoryById(memoryId: String): MemoryEntity?

    @Query("SELECT * FROM memories WHERE isPinned = 1 ORDER BY createdAt DESC")
    fun getPinnedMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE tags LIKE '%' || :tag || '%'")
    fun getMemoriesByTag(tag: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE relatedProjectId = :projectId ORDER BY createdAt DESC")
    fun getMemoriesByProject(projectId: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE createdAt > :since ORDER BY importanceScore DESC, accessCount DESC LIMIT :limit")
    suspend fun getRecentMemories(since: Long, limit: Int = 50): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE importanceScore >= :minImportance ORDER BY accessedAt DESC LIMIT :limit")
    suspend fun getImportantMemories(minImportance: Float = 0.7f, limit: Int = 100): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemories(memories: List<MemoryEntity>)

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Query("UPDATE memories SET accessedAt = :timestamp, accessCount = accessCount + 1 WHERE id = :memoryId")
    suspend fun recordAccess(memoryId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE memories SET importanceScore = :score WHERE id = :memoryId")
    suspend fun updateImportance(memoryId: String, score: Float)

    @Query("UPDATE memories SET isPinned = :pinned WHERE id = :memoryId")
    suspend fun setPinned(memoryId: String, pinned: Boolean)

    @Query("UPDATE memories SET embedding = :embedding WHERE id = :memoryId")
    suspend fun updateEmbedding(memoryId: String, embedding: ByteArray)

    @Delete
    suspend fun deleteMemory(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :memoryId")
    suspend fun deleteMemoryById(memoryId: String)

    @Query("DELETE FROM memories WHERE createdAt < :olderThan AND isPinned = 0")
    suspend fun deleteOldMemories(olderThan: Long)

    @Query("DELETE FROM memories WHERE importanceScore < :threshold AND isPinned = 0")
    suspend fun pruneLowImportance(threshold: Float = 0.2f)

    @Query("SELECT COUNT(*) FROM memories")
    fun getMemoryCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM memories WHERE category = :category")
    suspend fun getMemoryCountByCategory(category: String): Int
}
