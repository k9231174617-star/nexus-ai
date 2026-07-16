package com.nexus.agent.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "response_cache")
data class CacheEntryEntity(
    @PrimaryKey val id: String,
    val queryHash: String, // hash of the query/prompt
    val queryText: String, // original query text (for semantic matching)
    val responseText: String,
    val modelId: String, // which model generated this
    val embedding: ByteArray? = null, // for semantic cache lookup
    val hitCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null, // null = never expires
    val ttlMs: Long = 3600000, // default 1 hour
    val tokenCount: Int = 0,
    val latencyMs: Long = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CacheEntryEntity
        return id == other.id
    }
    override fun hashCode(): Int = id.hashCode()
}

@Entity(tableName = "cache_stats")
data class CacheStatsEntity(
    @PrimaryKey val id: String = "global",
    val totalHits: Long = 0,
    val totalMisses: Long = 0,
    val totalSavingsMs: Long = 0,
    val totalTokensSaved: Long = 0,
    val avgHitLatencyMs: Long = 0,
    val lastResetAt: Long = System.currentTimeMillis()
)

@Dao
interface CacheDao {
    // Cache entries
    @Query("SELECT * FROM response_cache ORDER BY lastAccessedAt DESC")
    fun getAllEntries(): Flow<List<CacheEntryEntity>>

    @Query("SELECT * FROM response_cache WHERE queryHash = :hash LIMIT 1")
    suspend fun getEntryByHash(hash: String): CacheEntryEntity?

    @Query("SELECT * FROM response_cache WHERE queryText = :queryText AND modelId = :modelId LIMIT 1")
    suspend fun getEntryByQuery(queryText: String, modelId: String): CacheEntryEntity?

    @Query("SELECT * FROM response_cache WHERE (expiresAt IS NULL OR expiresAt > :now) ORDER BY hitCount DESC, lastAccessedAt DESC LIMIT :limit")
    suspend fun getValidEntries(now: Long = System.currentTimeMillis(), limit: Int = 100): List<CacheEntryEntity>

    @Query("SELECT * FROM response_cache WHERE expiresAt < :now OR (lastAccessedAt < :staleTime AND hitCount = 0)")
    suspend fun getExpiredEntries(now: Long = System.currentTimeMillis(), staleTime: Long = System.currentTimeMillis() - 86400000): List<CacheEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: CacheEntryEntity)

    @Update
    suspend fun updateEntry(entry: CacheEntryEntity)

    @Query("UPDATE response_cache SET hitCount = hitCount + 1, lastAccessedAt = :timestamp WHERE id = :entryId")
    suspend fun recordHit(entryId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE response_cache SET embedding = :embedding WHERE id = :entryId")
    suspend fun updateEmbedding(entryId: String, embedding: ByteArray)

    @Delete
    suspend fun deleteEntry(entry: CacheEntryEntity)

    @Query("DELETE FROM response_cache WHERE id = :entryId")
    suspend fun deleteEntryById(entryId: String)

    @Query("DELETE FROM response_cache WHERE expiresAt < :now")
    suspend fun deleteExpiredEntries(now: Long = System.currentTimeMillis())

    @Query("DELETE FROM response_cache")
    suspend fun clearAllEntries()

    @Query("SELECT COUNT(*) FROM response_cache")
    fun getEntryCount(): Flow<Int>

    @Query("SELECT SUM(hitCount) FROM response_cache")
    suspend fun getTotalHits(): Int?

    // Stats
    @Query("SELECT * FROM cache_stats WHERE id = 'global'")
    suspend fun getStats(): CacheStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStats(stats: CacheStatsEntity)

    @Query("UPDATE cache_stats SET totalHits = totalHits + :hits, totalMisses = totalMisses + :misses, totalSavingsMs = totalSavingsMs + :savingsMs, totalTokensSaved = totalTokensSaved + :tokensSaved WHERE id = 'global'")
    suspend fun updateStats(hits: Long, misses: Long, savingsMs: Long, tokensSaved: Long)

    @Query("UPDATE cache_stats SET totalHits = 0, totalMisses = 0, totalSavingsMs = 0, totalTokensSaved = 0, lastResetAt = :timestamp WHERE id = 'global'")
    suspend fun resetStats(timestamp: Long = System.currentTimeMillis())
}
