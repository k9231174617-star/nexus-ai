package com.nexus.agent.core.router

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

/**
 * Центральный маршрутизатор запросов к LLM-провайдерам.
 * Реализует intelligent routing, load balancing, circuit breaker и failover.
 */
class ModelRouter(
    private val preferences: RoutePreferences,
    private val healthMonitor: ProviderHealth,
    private val costEstimator: CostEstimator,
    private val latencyTracker: LatencyTracker,
    private val fallbackChain: FallbackChain
) {
    companion object {
        private const val TAG = "ModelRouter"
        private const val DEFAULT_TIMEOUT_MS = 60000L
        private const val CIRCUIT_BREAKER_THRESHOLD = 5
        private const val CIRCUIT_BREAKER_RESET_MS = 30000L
        private const val RATE_LIMIT_WINDOW_MS = 60000L
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val providers = ConcurrentHashMap<String, LLMProvider>()
    private val providerMutex = Mutex()
    
    // Circuit breaker state
    private val failureCounters = ConcurrentHashMap<String, AtomicInteger>()
    private val circuitOpen = ConcurrentHashMap<String, AtomicBoolean>()
    private val lastFailureTime = ConcurrentHashMap<String, Long>()
    
    // Rate limiting
    private val requestTimestamps = ConcurrentHashMap<String, PriorityBlockingQueue<Long>>()
    
    // Metrics
    private val totalRequests = AtomicInteger(0)
    private val successfulRequests = AtomicInteger(0)
    private val failedRequests = AtomicInteger(0)
    private val fallbackInvocations = AtomicInteger(0)

    // Routing strategy
    private var currentStrategy: RoutingStrategy = RoutingStrategy.WEIGHTED_LATENCY

    enum class RoutingStrategy {
        ROUND_ROBIN,
        WEIGHTED_LATENCY,
        COST_OPTIMIZED,
        QUALITY_FIRST,
        FALLBACK_ONLY
    }

    data class RoutingDecision(
        val providerId: String,
        val modelId: String,
        val estimatedCost: Double,
        val estimatedLatencyMs: Long,
        val confidence: Double,
        val reason: String
    )

    /**
     * Регистрирует нового LLM-провайдера в роутере.
     */
    suspend fun registerProvider(provider: LLMProvider): Boolean = providerMutex.withLock {
        val providerId = provider.getProviderId()
        
        if (providers.containsKey(providerId)) {
            Log.w(TAG, "Provider $providerId already registered")
            return false
        }

        providers[providerId] = provider
        failureCounters[providerId] = AtomicInteger(0)
        circuitOpen[providerId] = AtomicBoolean(false)
        requestTimestamps[providerId] = PriorityBlockingQueue()
        
        // Инициализируем health check
        healthMonitor.registerProvider(providerId, provider)
        
        Log.i(TAG, "Registered provider: $providerId (${provider.getAvailableModels().size} models)")
        true
    }

    /**
     * Удаляет провайдера из роутера.
     */
    suspend fun unregisterProvider(providerId: String): Boolean = providerMutex.withLock {
        providers.remove(providerId)?.let {
            healthMonitor.unregisterProvider(providerId)
            failureCounters.remove(providerId)
            circuitOpen.remove(providerId)
            requestTimestamps.remove(providerId)
            Log.i(TAG, "Unregistered provider: $providerId")
            true
        } ?: false
    }

    /**
     * Основной метод маршрутизации запроса.
     * Выбирает оптимального провайдера и выполняет запрос.
     */
    suspend fun route(
        prompt: String,
        systemPrompt: String? = null,
        preferredModel: String? = null,
        maxTokens: Int = 2048,
        temperature: Float = 0.7f,
        routingStrategy: RoutingStrategy? = null
    ): LLMResponse {
        totalRequests.incrementAndGet()
        val strategy = routingStrategy ?: currentStrategy

        val decision = selectProvider(
            prompt = prompt,
            preferredModel = preferredModel,
            strategy = strategy
        )

        Log.d(TAG, "Routing decision: ${decision.providerId}/${decision.modelId} " +
                "(strategy=$strategy, confidence=${decision.confidence})")

        val provider = providers[decision.providerId]
            ?: throw RouterException("Selected provider ${decision.providerId} not found")

        return try {
            val startTime = System.currentTimeMillis()
            
            // Проверяем rate limit
            checkRateLimit(decision.providerId)

            // Выполняем запрос
            val response = withTimeout(DEFAULT_TIMEOUT_MS) {
                provider.complete(
                    prompt = prompt,
                    systemPrompt = systemPrompt,
                    modelId = decision.modelId,
                    maxTokens = maxTokens,
                    temperature = temperature
                )
            }

            // Обновляем метрики
            val latency = System.currentTimeMillis() - startTime
            latencyTracker.recordLatency(decision.providerId, decision.modelId, latency)
            costEstimator.recordUsage(decision.providerId, decision.modelId, response.tokensUsed)
            healthMonitor.recordSuccess(decision.providerId)
            
            // Сбрасываем circuit breaker при успехе
            resetCircuitBreaker(decision.providerId)
            
            successfulRequests.incrementAndGet()
            recordRequestTimestamp(decision.providerId)

            response.copy(
                metadata = response.metadata + mapOf(
                    "routed_via" to decision.providerId,
                    "routing_strategy" to strategy.name,
                    "estimated_cost" to decision.estimatedCost.toString(),
                    "actual_latency_ms" to latency.toString()
                )
            )

        } catch (e: Exception) {
            failedRequests.incrementAndGet()
            handleFailure(decision.providerId, e)
            
            // Fallback к следующему провайдеру в цепочке
            fallbackInvocations.incrementAndGet()
            executeFallback(
                prompt = prompt,
                systemPrompt = systemPrompt,
                preferredModel = preferredModel,
                maxTokens = maxTokens,
                temperature = temperature,
                failedProvider = decision.providerId,
                originalError = e
            )
        }
    }

    /**
     * Потоковая маршрутизация (streaming).
     */
    suspend fun routeStream(
        prompt: String,
        systemPrompt: String? = null,
        preferredModel: String? = null,
        maxTokens: Int = 2048,
        temperature: Float = 0.7f,
        onChunk: (String) -> Unit
    ): LLMResponse {
        val decision = selectProvider(prompt, preferredModel, currentStrategy)
        val provider = providers[decision.providerId]
            ?: throw RouterException("Provider not found")

        return try {
            checkRateLimit(decision.providerId)
            
            val startTime = System.currentTimeMillis()
            val response = provider.completeStream(
                prompt = prompt,
                systemPrompt = systemPrompt,
                modelId = decision.modelId,
                maxTokens = maxTokens,
                temperature = temperature,
                onChunk = onChunk
            )

            latencyTracker.recordLatency(decision.providerId, decision.modelId, 
                System.currentTimeMillis() - startTime)
            resetCircuitBreaker(decision.providerId)
            successfulRequests.incrementAndGet()

            response

        } catch (e: Exception) {
            handleFailure(decision.providerId, e)
            fallbackInvocations.incrementAndGet()
            
            // Для streaming fallback — пробуем следующего провайдера
            val fallbackProvider = fallbackChain.getNextProvider(decision.providerId)
                ?: throw RouterException("No fallback available", e)

            providers[fallbackProvider]?.completeStream(
                prompt = prompt,
                systemPrompt = systemPrompt,
                modelId = preferredModel ?: getDefaultModel(fallbackProvider),
                maxTokens = maxTokens,
                temperature = temperature,
                onChunk = onChunk
            ) ?: throw RouterException("Fallback provider $fallbackProvider not available", e)
        }
    }

    /**
     * Выбирает оптимального провайдера на основе стратегии.
     */
    private fun selectProvider(
        prompt: String,
        preferredModel: String?,
        strategy: RoutingStrategy
    ): RoutingDecision {
        val availableProviders = getHealthyProviders()
        
        if (availableProviders.isEmpty()) {
            throw RouterException("No healthy providers available")
        }

        return when (strategy) {
            RoutingStrategy.ROUND_ROBIN -> selectRoundRobin(availableProviders, preferredModel)
            RoutingStrategy.WEIGHTED_LATENCY -> selectWeightedLatency(availableProviders, prompt, preferredModel)
            RoutingStrategy.COST_OPTIMIZED -> selectCostOptimized(availableProviders, prompt, preferredModel)
            RoutingStrategy.QUALITY_FIRST -> selectQualityFirst(availableProviders, preferredModel)
            RoutingStrategy.FALLBACK_ONLY -> selectFallbackOnly(preferredModel)
        }
    }

    /**
     * Round-robin выбор.
     */
    private val roundRobinCounter = AtomicInteger(0)
    
    private fun selectRoundRobin(
        candidates: List<String>,
        preferredModel: String?
    ): RoutingDecision {
        val index = roundRobinCounter.getAndIncrement() % candidates.size
        val providerId = candidates[index]
        val modelId = preferredModel ?: getDefaultModel(providerId)
        val provider = providers[providerId]!!

        return RoutingDecision(
            providerId = providerId,
            modelId = modelId,
            estimatedCost = costEstimator.estimateCost(providerId, modelId, 1000),
            estimatedLatencyMs = latencyTracker.getEstimatedLatency(providerId, modelId),
            confidence = 0.5,
            reason = "Round-robin selection"
        )
    }

    /**
     * Weighted latency-based выбор.
     */
    private fun selectWeightedLatency(
        candidates: List<String>,
        prompt: String,
        preferredModel: String?
    ): RoutingDecision {
        val promptTokens = estimateTokens(prompt)
        var bestDecision: RoutingDecision? = null
        var bestScore = Double.MAX_VALUE

        candidates.forEach { providerId ->
            val provider = providers[providerId]!!
            val models = if (preferredModel != null) {
                listOfNotNull(provider.getModelInfo(preferredModel))
            } else {
                provider.getAvailableModels()
            }

            models.forEach { model ->
                val latency = latencyTracker.getEstimatedLatency(providerId, model.id)
                val cost = costEstimator.estimateCost(providerId, model.id, promptTokens)
                val health = healthMonitor.getHealthScore(providerId)
                val successRate = healthMonitor.getSuccessRate(providerId)

                // Score = latency * cost_weight * (1/health)
                val score = latency * (1 + cost * preferences.costWeight) * 
                        (1 + (1 - health) * preferences.reliabilityWeight) *
                        (1 + (1 - successRate) * 2)

                if (score < bestScore) {
                    bestScore = score
                    bestDecision = RoutingDecision(
                        providerId = providerId,
                        modelId = model.id,
                        estimatedCost = cost,
                        estimatedLatencyMs = latency,
                        confidence = health * successRate,
                        reason = "Lowest weighted score: ${String.format("%.2f", score)}"
                    )
                }
            }
        }

        return bestDecision ?: throw RouterException("No suitable provider found")
    }

    /**
     * Cost-optimized выбор.
     */
    private fun selectCostOptimized(
        candidates: List<String>,
        prompt: String,
        preferredModel: String?
    ): RoutingDecision {
        val promptTokens = estimateTokens(prompt)
        var bestDecision: RoutingDecision? = null
        var lowestCost = Double.MAX_VALUE

        candidates.forEach { providerId ->
            val provider = providers[providerId]!!
            val models = if (preferredModel != null) {
                listOfNotNull(provider.getModelInfo(preferredModel))
            } else {
                provider.getAvailableModels()
            }

            models.forEach { model ->
                val cost = costEstimator.estimateCost(providerId, model.id, promptTokens)
                val latency = latencyTracker.getEstimatedLatency(providerId, model.id)
                val health = healthMonitor.getHealthScore(providerId)

                // Проверяем что latency приемлемая
                if (latency <= preferences.maxAcceptableLatencyMs && health >= 0.5) {
                    if (cost < lowestCost) {
                        lowestCost = cost
                        bestDecision = RoutingDecision(
                            providerId = providerId,
                            modelId = model.id,
                            estimatedCost = cost,
                            estimatedLatencyMs = latency,
                            confidence = health,
                            reason = "Lowest cost: $${String.format("%.4f", cost)}"
                        )
                    }
                }
            }
        }

        return bestDecision ?: selectWeightedLatency(candidates, prompt, preferredModel)
    }

    /**
     * Quality-first выбор (лучшая модель).
     */
    private fun selectQualityFirst(
        candidates: List<String>,
        preferredModel: String?
    ): RoutingDecision {
        var bestDecision: RoutingDecision? = null
        var bestQualityScore = 0.0

        candidates.forEach { providerId ->
            val provider = providers[providerId]!!
            val models = if (preferredModel != null) {
                listOfNotNull(provider.getModelInfo(preferredModel))
            } else {
                provider.getAvailableModels().sortedByDescending { it.qualityScore }
            }

            models.firstOrNull()?.let { model ->
                val qualityScore = model.qualityScore * healthMonitor.getHealthScore(providerId)
                val latency = latencyTracker.getEstimatedLatency(providerId, model.id)

                if (qualityScore > bestQualityScore) {
                    bestQualityScore = qualityScore
                    bestDecision = RoutingDecision(
                        providerId = providerId,
                        modelId = model.id,
                        estimatedCost = costEstimator.estimateCost(providerId, model.id, 1000),
                        estimatedLatencyMs = latency,
                        confidence = qualityScore,
                        reason = "Highest quality score: ${String.format("%.2f", qualityScore)}"
                    )
                }
            }
        }

        return bestDecision ?: throw RouterException("No provider meets quality requirements")
    }

    /**
     * Fallback-only выбор (только по цепочке fallback).
     */
    private fun selectFallbackOnly(preferredModel: String?): RoutingDecision {
        val primary = fallbackChain.getPrimaryProvider()
            ?: throw RouterException("No primary provider in fallback chain")

        return RoutingDecision(
            providerId = primary,
            modelId = preferredModel ?: getDefaultModel(primary),
            estimatedCost = 0.0,
            estimatedLatencyMs = 0,
            confidence = 1.0,
            reason = "Fallback chain primary"
        )
    }

    /**
     * Получает список здоровых провайдеров (circuit breaker закрыт).
     */
    private fun getHealthyProviders(): List<String> {
        return providers.keys.filter { providerId ->
            !isCircuitOpen(providerId) && healthMonitor.isHealthy(providerId)
        }.ifEmpty {
            // Если все circuit open — пробуем сбросить самый старый
            attemptCircuitReset()
        }
    }

    /**
     * Проверяет, открыт ли circuit breaker.
     */
    private fun isCircuitOpen(providerId: String): Boolean {
        val open = circuitOpen[providerId]?.get() ?: return false
        if (!open) return false

        // Проверяем время с последнего failure
        val lastFail = lastFailureTime[providerId] ?: 0
        if (System.currentTimeMillis() - lastFail > CIRCUIT_BREAKER_RESET_MS) {
            // Пробуем half-open
            circuitOpen[providerId]?.set(false)
            failureCounters[providerId]?.set(0)
            return false
        }
        return true
    }

    /**
     * Обрабатывает failure и обновляет circuit breaker.
     */
    private fun handleFailure(providerId: String, error: Exception) {
        Log.w(TAG, "Provider $providerId failed: ${error.message}")

        val counter = failureCounters[providerId] ?: return
        val failures = counter.incrementAndGet()

        if (failures >= CIRCUIT_BREAKER_THRESHOLD) {
            circuitOpen[providerId]?.set(true)
            lastFailureTime[providerId] = System.currentTimeMillis()
            Log.e(TAG, "Circuit breaker OPEN for $providerId ($failures failures)")
        }

        healthMonitor.recordFailure(providerId, error)
    }

    /**
     * Сбрасывает circuit breaker при успехе.
     */
    private fun resetCircuitBreaker(providerId: String) {
        failureCounters[providerId]?.set(0)
    }

    /**
     * Пробует сбросить circuit breaker для провайдера с наибольшим временем.
     */
    private fun attemptCircuitReset(): List<String> {
        val oldestFailure = lastFailureTime.entries
            .filter { System.currentTimeMillis() - (it.value) > CIRCUIT_BREAKER_RESET_MS }
            .minByOrNull { it.value }

        oldestFailure?.let { (providerId, _) ->
            circuitOpen[providerId]?.set(false)
            failureCounters[providerId]?.set(0)
            lastFailureTime.remove(providerId)
            Log.i(TAG, "Circuit breaker half-open for $providerId")
            return listOf(providerId)
        }

        // Все circuit open — возвращаем всех (last resort)
        return providers.keys.toList()
    }

    /**
     * Проверяет rate limit провайдера.
     */
    private fun checkRateLimit(providerId: String) {
        val timestamps = requestTimestamps[providerId] ?: return
        val now = System.currentTimeMillis()
        val windowStart = now - RATE_LIMIT_WINDOW_MS

        // Удаляем старые записи
        while (timestamps.peek()?.let { it < windowStart } == true) {
            timestamps.poll()
        }

        val provider = providers[providerId] ?: return
        val limit = provider.getRateLimit()

        if (timestamps.size >= limit.requestsPerMinute) {
            throw RouterException("Rate limit exceeded for $providerId (${limit.requestsPerMinute}/min)")
        }
    }

    private fun recordRequestTimestamp(providerId: String) {
        requestTimestamps[providerId]?.offer(System.currentTimeMillis())
    }

    /**
     * Fallback execution.
     */
    private suspend fun executeFallback(
        prompt: String,
        systemPrompt: String?,
        preferredModel: String?,
        maxTokens: Int,
        temperature: Float,
        failedProvider: String,
        originalError: Exception
    ): LLMResponse {
        val fallbackId = fallbackChain.getNextProvider(failedProvider)
            ?: throw RouterException("No fallback available after $failedProvider", originalError)

        Log.w(TAG, "Falling back from $failedProvider to $fallbackId")

        val fallbackProvider = providers[fallbackId]
            ?: throw RouterException("Fallback provider $fallbackId not found", originalError)

        return try {
            val response = fallbackProvider.complete(
                prompt = prompt,
                systemPrompt = systemPrompt,
                modelId = preferredModel ?: getDefaultModel(fallbackId),
                maxTokens = maxTokens,
                temperature = temperature
            )

            healthMonitor.recordSuccess(fallbackId)
            response.copy(
                metadata = response.metadata + mapOf(
                    "fallback_from" to failedProvider,
                    "fallback_reason" to (originalError.message ?: "unknown")
                )
            )
        } catch (e: Exception) {
            // Рекурсивный fallback
            executeFallback(
                prompt = prompt,
                systemPrompt = systemPrompt,
                preferredModel = preferredModel,
                maxTokens = maxTokens,
                temperature = temperature,
                failedProvider = fallbackId,
                originalError = e
            )
        }
    }

    /**
     * Возвращает default model для провайдера.
     */
    private fun getDefaultModel(providerId: String): String {
        return providers[providerId]?.getDefaultModel() ?: "default"
    }

    /**
     * Оценивает количество токенов в prompt.
     */
    private fun estimateTokens(text: String): Int {
        // Упрощённая оценка: ~4 chars per token для английского, ~2 для других
        return max(1, text.length / 3)
    }

    /**
     * Устанавливает стратегию маршрутизации.
     */
    fun setRoutingStrategy(strategy: RoutingStrategy) {
        currentStrategy = strategy
        Log.i(TAG, "Routing strategy changed to: $strategy")
    }

    /**
     * Возвращает статистику роутера.
     */
    fun getStats(): RouterStats {
        return RouterStats(
            totalRequests = totalRequests.get(),
            successfulRequests = successfulRequests.get(),
            failedRequests = failedRequests.get(),
            fallbackInvocations = fallbackInvocations.get(),
            activeProviders = providers.size,
            healthyProviders = getHealthyProviders().size,
            circuitOpenCount = circuitOpen.count { it.value.get() },
            averageLatencyMs = latencyTracker.getGlobalAverageLatency(),
            currentStrategy = currentStrategy
        )
    }

    data class RouterStats(
        val totalRequests: Int,
        val successfulRequests: Int,
        val failedRequests: Int,
        val fallbackInvocations: Int,
        val activeProviders: Int,
        val healthyProviders: Int,
        val circuitOpenCount: Int,
        val averageLatencyMs: Long,
        val currentStrategy: RoutingStrategy
    )

    /**
     * Очищает все ресурсы.
     */
    fun shutdown() {
        Log.i(TAG, "Shutting down ModelRouter")
        coroutineScope.cancel()
        providers.values.forEach { it.shutdown() }
        providers.clear()
    }

    class RouterException(message: String, cause: Throwable? = null) : Exception(message, cause)
    }
    