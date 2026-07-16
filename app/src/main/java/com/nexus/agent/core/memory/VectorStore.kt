package com.nexus.agent.core.memory

import javax.inject.Inject
import javax.inject.Singleton

data class VectorSearchResult(val id: Long, val score: Float)

@Singleton
class VectorStore @Inject constructor(
    private val localEmbedder: LocalEmbedder,
) {
    // In-memory HNSW-lite: flat index for < 10k entries
    // In production: use FAISS via JNI (nativelibs/faiss/)
    private val index = mutableMapOf<Long, FloatArray>()

    fun store(id: Long, embedding: FloatArray) {
        index[id] = embedding
    }

    fun search(query: FloatArray, topK: Int): List<VectorSearchResult> {
        return index.entries
            .map { (id, vec) -> VectorSearchResult(id, localEmbedder.cosineSimilarity(query, vec)) }
            .sortedByDescending { it.score }
            .take(topK)
    }

    fun remove(id: Long) { index.remove(id) }

    fun size() = index.size

    fun clear() = index.clear()

    fun bulkStore(entries: Map<Long, FloatArray>) {
        index.putAll(entries)
    }
}