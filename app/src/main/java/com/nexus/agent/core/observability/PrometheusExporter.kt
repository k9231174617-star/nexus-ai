package com.nexus.agent.core.observability

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PrometheusMetric(
    val name: String,
    val value: Double,
    val labels: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Prometheus-compatible metrics exporter.
 * Collects metrics in-memory and exposes them in Prometheus text format.
 */
@Singleton
class PrometheusExporter @Inject constructor() {

    private val _metrics = MutableStateFlow<List<PrometheusMetric>>(emptyList())
    val metrics: StateFlow<List<PrometheusMetric>> = _metrics

    private val counters = mutableMapOf<String, Double>()
    private val gauges = mutableMapOf<String, Double>()
    private val histograms = mutableMapOf<String, MutableList<Double>>()

    fun incrementCounter(name: String, amount: Double = 1.0, labels: Map<String, String> = emptyMap()) {
        val key = buildKey(name, labels)
        counters[key] = (counters[key] ?: 0.0) + amount
        emitMetric(name, counters[key]!!, labels)
    }

    fun setGauge(name: String, value: Double, labels: Map<String, String> = emptyMap()) {
        val key = buildKey(name, labels)
        gauges[key] = value
        emitMetric(name, value, labels)
    }

    fun observeHistogram(name: String, value: Double, labels: Map<String, String> = emptyMap()) {
        val key = buildKey(name, labels)
        histograms.getOrPut(key) { mutableListOf() }.add(value)
        emitMetric(name, value, labels)
    }

    fun exportText(): String {
        val sb = StringBuilder()
        counters.forEach { (key, value) ->
            val (name, labels) = parseKey(key)
            sb.appendLine("# HELP ${name}_total Counter metric")
            sb.appendLine("# TYPE ${name}_total counter")
            sb.appendLine("${name}_total${formatLabels(labels)} $value")
        }
        gauges.forEach { (key, value) ->
            val (name, labels) = parseKey(key)
            sb.appendLine("# HELP $name Gauge metric")
            sb.appendLine("# TYPE $name gauge")
            sb.appendLine("$name${formatLabels(labels)} $value")
        }
        histograms.forEach { (key, values) ->
            val (name, labels) = parseKey(key)
            sb.appendLine("# HELP $name Histogram metric")
            sb.appendLine("# TYPE $name histogram")
            sb.appendLine("${name}_count${formatLabels(labels)} ${values.size}")
            sb.appendLine("${name}_sum${formatLabels(labels)} ${values.sum()}")
        }
        return sb.toString()
    }

    fun reset() {
        counters.clear()
        gauges.clear()
        histograms.clear()
        _metrics.value = emptyList()
    }

    private fun emitMetric(name: String, value: Double, labels: Map<String, String>) {
        _metrics.value = _metrics.value + PrometheusMetric(name, value, labels)
    }

    private fun buildKey(name: String, labels: Map<String, String>): String {
        return if (labels.isEmpty()) name else "$name{${labels.entries.joinToString(",") { "${it.key}=${it.value}" }}}"
    }

    private fun parseKey(key: String): Pair<String, Map<String, String>> {
        val braceIdx = key.indexOf('{')
        return if (braceIdx > 0) {
            val name = key.substring(0, braceIdx)
            val labelsStr = key.substring(braceIdx + 1, key.lastIndexOf('}'))
            val labels = labelsStr.split(",").mapNotNull {
                val eqIdx = it.indexOf('=')
                if (eqIdx > 0) it.substring(0, eqIdx).trim() to it.substring(eqIdx + 1).trim()
                else null
            }.toMap()
            name to labels
        } else {
            key to emptyMap()
        }
    }

    private fun formatLabels(labels: Map<String, String>): String {
        if (labels.isEmpty()) return ""
        return labels.entries.joinToString(",", "{", "}") { "${it.key}=\"${it.value}\"" }
    }
}
