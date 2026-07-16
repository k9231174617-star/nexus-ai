package com.nexus.agent.core.cache

import com.nexus.agent.core.memory.LocalEmbedder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SemanticCache @Inject constructor(
    private val embedder: LocalEmbedder,
) {
    private data class SemanticEntry(
        val entry: CacheEntry,
        val embedding: FloatArray,
    )

    private val index = mutableMapOf<String, MutableList<SemanticEntry>>()

    fun store(entry: CacheEntry) {
        val embedding = embedder.embed(entry.prompt)
        index.getOrPut(entry.agentType) { mutableListOf() }
            .add(SemanticEntry(entry, embedding))
    }

    fun findSimilar(prompt: String, threshold: Float, agentType: String): CacheEntry? {
        val queryEmb = embedder.embed(prompt)
        return index[agentType]
            ?.filter { System.currentTimeMillis() < it.entry.expiresAt }
            ?.maxByOrNull { embedder.cosineSimilarity(queryEmb, it.embedding) }
            ?.let { best ->
                val score = embedder.cosineSimilarity(queryEmb, best.embedding)
                if (score >= threshold) best.entry else null
            }
    }

    fun clear(agentType: String) { index.remove(agentType) }
    fun size(): Int = index.values.sumOf { it.size }
}