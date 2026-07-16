package com.nexus.agent.core.router

import kotlinx.coroutines.flow.Flow

/**
 * Абстрактный интерфейс LLM-провайдера.
 * Все провайдеры (OpenAI, Anthropic, local models и т.д.) реализуют этот интерфейс.
 */
interface LLMProvider {
    
    /**
     * Уникальный идентификатор провайдера.
     */
    fun getProviderId(): String

    /**
     * Название провайдера для отображения.
     */
    fun getProviderName(): String

    /**
     * Выполняет синхронный запрос к LLM.
     */
    suspend fun complete(
        prompt: String,
        systemPrompt: String? = null,
        modelId: String,
        maxTokens: Int = 2048,
        temperature: Float = 0.7f,
        topP: Float = 1.0f,
        frequencyPenalty: Float = 0.0f,
        presencePenalty: Float = 0.0f,
        stopSequences: List<String> = emptyList()
    ): LLMResponse

    /**
     * Выполняет потоковый (streaming) запрос к LLM.
     */
    suspend fun completeStream(
        prompt: String,
        systemPrompt: String? = null,
        modelId: String,
        maxTokens: Int = 2048,
        temperature: Float = 0.7f,
        topP: Float = 1.0f,
        onChunk: (String) -> Unit
    ): LLMResponse

    /**
     * Возвращает список доступных моделей.
     */
    fun getAvailableModels(): List<ModelInfo>

    /**
     * Возвращает информацию о конкретной модели.
     */
    fun getModelInfo(modelId: String): ModelInfo?

    /**
     * Возвращает модель по умолчанию.
     */
    fun getDefaultModel(): String

    /**
     * Проверяет доступность провайдера.
     */
    suspend fun healthCheck(): HealthStatus

    /**
     * Возвращает ограничения rate limit.
     */
    fun getRateLimit(): RateLimit

    /**
     * Возвращает стоимость за 1K токенов для модели.
     */
    fun getPricing(modelId: String): Pricing

    /**
     * Подсчитывает токены в тексте (если поддерживается).
     */
    fun countTokens(text: String, modelId: String): Int

    /**
     * Валидирует API ключ или credentials.
     */
    suspend fun validateCredentials(): Boolean

    /**
     * Обновляет конфигурацию провайдера.
     */
    fun updateConfig(config: ProviderConfig)

    /**
     * Возвращает текущую конфигурацию.
     */
    fun getConfig(): ProviderConfig

    /**
     * Освобождает ресурсы.
     */
    fun shutdown()
}

/**
 * Информация о модели.
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val contextWindow: Int,
    val maxOutputTokens: Int,
    val supportsVision: Boolean = false,
    val supportsFunctionCalling: Boolean = false,
    val supportsJsonMode: Boolean = false,
    val qualityScore: Double = 1.0, // 0.0 - 1.0
    val speedScore: Double = 1.0,
    val costScore: Double = 1.0
)

/**
 * Ответ от LLM.
 */
data class LLMResponse(
    val text: String,
    val modelId: String,
    val providerId: String,
    val tokensUsed: Int,
    val promptTokens: Int,
    val completionTokens: Int,
    val finishReason: String,
    val latencyMs: Long,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Статус здоровья провайдера.
 */
data class HealthStatus(
    val isHealthy: Boolean,
    val latencyMs: Long,
    val lastChecked: Long,
    val errorMessage: String? = null
)

/**
 * Rate limit информация.
 */
data class RateLimit(
    val requestsPerMinute: Int,
    val requestsPerHour: Int,
    val tokensPerMinute: Int,
    val tokensPerDay: Int
)

/**
 * Ценообразование.
 */
data class Pricing(
    val inputPricePer1K: Double,    // USD
    val outputPricePer1K: Double,   // USD
    val currency: String = "USD"
)

/**
 * Конфигурация провайдера.
 */
data class ProviderConfig(
    val apiKey: String? = null,
    val baseUrl: String? = null,
    val timeoutMs: Long = 60000,
    val maxRetries: Int = 3,
    val retryDelayMs: Long = 1000,
    val customHeaders: Map<String, String> = emptyMap(),
    val proxyUrl: String? = null,
    val organizationId: String? = null,
    val extraParams: Map<String, Any> = emptyMap()
)
