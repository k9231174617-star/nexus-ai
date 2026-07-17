package com.nexus.agent.core.rag

data class RetrievalResult(
    val chunk: DocumentChunk,
    val score: Float,
)
