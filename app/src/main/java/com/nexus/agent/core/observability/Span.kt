package com.nexus.agent.core.observability

import com.nexus.agent.data.local.SpanEntity

enum class SpanStatus { OK, ERROR, TIMEOUT }

/**
 * In-memory Span DTO. Convert to [SpanEntity] before persisting via Room.
 */
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
) {
    fun toEntity(traceId: String = "default"): SpanEntity = SpanEntity(
        id = id,
        traceId = traceId,
        parentId = parentId,
        name = name,
        service = agentType,
        status = status.name.lowercase(),
        startTime = startTime,
        endTime = endTime,
        durationMs = if (durationMs > 0) durationMs else
            endTime?.let { it - startTime },
        tags = tags.entries.joinToString(",") { "${it.key}=${it.value}" },
        logs = "",
        errorMessage = error,
        errorType = null,
        stackTrace = null,
    )
}

fun SpanEntity.toSpan(): Span = Span(
    id = id,
    name = name,
    parentId = parentId,
    startTime = startTime,
    endTime = endTime,
    durationMs = durationMs ?: 0,
    status = when (status) {
        "error" -> SpanStatus.ERROR
        "timeout" -> SpanStatus.TIMEOUT
        else -> SpanStatus.OK
    },
    error = errorMessage,
    tags = if (tags.isNotBlank()) {
        tags.split(",").mapNotNull {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
    } else emptyMap(),
    agentType = service,
)
