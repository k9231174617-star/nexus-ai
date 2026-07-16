package com.nexus.agent.core.rag

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class RAGResult(
    val answer: String,
    val sources: List<RetrievalResult>,
    val confidence: Float,
    val tokensUsed: Int,
)

@Singleton
class RAGSystem @Inject constructor(
    private val ingestor: DocumentIngestor,
    private val embedder: ChunkEmbedder,
    private val search: VectorSearch,
    private val assembler: ContextAssembler,
) {
    suspend fun ingestDocument(uri: Uri, docId: String? = null): String =
        withContext(Dispatchers.IO) {
            val chunks = ingestor.ingest(uri, docId)
            chunks.forEach { chunk ->
                val embedding = embedder.embed(chunk.content)
                search.store(chunk, embedding)
            }
            "Ingested ${chunks.size} chunks from document"
        }

    suspend fun query(
        question: String,
        topK: Int = 5,
        minScore: Float = 0.3f,
    ): RAGResult = withContext(Dispatchers.IO) {
        val queryEmbedding = embedder.embed(question)
        val retrieved = search.search(queryEmbedding, topK, minScore)
        val context = assembler.assemble(question, retrieved)

        RAGResult(
            answer = context,
            sources = retrieved,
            confidence = retrieved.firstOrNull()?.score ?: 0f,
            tokensUsed = context.length / 4,
        )
    }

    suspend fun deleteDocument(docId: String) = withContext(Dispatchers.IO) {
        search.deleteByDocId(docId)
    }

    suspend fun listDocuments(): List<String> = withContext(Dispatchers.IO) {
        search.listDocumentIds()
    }

    suspend fun getStats(): Map<String, Any> = withContext(Dispatchers.IO) {
        mapOf(
            "totalChunks" to search.totalChunks(),
            "documents" to search.listDocumentIds().size,
        )
    }
}