package com.nexus.agent.core.observability

enum class SpanStatus { OK, ERROR, TIMEOUT }

data class Span(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val durationMs: Long = 0,
    val status: SpanStatus = SpanStatus.OK,
    val error: String? = null,
    val tags: Map<String, String> = emptyMap(),
    val agentType: String = "MAIN",
)