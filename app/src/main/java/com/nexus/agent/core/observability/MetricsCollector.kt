package com.nexus.agent.core.observability

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class AgentMetrics(
    val totalRequests: Long = 0,
    val successRequests: Long = 0,
    val failedRequests: Long = 0,
    val avgLatencyMs: Double = 0.0,
    val totalTokens: Long = 0,
    val cacheHits: Long = 0,
    val cacheMisses: Long = 0,
)

@Singleton
class MetricsCollector @Inject constructor() {
    private val metricsMap = mutableMapOf<String, AgentMetrics>()
    private val latencies = mutableListOf<Long>()

    private val _metrics = MutableStateFlow<Map<String, AgentMetrics>>(emptyMap())
    val metrics: StateFlow<Map<String, AgentMetrics>> = _metrics

    fun recordRequest(agentType: String, latencyMs: Long, success: Boolean, tokens: Int = 0) {
        val current = metricsMap.getOrDefault(agentType, AgentMetrics())
        latencies.add(latencyMs)
        val avgLatency = latencies.average()
        metricsMap[agentType] = current.copy(
            totalRequests = current.totalRequests + 1,
            successRequests = if (success) current.successRequests + 1 else current.successRequests,
            failedRequests = if (!success) current.failedRequests + 1 else current.failedRequests,
            avgLatencyMs = avgLatency,
            totalTokens = current.totalTokens + tokens,
        )
        _metrics.value = metricsMap.toMap()
    }

    fun recordSpan(span: Span) {
        recordRequest(span.agentType, span.durationMs, span.status == SpanStatus.OK)
    }

    fun recordCacheHit(agentType: String) {
        val current = metricsMap.getOrDefault(agentType, AgentMetrics())
        metricsMap[agentType] = current.copy(cacheHits = current.cacheHits + 1)
        _metrics.value = metricsMap.toMap()
    }

    fun getOverall(): AgentMetrics = metricsMap.values.fold(AgentMetrics()) { acc, m ->
        acc.copy(
            totalRequests = acc.totalRequests + m.totalRequests,
            successRequests = acc.successRequests + m.successRequests,
            failedRequests = acc.failedRequests + m.failedRequests,
            totalTokens = acc.totalTokens + m.totalTokens,
            cacheHits = acc.cacheHits + m.cacheHits,
            avgLatencyMs = if (latencies.isEmpty()) 0.0 else latencies.average(),
        )
    }

    fun reset() {
        metricsMap.clear()
        latencies.clear()
        _metrics.value = emptyMap()
    }
}