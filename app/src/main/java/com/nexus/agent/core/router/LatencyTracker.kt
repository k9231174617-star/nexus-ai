package com.nexus.agent.core.router

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/**
 * LatencyTracker — отслеживает задержки (latency) запросов к LLM-провайдерам,
 * вычисляет скользящие средние, перцентили и health-score.
 */
class LatencyTracker {

    // ─────────────────────────────────────────────────────────────────────────
    // Data classes
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Запись о latency одного запроса.
     */
    data class LatencyRecord(
        val provider: String,
        val model: String,
        val endpoint: String,
        val latencyMs: Long,           // полная задержка (TTFB + generation)
        val timeToFirstTokenMs: Long,  // время до первого токена
        val tokensPerSecond: Double,  // скорость генерации
        val timestamp: Long = System.currentTimeMillis(),
        val success: Boolean = true,
        val errorType: String? = null
    )

    /**
     * Статистика latency для провайдера/модели.
     */
    data class LatencyStats(
        val provider: String,
        val model: String,
        val count: Int,
        val avgLatencyMs: Double,
        val minLatencyMs: Long,
        val maxLatencyMs: Long,
        val p50: Double,
        val p90: Double,
        val p95: Double,
        val p99: Double,
        val stdDev: Double,
        val avgTTFB: Double,
        val avgTokensPerSecond: Double,
        val successRate: Double,
        val lastUpdated: Long
    )

    /**
     * Health score провайдера (0.0 - 1.0).
     */
    data class HealthScore(
        val provider: String,
        val model: String,
        val score: Double,           // 0.0-1.0
        val latencyScore: Double,     // 0.0-1.0
        val availabilityScore: Double, // 0.0-1.0
        val stabilityScore: Double,   // 0.0-1.0
        val timestamp: Long
    )

    /**
     * Настройки для отслеживания.
     */
    data class TrackingConfig(
        val windowSize: Int = 100,           // размер окна для скользящего среднего
        val maxHistoryPerProvider: Int = 1000,
        val slowThresholdMs: Long = 5000,    // порог "медленного" запроса
        val timeoutThresholdMs: Long = 30000 // порог таймаута
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Internal state
    // ─────────────────────────────────────────────────────────────────────────

    private val config: TrackingConfig = TrackingConfig()

    // История записей по провайдеру:модели
    private val history = ConcurrentHashMap<String, MutableList<LatencyRecord>>()
    private val historyMutex = Mutex()

    // Скользящие средние (EWMA — Exponentially Weighted Moving Average)
    private val ewmaLatency = ConcurrentHashMap<String, Double>()      // key: "provider:model"
    private val ewmaTTFB = ConcurrentHashMap<String, Double>()
    private val ewmaTokensPerSec = ConcurrentHashMap<String, Double>()

    // Счётчики успехов/неудач
    private val successCount = ConcurrentHashMap<String, Int>()
    private val failureCount = ConcurrentHashMap<String, Int>()

    // Последние health scores
    private val healthScores = ConcurrentHashMap<String, HealthScore>()

    // ─────────────────────────────────────────────────────────────────────────
    // Recording
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Записывает latency запроса.
     */
    suspend fun record(record: LatencyRecord) {
        val key = "${record.provider}:${record.model}"

        // Добавляем в историю
        historyMutex.withLock {
            val list = history.getOrPut(key) { mutableListOf() }
            list.add(record)
            if (list.size > config.maxHistoryPerProvider) {
                list.removeAt(0)
            }
        }

        // Обновляем EWMA
        updateEwma(key, record)

        // Обновляем счётчики
        if (record.success) {
            successCount.merge(key, 1) { old, _ -> old + 1 }
        } else {
            failureCount.merge(key, 1) { old, _ -> old + 1 }
        }

        // Пересчитываем health score
        recalculateHealthScore(record.provider, record.model)
    }

    /**
     * Упрощённая запись только с latency.
     */
    suspend fun recordLatency(
        provider: String,
        model: String,
        latencyMs: Long,
        success: Boolean = true
    ) {
        record(
            LatencyRecord(
                provider = provider,
                model = model,
                endpoint = "",
                latencyMs = latencyMs,
                timeToFirstTokenMs = latencyMs,
                tokensPerSecond = 0.0,
                success = success
            )
        )
    }

    /**
     * Записывает TTFB (Time To First Token) отдельно.
     */
    suspend fun recordTTFB(provider: String, model: String, ttfbMs: Long) {
        val key = "$provider:$model"
        ewmaTTFB.merge(key, ttfbMs.toDouble()) { old, new ->
            old * 0.7 + new * 0.3
        }
    }

    /**
     * Записывает скорость генерации токенов.
     */
    suspend fun recordTokensPerSecond(provider: String, model: String, tps: Double) {
        val key = "$provider:$model"
        ewmaTokensPerSec.merge(key, tps) { old, new ->
            old * 0.7 + new * 0.3
        }
    }

    /**
     * Обновляет EWMA для latency.
     */
    private fun updateEwma(key: String, record: LatencyRecord) {
        val alpha = 0.3  // коэффициент сглаживания

        ewmaLatency.merge(key, record.latencyMs.toDouble()) { old, new ->
            old * (1 - alpha) + new * alpha
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Querying statistics
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Возвращает полную статистику для провайдера:модели.
     */
    suspend fun getStats(provider: String, model: String): LatencyStats? {
        val key = "$provider:$model"
        val records = historyMutex.withLock {
            history[key]?.toList() ?: return null
        }

        if (records.isEmpty()) return null

        val latencies = records.map { it.latencyMs.toDouble() }.sorted()
        val ttbList = records.map { it.timeToFirstTokenMs.toDouble() }
        val tpsList = records.filter { it.tokensPerSecond > 0 }.map { it.tokensPerSecond }

        val total = records.size
        val successes = records.count { it.success }
        val avg = latencies.average()
        val min = latencies.minOrNull() ?: 0.0
        val max = latencies.maxOrNull() ?: 0.0

        return LatencyStats(
            provider = provider,
            model = model,
            count = total,
            avgLatencyMs = avg,
            minLatencyMs = min.toLong(),
            maxLatencyMs = max.toLong(),
            p50 = percentile(latencies, 0.50),
            p90 = percentile(latencies, 0.90),
            p95 = percentile(latencies, 0.95),
            p99 = percentile(latencies, 0.99),
            stdDev = standardDeviation(latencies, avg),
            avgTTFB = ttbList.average(),
            avgTokensPerSecond = if (tpsList.isNotEmpty()) tpsList.average() else 0.0,
            successRate = successes.toDouble() / total,
            lastUpdated = records.last().timestamp
        )
    }

    /**
     * Возвращает EWMA latency.
     */
    fun getEwmaLatency(provider: String, model: String): Double? {
        return ewmaLatency["$provider:$model"]
    }

    /**
     * Возвращает EWMA TTFB.
     */
    fun getEwmaTTFB(provider: String, model: String): Double? {
        return ewmaTTFB["$provider:$model"]
    }

    /**
     * Возвращает скользящее среднее скорости генерации.
     */
    fun getEwmaTokensPerSecond(provider: String, model: String): Double? {
        return ewmaTokensPerSec["$provider:$model"]
    }

    /**
     * Возвращает success rate.
     */
    fun getSuccessRate(provider: String, model: String): Double {
        val key = "$provider:$model"
        val successes = successCount[key] ?: 0
        val failures = failureCount[key] ?: 0
        val total = successes + failures
        return if (total > 0) successes.toDouble() / total else 1.0
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Health scoring
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Пересчитывает health score для провайдера:модели.
     */
    private suspend fun recalculateHealthScore(provider: String, model: String) {
        val key = "$provider:$model"
        val stats = getStats(provider, model) ?: return

        // Latency score: чем ниже latency относительно порога — тем выше score
        val latencyScore = when {
            stats.avgLatencyMs <= 500 -> 1.0
            stats.avgLatencyMs >= config.timeoutThresholdMs -> 0.0
            else -> 1.0 - (stats.avgLatencyMs - 500) / (config.timeoutThresholdMs - 500).toDouble()
        }

        // Availability score
        val availabilityScore = stats.successRate

        // Stability score: обратно пропорционально stdDev (низкий stdDev = стабильный)
        val stabilityScore = when {
            stats.stdDev <= 100 -> 1.0
            stats.stdDev >= 2000 -> 0.0
            else -> 1.0 - (stats.stdDev - 100) / 1900.0
        }

        // Общий score — взвешенная сумма
        val totalScore = latencyScore * 0.4 + availabilityScore * 0.4 + stabilityScore * 0.2

        healthScores[key] = HealthScore(
            provider = provider,
            model = model,
            score = totalScore.coerceIn(0.0, 1.0),
            latencyScore = latencyScore.coerceIn(0.0, 1.0),
            availabilityScore = availabilityScore.coerceIn(0.0, 1.0),
            stabilityScore = stabilityScore.coerceIn(0.0, 1.0),
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Возвращает health score.
     */
    fun getHealthScore(provider: String, model: String): HealthScore? {
        return healthScores["$provider:$model"]
    }

    /**
     * Возвращает все health scores.
     */
    fun getAllHealthScores(): List<HealthScore> = healthScores.values.toList()

    /**
     * Возвращает лучшие провайдеры по health score.
     */
    fun getBestProviders(limit: Int = 5): List<HealthScore> {
        return healthScores.values
            .sortedByDescending { it.score }
            .take(limit)
    }

    /**
     * Проверяет, является ли провайдер "здоровым".
     */
    fun isHealthy(provider: String, model: String, minScore: Double = 0.5): Boolean {
        return getHealthScore(provider, model)?.score?.let { it >= minScore } ?: true
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Comparison & ranking
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Сравнивает latency нескольких провайдеров.
     */
    suspend fun compare(providers: List<Pair<String, String>>): List<LatencyComparison> {
        return providers.mapNotNull { (provider, model) ->
            getStats(provider, model)?.let { stats ->
                LatencyComparison(provider, model, stats.avgLatencyMs, stats.p95, stats.successRate)
            }
        }.sortedBy { it.avgLatencyMs }
    }

    data class LatencyComparison(
        val provider: String,
        val model: String,
        val avgLatencyMs: Double,
        val p95LatencyMs: Double,
        val successRate: Double
    )

    /**
     * Ранжирует провайдеров по приоритету (latency + health).
     */
    suspend fun rankByPriority(
        providers: List<Pair<String, String>>,
        latencyWeight: Double = 0.6,
        healthWeight: Double = 0.4
    ): List<RankedProvider> {
        require(latencyWeight + healthWeight == 1.0) { "Weights must sum to 1.0" }

        val maxLatency = providers.mapNotNull { (p, m) ->
            getStats(p, m)?.avgLatencyMs
        }.maxOrNull() ?: 1.0

        return providers.mapNotNull { (provider, model) ->
            val stats = getStats(provider, model) ?: return@mapNotNull null
            val health = getHealthScore(provider, model)?.score ?: 0.5

            // Нормализованный latency score (меньше = лучше)
            val normalizedLatency = 1.0 - (stats.avgLatencyMs / maxLatency).coerceIn(0.0, 1.0)

            val priorityScore = normalizedLatency * latencyWeight + health * healthWeight

            RankedProvider(provider, model, priorityScore, stats.avgLatencyMs, health)
        }.sortedByDescending { it.priorityScore }
    }

    data class RankedProvider(
        val provider: String,
        val model: String,
        val priorityScore: Double,
        val avgLatencyMs: Double,
        val healthScore: Double
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Prediction
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Предсказывает ожидаемую latency для следующего запроса.
     */
    fun predictLatency(provider: String, model: String): Double? {
        val key = "$provider:$model"
        val ewma = ewmaLatency[key] ?: return null
        val stdDev = getStdDev(key) ?: 0.0

        // Предсказание = EWMA + небольшой запас (1 stdDev)
        return ewma + stdDev
    }

    /**
     * Предсказывает, уложится ли запрос в бюджет времени.
     */
    fun willFitInBudget(
        provider: String,
        model: String,
        budgetMs: Long,
        confidence: Double = 0.95
    ): Boolean {
        val predicted = predictLatency(provider, model) ?: return true
        // Для 95% confidence добавляем 2 stdDev
        val stdDev = getStdDev("$provider:$model") ?: 0.0
        val upperBound = predicted + (stdDev * 2 * confidence)
        return upperBound <= budgetMs
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cleanup
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Очищает старые записи (старше указанного возраста в мс).
     */
    suspend fun pruneOlderThan(maxAgeMs: Long) {
        val cutoff = System.currentTimeMillis() - maxAgeMs

        historyMutex.withLock {
            history.forEach { (_, list) ->
                list.removeAll { it.timestamp < cutoff }
            }
            history.entries.removeAll { it.value.isEmpty() }
        }
    }

    /**
     * Полная очистка (для тестов).
     */
    suspend fun clear() {
        historyMutex.withLock {
            history.clear()
        }
        ewmaLatency.clear()
        ewmaTTFB.clear()
        ewmaTokensPerSec.clear()
        successCount.clear()
        failureCount.clear()
        healthScores.clear()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility methods
    // ─────────────────────────────────────────────────────────────────────────

    private fun percentile(sortedValues: List<Double>, p: Double): Double {
        if (sortedValues.isEmpty()) return 0.0
        val index = (p * (sortedValues.size - 1)).toInt()
        return sortedValues[index]
    }

    private fun standardDeviation(values: List<Double>, mean: Double): Double {
        if (values.size < 2) return 0.0
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance)
    }

    private fun getStdDev(key: String): Double? {
        val records = history[key] ?: return null
        if (records.size < 2) return 0.0
        val latencies = records.map { it.latencyMs.toDouble() }
        val mean = latencies.average()
        return standardDeviation(latencies, mean)
    }
}
