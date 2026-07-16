package com.nexus.agent.core.observability

import javax.inject.Inject
import javax.inject.Singleton

data class Bottleneck(
    val spanName: String,
    val avgDurationMs: Double,
    val count: Int,
    val errorRate: Float,
    val severity: Severity,
)

enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }

@Singleton
class BottleneckAnalyzer @Inject constructor(
    private val tracer: Tracer,
) {
    fun analyze(): List<Bottleneck> {
        val spans = tracer.spans.value
        val grouped = spans.groupBy { it.name }

        return grouped.map { (name, group) ->
            val avgDuration = group.mapNotNull { it.endTime?.let { e -> e - it.startTime } }.average()
            val errorRate = group.count { it.status == SpanStatus.ERROR }.toFloat() / group.size
            val severity = when {
                avgDuration > 10_000 || errorRate > 0.5f -> Severity.CRITICAL
                avgDuration > 5_000  || errorRate > 0.3f -> Severity.HIGH
                avgDuration > 2_000  || errorRate > 0.1f -> Severity.MEDIUM
                else -> Severity.LOW
            }
            Bottleneck(
                spanName = name,
                avgDurationMs = avgDuration,
                count = group.size,
                errorRate = errorRate,
                severity = severity,
            )
        }.sortedByDescending { it.avgDurationMs }
    }

    fun getSlowSpans(thresholdMs: Long = 3000): List<Span> =
        tracer.spans.value.filter { (it.durationMs) > thresholdMs }

    fun getErrorSpans(): List<Span> =
        tracer.spans.value.filter { it.status == SpanStatus.ERROR }
}