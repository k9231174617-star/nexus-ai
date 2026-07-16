package com.nexus.agent.core.rag

import com.nexus.agent.core.memory.LocalEmbedder
import javax.inject.Inject
import javax.inject.Singleton

data class RetrievalResult(
    val chunk: DocumentChunk,
    val score: Float,
)

@Singleton
class VectorSearch @Inject constructor(
    private val localEmbedder: LocalEmbedder,
) {
    private val index = mutableMapOf<String, Pair<DocumentChunk, FloatArray>>()

    fun store(chunk: DocumentChunk, embedding: FloatArray) {
        index[chunk.id] = Pair(chunk, embedding)
    }

    fun search(query: FloatArray, topK: Int, minScore: Float = 0f): List<RetrievalResult> {
        return index.values
            .map { (chunk, vec) ->
                RetrievalResult(chunk, localEmbedder.cosineSimilarity(query, vec))
            }
            .filter { it.score >= minScore }
            .sortedByDescending { it.score }
            .take(topK)
    }

    fun deleteByDocId(docId: String) {
        index.entries.removeIf { (_, pair) -> pair.first.docId == docId }
    }

    fun listDocumentIds(): List<String> = index.values.map { it.first.docId }.distinct()

    fun totalChunks(): Int = index.size
}