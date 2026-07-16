package com.nexus.agent.core.memory

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class LocalEmbedder @Inject constructor() {
    // Lightweight TF-IDF style embedding (512 dims)
    // In production: replace with ONNX MiniLM or TFLite model
    private val DIMS = 512

    fun embed(text: String): FloatArray {
        val vec = FloatArray(DIMS)
        val tokens = tokenize(text)
        val tf = termFrequency(tokens)

        tf.forEach { (term, freq) ->
            val hash = term.hashCode()
            val idx = Math.abs(hash) % DIMS
            vec[idx] += freq.toFloat()
            // Second hash for collision reduction
            val idx2 = Math.abs(hash * 31 + 17) % DIMS
            vec[idx2] += freq * 0.5f
        }
        return normalize(vec)
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Dimension mismatch" }
        var dot = 0f; var normA = 0f; var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0f) 0f else dot / denom
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .replace(Regex("[^a-zа-яё0-9\\s]"), " ")
            .split("\\s+".toRegex())
            .filter { it.length > 2 }

    private fun termFrequency(tokens: List<String>): Map<String, Int> {
        val tf = mutableMapOf<String, Int>()
        tokens.forEach { tf[it] = (tf[it] ?: 0) + 1 }
        return tf
    }

    private fun normalize(vec: FloatArray): FloatArray {
        val norm = sqrt(vec.map { it * it }.sum())
        return if (norm == 0f) vec else FloatArray(vec.size) { vec[it] / norm }
    }
}