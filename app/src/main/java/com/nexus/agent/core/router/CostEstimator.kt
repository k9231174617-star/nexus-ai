package com.nexus.agent.core.router

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * CostEstimator — оценивает стоимость запросов к различным LLM-провайдерам,
 * отслеживает расходы и бюджеты.
 */
class CostEstimator {

    // ─────────────────────────────────────────────────────────────────────────
    // Data classes
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Модель ценообразования провайдера (цена за 1M токенов).
     */
    data class Pricing(
        val provider: String,
        val model: String,
        val inputPricePer1M: Double,   // $ за 1M input токенов
        val outputPricePer1M: Double,  // $ за 1M output токенов
        val cachedInputPricePer1M: Double? = null  // цена кэшированных токенов (если есть)
    )

    /**
     * Запись о реальном расходе.
     */
    data class CostRecord(
        val provider: String,
        val model: String,
        val inputTokens: Int,
        val outputTokens: Int,
        val cachedTokens: Int = 0,
        val estimatedCost: Double,
        val actualCost: Double? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Бюджет для пользователя/организации.
     */
    data class Budget(
        val id: String,
        val totalLimit: Double,       // общий лимит в $
        val period: BudgetPeriod,
        val spent: Double = 0.0,
        val remaining: Double = totalLimit,
        val resetTimestamp: Long = System.currentTimeMillis()
    )

    enum class BudgetPeriod {
        DAILY, WEEKLY, MONTHLY
    }

    /**
     * Результат оценки стоимости.
     */
    data class CostEstimate(
        val provider: String,
        val model: String,
        val estimatedInputCost: Double,
        val estimatedOutputCost: Double,
        val estimatedTotalCost: Double,
        val confidence: Double  // 0.0-1.0 — уверенность в оценке
    )

    /**
     * Статистика по расходам.
     */
    data class CostStats(
        val totalSpent: Double,
        val totalRequests: Int,
        val avgCostPerRequest: Double,
        val maxCost: Double,
        val minCost: Double,
        val byProvider: Map<String, Double>,
        val byModel: Map<String, Double>
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Internal state
    // ─────────────────────────────────────────────────────────────────────────

    private val pricingMap = ConcurrentHashMap<String, Pricing>()  // key: "provider:model"
    private val costHistory = mutableListOf<CostRecord>()
    private val costHistoryMutex = Mutex()
    private val budgets = ConcurrentHashMap<String, Budget>()
    private val providerSpend = ConcurrentHashMap<String, Double>()
    private val modelSpend = ConcurrentHashMap<String, Double>()

    // Эвристики для оценки output tokens
    private val outputTokenRatios = ConcurrentHashMap<String, Double>()  // key: "provider:model"

    // ─────────────────────────────────────────────────────────────────────────
    // Pricing management
    // ─────────────────────────────────────────────────────────────────────────

    fun registerPricing(pricing: Pricing) {
        val key = "${pricing.provider}:${pricing.model}"
        pricingMap[key] = pricing
    }

    fun getPricing(provider: String, model: String): Pricing? {
        return pricingMap["$provider:$model"]
    }

    fun removePricing(provider: String, model: String) {
        pricingMap.remove("$provider:$model")
    }

    fun getAllPricings(): List<Pricing> = pricingMap.values.toList()

    // ─────────────────────────────────────────────────────────────────────────
    // Cost estimation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Оценивает стоимость запроса до его выполнения.
     *
     * @param provider Идентификатор провайдера
     * @param model Идентификатор модели
     * @param inputTokens Ожидаемое количество input токенов
     * @param expectedOutputTokens Ожидаемое количество output токенов (null — оценим по эвристике)
     */
    fun estimateCost(
        provider: String,
        model: String,
        inputTokens: Int,
        expectedOutputTokens: Int? = null
    ): CostEstimate {
        val pricing = getPricing(provider, model)
            ?: return CostEstimate(
                provider = provider,
                model = model,
                estimatedInputCost = 0.0,
                estimatedOutputCost = 0.0,
                estimatedTotalCost = 0.0,
                confidence = 0.0
            )

        val estimatedOutput = expectedOutputTokens ?: estimateOutputTokens(provider, model, inputTokens)

        val inputCost = (inputTokens / 1_000_000.0) * pricing.inputPricePer1M
        val outputCost = (estimatedOutput / 1_000_000.0) * pricing.outputPricePer1M

        // Уверенность зависит от наличия исторических данных
        val hasHistory = outputTokenRatios.containsKey("$provider:$model")
        val confidence = if (hasHistory) 0.85 else 0.6

        return CostEstimate(
            provider = provider,
            model = model,
            estimatedInputCost = inputCost,
            estimatedOutputCost = outputCost,
            estimatedTotalCost = inputCost + outputCost,
            confidence = confidence
        )
    }

    /**
     * Оценивает стоимость для нескольких провайдеров (для сравнения).
     */
    fun estimateForProviders(
        providers: List<Pair<String, String>>,
        inputTokens: Int,
        expectedOutputTokens: Int? = null
    ): List<CostEstimate> {
        return providers.map { (provider, model) ->
            estimateCost(provider, model, inputTokens, expectedOutputTokens)
        }.sortedBy { it.estimatedTotalCost }
    }

    /**
     * Эвристическая оценка output токенов на основе истории.
     */
    private fun estimateOutputTokens(provider: String, model: String, inputTokens: Int): Int {
        val key = "$provider:$model"
        val ratio = outputTokenRatios[key] ?: 0.5  // По умолчанию 50% от input

        return (inputTokens * ratio).toInt().coerceIn(1, 8192)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cost recording & tracking
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Записывает фактический расход после выполнения запроса.
     */
    suspend fun recordCost(record: CostRecord) {
        costHistoryMutex.withLock {
            costHistory.add(record)
            // Храним только последние 10000 записей
            if (costHistory.size > 10000) {
                costHistory.removeAt(0)
            }
        }

        // Обновляем агрегированные данные
        val providerKey = record.provider
        val modelKey = "${record.provider}:${record.model}"

        providerSpend.merge(providerKey, record.actualCost ?: record.estimatedCost) { old, new -> old + new }
        modelSpend.merge(modelKey, record.actualCost ?: record.estimatedCost) { old, new -> old + new }

        // Обновляем эвристику ratio output/input
        if (record.inputTokens > 0 && record.outputTokens > 0) {
            val newRatio = record.outputTokens.toDouble() / record.inputTokens
            val key = modelKey
            outputTokenRatios.merge(key, newRatio) { old, _ -> old * 0.7 + newRatio * 0.3 }
        }
    }

    /**
     * Записывает расход с автоматическим расчётом на основе токенов.
     */
    suspend fun recordUsage(
        provider: String,
        model: String,
        inputTokens: Int,
        outputTokens: Int,
        cachedTokens: Int = 0
    ) {
        val pricing = getPricing(provider, model)
        val actualCost = pricing?.let {
            val inputCost = ((inputTokens - cachedTokens) / 1_000_000.0) * it.inputPricePer1M
            val cachedCost = (cachedTokens / 1_000_000.0) * (it.cachedInputPricePer1M ?: it.inputPricePer1M)
            val outputCost = (outputTokens / 1_000_000.0) * it.outputPricePer1M
            inputCost + cachedCost + outputCost
        }

        recordCost(
            CostRecord(
                provider = provider,
                model = model,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                cachedTokens = cachedTokens,
                estimatedCost = estimateCost(provider, model, inputTokens, outputTokens).estimatedTotalCost,
                actualCost = actualCost
            )
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Budget management
    // ─────────────────────────────────────────────────────────────────────────

    fun setBudget(budget: Budget) {
        budgets[budget.id] = budget
    }

    fun getBudget(id: String): Budget? = budgets[id]

    /**
     * Проверяет, не превышен ли бюджет.
     */
    fun isBudgetExceeded(id: String): Boolean {
        val budget = budgets[id] ?: return false
        return budget.spent >= budget.totalLimit
    }

    /**
     * Проверяет, достаточно ли бюджета для запроса.
     */
    fun hasBudgetFor(id: String, estimatedCost: Double): Boolean {
        val budget = budgets[id] ?: return true  // Без бюджета — всё разрешено
        return (budget.remaining - estimatedCost) >= 0
    }

    /**
     * Расходует бюджет.
     */
    fun spendBudget(id: String, amount: Double): Boolean {
        val budget = budgets[id] ?: return true

        val newSpent = budget.spent + amount
        if (newSpent > budget.totalLimit) {
            return false
        }

        budgets[id] = budget.copy(
            spent = newSpent,
            remaining = budget.totalLimit - newSpent
        )
        return true
    }

    /**
     * Сбрасывает бюджеты по расписанию.
     */
    fun resetExpiredBudgets() {
        val now = System.currentTimeMillis()
        budgets.replaceAll { id, budget ->
            val shouldReset = when (budget.period) {
                BudgetPeriod.DAILY -> now - budget.resetTimestamp >= 86400000
                BudgetPeriod.WEEKLY -> now - budget.resetTimestamp >= 604800000
                BudgetPeriod.MONTHLY -> now - budget.resetTimestamp >= 2592000000
            }
            if (shouldReset) {
                budget.copy(spent = 0.0, remaining = budget.totalLimit, resetTimestamp = now)
            } else {
                budget
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Statistics & analytics
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Возвращает агрегированную статистику по расходам.
     */
    suspend fun getStats(since: Long = 0): CostStats {
        val records = costHistoryMutex.withLock {
            costHistory.filter { it.timestamp >= since }
        }

        if (records.isEmpty()) {
            return CostStats(0.0, 0, 0.0, 0.0, 0.0, emptyMap(), emptyMap())
        }

        val costs = records.map { it.actualCost ?: it.estimatedCost }
        val byProvider = records.groupBy { it.provider }
            .mapValues { (_, list) -> list.sumOf { it.actualCost ?: it.estimatedCost } }
        val byModel = records.groupBy { "${it.provider}:${it.model}" }
            .mapValues { (_, list) -> list.sumOf { it.actualCost ?: it.estimatedCost } }

        return CostStats(
            totalSpent = costs.sum(),
            totalRequests = records.size,
            avgCostPerRequest = costs.average(),
            maxCost = costs.maxOrNull() ?: 0.0,
            minCost = costs.minOrNull() ?: 0.0,
            byProvider = byProvider,
            byModel = byModel
        )
    }

    /**
     * Возвращает расходы по провайдеру.
     */
    fun getProviderSpend(provider: String): Double = providerSpend[provider] ?: 0.0

    /**
     * Возвращает расходы по модели.
     */
    fun getModelSpend(provider: String, model: String): Double =
        modelSpend["$provider:$model"] ?: 0.0

    /**
     * Прогноз расходов до конца периода.
     */
    fun forecastSpend(budgetId: String): Double? {
        val budget = budgets[budgetId] ?: return null
        val elapsed = System.currentTimeMillis() - budget.resetTimestamp
        val totalPeriod = when (budget.period) {
            BudgetPeriod.DAILY -> 86400000.0
            BudgetPeriod.WEEKLY -> 604800000.0
            BudgetPeriod.MONTHLY -> 2592000000.0
        }

        if (elapsed <= 0 || budget.spent <= 0) return budget.totalLimit

        val rate = budget.spent / elapsed  // $/ms
        return rate * totalPeriod
    }

    /**
     * Очищает всю историю (для тестов).
     */
    suspend fun clear() {
        costHistoryMutex.withLock {
            costHistory.clear()
        }
        providerSpend.clear()
        modelSpend.clear()
        outputTokenRatios.clear()
        budgets.clear()
    }
}
