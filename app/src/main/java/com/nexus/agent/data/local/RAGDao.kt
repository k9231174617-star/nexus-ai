package com.nexus.agent.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "rag_documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val filePath: String,
    val fileType: String, // pdf, txt, md, docx, etc.
    val fileSize: Long = 0,
    val content: String? = null, // extracted text content
    val summary: String? = null,
    val status: String = "pending", // pending, processing, indexed, error
    val chunkCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val indexedAt: Long? = null,
    val errorMessage: String? = null,
    val tags: String = "",
    val isIndexed: Boolean = false
)

@Entity(tableName = "rag_chunks")
data class ChunkEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val content: String,
    val embedding: ByteArray?, // serialized float array
    val startIndex: Int = 0,
    val endIndex: Int = 0,
    val chunkIndex: Int = 0, // order within document
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ChunkEntity
        return id == other.id
    }
    override fun hashCode(): Int = id.hashCode()
}

@Entity(tableName = "rag_queries")
data class QueryEntity(
    @PrimaryKey val id: String,
    val queryText: String,
    val embedding: ByteArray? = null,
    val results: String = "", // JSON serialized results
    val resultCount: Int = 0,
    val executedAt: Long = System.currentTimeMillis(),
    val executionTimeMs: Long = 0,
    val topDocumentIds: String = "" // comma-separated
)

@Dao
interface RAGDao {
    // Documents
    @Query("SELECT * FROM rag_documents ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM rag_documents WHERE id = :documentId")
    suspend fun getDocumentById(documentId: String): DocumentEntity?

    @Query("SELECT * FROM rag_documents WHERE status = :status ORDER BY createdAt DESC")
    fun getDocumentsByStatus(status: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM rag_documents WHERE isIndexed = 1 ORDER BY indexedAt DESC")
    fun getIndexedDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM rag_documents WHERE title LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%'")
    fun searchDocuments(query: String): Flow<List<DocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("UPDATE rag_documents SET status = :status, errorMessage = :error WHERE id = :documentId")
    suspend fun updateDocumentStatus(documentId: String, status: String, error: String? = null)

    @Query("UPDATE rag_documents SET isIndexed = 1, indexedAt = :timestamp, chunkCount = :chunkCount WHERE id = :documentId")
    suspend fun markAsIndexed(documentId: String, chunkCount: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE rag_documents SET content = :content, summary = :summary WHERE id = :documentId")
    suspend fun updateDocumentContent(documentId: String, content: String, summary: String?)

    @Delete
    suspend fun deleteDocument(document: DocumentEntity)

    @Query("DELETE FROM rag_documents WHERE id = :documentId")
    suspend fun deleteDocumentById(documentId: String)

    @Query("SELECT COUNT(*) FROM rag_documents")
    fun getDocumentCount(): Flow<Int>

    // Chunks
    @Query("SELECT * FROM rag_chunks WHERE documentId = :documentId ORDER BY chunkIndex ASC")
    suspend fun getChunksByDocument(documentId: String): List<ChunkEntity>

    @Query("SELECT * FROM rag_chunks WHERE id = :chunkId")
    suspend fun getChunkById(chunkId: String): ChunkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: ChunkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<ChunkEntity>)

    @Query("DELETE FROM rag_chunks WHERE documentId = :documentId")
    suspend fun deleteChunksByDocument(documentId: String)

    @Query("UPDATE rag_chunks SET embedding = :embedding WHERE id = :chunkId")
    suspend fun updateChunkEmbedding(chunkId: String, embedding: ByteArray)

    @Query("SELECT COUNT(*) FROM rag_chunks WHERE documentId = :documentId")
    suspend fun getChunkCountByDocument(documentId: String): Int

    // Queries
    @Query("SELECT * FROM rag_queries ORDER BY executedAt DESC LIMIT :limit")
    suspend fun getRecentQueries(limit: Int = 50): List<QueryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuery(query: QueryEntity)

    @Query("DELETE FROM rag_queries WHERE executedAt < :olderThan")
    suspend fun deleteOldQueries(olderThan: Long)
}
