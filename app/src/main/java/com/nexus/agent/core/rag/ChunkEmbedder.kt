package com.nexus.agent.core.rag

import com.nexus.agent.core.memory.LocalEmbedder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChunkEmbedder @Inject constructor(
    private val localEmbedder: LocalEmbedder,
) {
    fun embed(text: String): FloatArray = localEmbedder.embed(text)

    fun embedBatch(texts: List<String>): List<FloatArray> = texts.map { embed(it) }

    fun similarity(a: FloatArray, b: FloatArray): Float = localEmbedder.cosineSimilarity(a, b)
}