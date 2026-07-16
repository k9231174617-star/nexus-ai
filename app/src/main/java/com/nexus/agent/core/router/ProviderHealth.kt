package com.nexus.agent.core.router

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

/**
 * Мониторинг здоровья LLM-провайдеров.
 * Отслеживает latency, success rate, error rate и определяет статус провайдера.
 */
class ProviderHealth(
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    companion object {
        private const val TAG = "ProviderHealth"
        private const val HEALTH_CHECK_INTERVAL_MS = 30000L
        private const val MAX_HISTORY_SIZE = 100
        private const val HEALTHY_THRESHOLD = 0.8
        private const val DEGRADED_THRESHOLD = 0.5
    }

    private val healthData = ConcurrentHashMap<String, ProviderHealthData>()
    private val mutex = Mutex()

    // Job для периодических проверок
    private var healthCheckJob: Job? = null

    data class ProviderHealthData(
        val providerId: String,
        val successCount: AtomicInteger = AtomicInteger(0),
        val failureCount: AtomicInteger = AtomicInteger(0),
        val latencyHistory: MutableList<Long> = mutableListOf(),
        val errorHistory: MutableList<HealthError> = mutableListOf(),
        val lastSuccessTime: AtomicLong = AtomicLong(0),
        val lastFailureTime: AtomicLong = AtomicLong(0),
        val consecutiveFailures: AtomicInteger = AtomicInteger(0),
        val status: AtomicReference<ProviderStatus> = AtomicReference(ProviderStatus.UNKNOWN)
    )

    data class HealthError(
        val timestamp: Long,
        val message: String,
        val exceptionType: String?,
        val isRecoverable: Boolean
    )

    enum class ProviderStatus {
        HEALTHY,      // > 80% success rate
        DEGRADED,     // 50-80% success rate
        UNHEALTHY,    // < 50% success rate
        UNKNOWN       // Недостаточно данных
    }

    /**
     * Регистрирует провайдер для мониторинга.
     */
    fun registerProvider(providerId: String, provider: LLMProvider) {
        healthData[providerId] = ProviderHealthData(providerId = providerId)
        Log.i(TAG, "Registered health monitoring for $providerId")
    }

    /**
     * Удаляет провайдер из мониторинга.
     */
    fun unregisterProvider(providerId: String) {
        healthData.remove(providerId)
        Log.i(TAG, "Unregistered health monitoring for $providerId")
    }

    /**
     * Запускает периодические health checks.
     */
    fun startHealthChecks() {
        if (healthCheckJob?.isActive == true) return

        healthCheckJob = coroutineScope.launch {
            while (isActive) {
                delay(HEALTH_CHECK_INTERVAL_MS)
                runHealthChecks()
            }
        }
        Log.i(TAG, "Health checks started")
    }

    /**
     * Останавливает health checks.
     */
    fun stopHealthChecks() {
        healthCheckJob?.cancel()
        healthCheckJob = null
        Log.i(TAG, "Health checks stopped")
    }

    /**
     * Выполняет health check для всех провайдеров.
     */
    private suspend fun runHealthChecks() {
        healthData.values.forEach { data ->
            val provider = getProviderReference(data.providerId) ?: return@forEach
            
            val startTime = System.currentTimeMillis()
            val status = try {
                val health = provider.healthCheck()
                val latency = System.currentTimeMillis() - startTime
                
                recordLatency(data.providerId, latency)
                
                if (health.isHealthy) {
                    data.successCount.incrementAndGet()
                    data.consecutiveFailures.set(0)
                    data.lastSuccessTime.set(System.currentTimeMillis())
                    ProviderStatus.HEALTHY
                } else {
                    recordFailure(data.providerId, Exception(health.errorMessage ?: "Unhealthy"))
                    ProviderStatus.UNHEALTHY
                }
            } catch (e: Exception) {
                recordFailure(data.providerId, e)
                ProviderStatus.UNHEALTHY
            }

            data.status.set(status)
        }
    }

    /**
     * Записывает успешный запрос.
     */
    fun recordSuccess(providerId: String) {
        val data = healthData[providerId] ?: return
        
        data.successCount.incrementAndGet()
        data.consecutiveFailures.set(0)
        data.lastSuccessTime.set(System.currentTimeMillis())
        
        updateStatus(data)
    }

    /**
     * Записывает failed запрос.
     */
    fun recordFailure(providerId: String, error: Exception) {
        val data = healthData[providerId] ?: return
        
        data.failureCount.incrementAndGet()
        data.consecutiveFailures.incrementAndGet()
        data.lastFailureTime.set(System.currentTimeMillis())
        
        data.errorHistory.add(HealthError(
            timestamp = System.currentTimeMillis(),
            message = error.message ?: "Unknown error",
            exceptionType = error.javaClass.simpleName,
            isRecoverable = isRecoverableError(error)
        ))
        
        // Ограничиваем размер истории
        if (data.errorHistory.size > MAX_HISTORY_SIZE) {
            data.errorHistory.removeAt(0)
        }
        
        updateStatus(data)
    }

    /**
     * Записывает latency.
     */
    fun recordLatency(providerId: String, latencyMs: Long) {
        val data = healthData[providerId] ?: return
        
        data.latencyHistory.add(latencyMs)
        if (data.latencyHistory.size > MAX_HISTORY_SIZE) {
            data.latencyHistory.removeAt(0)
        }
    }

    /**
     * Проверяет, здоров ли провайдер.
     */
    fun isHealthy(providerId: String): Boolean {
        return getStatus(providerId) != ProviderStatus.UNHEALTHY
    }

    /**
     * Возвращает статус провайдера.
     */
    fun getStatus(providerId: String): ProviderStatus {
        return healthData[providerId]?.status?.get() ?: ProviderStatus.UNKNOWN
    }

    /**
     * Возвращает health score (0.0 - 1.0).
     */
    fun getHealthScore(providerId: String): Double {
        val data = healthData[providerId] ?: return 0.0
        val total = data.successCount.get() + data.failureCount.get()
        if (total == 0) return 1.0 // По умолчанию считаем здоровым
        
        val successRate = data.successCount.get().toDouble() / total
        
        // Учитываем consecutive failures
        val failurePenalty = min(data.consecutiveFailures.get() * 0.1, 0.5)
        
        // Учитываем время с последнего success
        val timeSinceSuccess = System.currentTimeMillis() - data.lastSuccessTime.get()
        val timePenalty = if (timeSinceSuccess > 300000) 0.2 else 0.0 // 5 минут без success
        
        return max(0.0, successRate - failurePenalty - timePenalty)
    }

    /**
     * Возвращает success rate.
     */
    fun getSuccessRate(providerId: String): Double {
        val data = healthData[providerId] ?: return 0.0
        val total = data.successCount.get() + data.failureCount.get()
        if (total == 0) return 1.0
        return data.successCount.get().toDouble() / total
    }

    /**
     * Возвращает среднюю latency.
     */
    fun getAverageLatency(providerId: String): Long {
        val history = healthData[providerId]?.latencyHistory ?: return 0
        if (history.isEmpty()) return 0
        return history.average().toLong()
    }

    /**
     * Возвращает P95 latency.
     */
    fun getP95Latency(providerId: String): Long {
        val history = healthData[providerId]?.latencyHistory ?: return 0
        if (history.isEmpty()) return 0
        val sorted = history.sorted()
        val index = (sorted.size * 0.95).toInt()
        return sorted[min(index, sorted.size - 1)]
    }

    /**
     * Возвращает количество consecutive failures.
     */
    fun getConsecutiveFailures(providerId: String): Int {
        return healthData[providerId]?.consecutiveFailures?.get() ?: 0
    }

    /**
     * Возвращает последние ошибки.
     */
    fun getRecentErrors(providerId: String, count: Int = 10): List<HealthError> {
        return healthData[providerId]?.errorHistory?.takeLast(count) ?: emptyList()
    }

    /**
     * Возвращает полный health report.
     */
    fun getHealthReport(providerId: String): HealthReport? {
        val data = healthData[providerId] ?: return null
        
        val total = data.successCount.get() + data.failureCount.get()
        
        return HealthReport(
            providerId = providerId,
            status = data.status.get(),
            healthScore = getHealthScore(providerId),
            successRate = if (total > 0) data.successCount.get().toDouble() / total else 1.0,
            totalRequests = total,
            successCount = data.successCount.get(),
            failureCount = data.failureCount.get(),
            averageLatencyMs = getAverageLatency(providerId),
            p95LatencyMs = getP95Latency(providerId),
            consecutiveFailures = data.consecutiveFailures.get(),
            lastSuccessTime = data.lastSuccessTime.get(),
            lastFailureTime = data.lastFailureTime.get(),
            recentErrors = getRecentErrors(providerId, 5)
        )
    }

    /**
     * Возвращает health report для всех провайдеров.
     */
    fun getAllHealthReports(): List<HealthReport> {
        return healthData.keys.mapNotNull { getHealthReport(it) }
    }

    /**
     * Обновляет статус провайдера на основе метрик.
     */
    private fun updateStatus(data: ProviderHealthData) {
        val score = getHealthScore(data.providerId)
        
        val newStatus = when {
            score >= HEALTHY_THRESHOLD -> ProviderStatus.HEALTHY
            score >= DEGRADED_THRESHOLD -> ProviderStatus.DEGRADED
            else -> ProviderStatus.UNHEALTHY
        }
        
        val oldStatus = data.status.getAndSet(newStatus)
        if (oldStatus != newStatus) {
            Log.i(TAG, "Provider ${data.providerId} status changed: $oldStatus -> $newStatus (score=${String.format("%.2f", score)})")
        }
    }

    /**
     * Определяет, является ли ошибка recoverable.
     */
    private fun isRecoverableError(error: Exception): Boolean {
        val message = error.message?.lowercase() ?: ""
        return when {
            message.contains("timeout") -> true
            message.contains("rate limit") -> true
            message.contains("too many requests") -> true
            message.contains("connection") -> true
            message.contains("network") -> true
            message.contains("unavailable") -> true
            message.contains("invalid api key") -> false
            message.contains("unauthorized") -> false
            message.contains("forbidden") -> false
            else -> true
        }
    }

    /**
     * Сбрасывает статистику провайдера.
     */
    suspend fun resetProviderStats(providerId: String) = mutex.withLock {
        healthData[providerId]?.let { data ->
            data.successCount.set(0)
            data.failureCount.set(0)
            data.latencyHistory.clear()
            data.errorHistory.clear()
            data.consecutiveFailures.set(0)
            data.status.set(ProviderStatus.UNKNOWN)
            Log.i(TAG, "Reset stats for $providerId")
        }
    }

    private fun getProviderReference(providerId: String): LLMProvider? {
        // В реальной реализации — ссылка на провайдер
        return null
    }

    data class HealthReport(
        val providerId: String,
        val status: ProviderStatus,
        val healthScore: Double,
        val successRate: Double,
        val totalRequests: Int,
        val successCount: Int,
        val failureCount: Int,
        val averageLatencyMs: Long,
        val p95LatencyMs: Long,
        val consecutiveFailures: Int,
        val lastSuccessTime: Long,
        val lastFailureTime: Long,
        val recentErrors: List<HealthError>
    )
}
