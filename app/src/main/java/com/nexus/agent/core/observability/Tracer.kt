package com.nexus.agent.core.observability

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Tracer @Inject constructor(
    private val metricsCollector: MetricsCollector,
) {
    private val _spans = MutableStateFlow<List<Span>>(emptyList())
    val spans: StateFlow<List<Span>> = _spans

    fun startSpan(name: String, parentId: String? = null): Span {
        val span = Span(
            id = UUID.randomUUID().toString(),
            name = name,
            parentId = parentId,
            startTime = System.currentTimeMillis(),
        )
        _spans.value = _spans.value + span
        return span
    }

    fun endSpan(span: Span, status: SpanStatus = SpanStatus.OK, error: String? = null) {
        val completed = span.copy(
            endTime = System.currentTimeMillis(),
            status = status,
            error = error,
            durationMs = System.currentTimeMillis() - span.startTime,
        )
        _spans.value = _spans.value.map { if (it.id == span.id) completed else it }
        metricsCollector.recordSpan(completed)
    }

    inline fun <T> trace(name: String, parentId: String? = null, block: (Span) -> T): T {
        val span = startSpan(name, parentId)
        return try {
            val result = block(span)
            endSpan(span, SpanStatus.OK)
            result
        } catch (e: Exception) {
            endSpan(span, SpanStatus.ERROR, e.message)
            throw e
        }
    }

    fun getRecentSpans(limit: Int = 50): List<Span> =
        _spans.value.takeLast(limit).sortedByDescending { it.startTime }

    fun clearSpans() { _spans.value = emptyList() }
}