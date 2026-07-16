package com.nexus.agent.core.rag

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContextAssembler @Inject constructor() {

    fun assemble(question: String, results: List<RetrievalResult>): String {
        if (results.isEmpty()) return "No relevant context found."
        return buildString {
            append("[RETRIEVED CONTEXT]\n\n")
            results.forEachIndexed { i, result ->
                append("Source ${i + 1} (relevance: ${"%.2f".format(result.score)})\n")
                append("Doc: ${result.chunk.docId} | Chunk: ${result.chunk.chunkIndex + 1}/${result.chunk.totalChunks}\n")
                append(result.chunk.content)
                append("\n\n---\n\n")
            }
            append("[END CONTEXT]\n\n")
            append("Question: $question")
        }
    }

    fun formatCitation(result: RetrievalResult, index: Int): String =
        "[${index + 1}] ${result.chunk.metadata["name"] ?: result.chunk.docId} " +
        "(chunk ${result.chunk.chunkIndex + 1})"

    fun deduplicateResults(results: List<RetrievalResult>): List<RetrievalResult> {
        val seen = mutableSetOf<String>()
        return results.filter { result ->
            val key = result.chunk.content.take(50)
            seen.add(key)
        }
    }
}