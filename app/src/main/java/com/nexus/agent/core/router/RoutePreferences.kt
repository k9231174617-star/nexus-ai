package com.nexus.agent.core.router

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Управляет пользовательскими предпочтениями маршрутизации.
 * Хранит настройки в SharedPreferences и предоставляет reactive updates.
 */
class RoutePreferences(context: Context) {
    
    companion object {
        private const val TAG = "RoutePreferences"
        private const val PREFS_NAME = "nexus_router_prefs"
        
        // Keys
        private const val KEY_STRATEGY = "routing_strategy"
        private const val KEY_COST_WEIGHT = "cost_weight"
        private const val KEY_LATENCY_WEIGHT = "latency_weight"
        private const val KEY_QUALITY_WEIGHT = "quality_weight"
        private const val KEY_RELIABILITY_WEIGHT = "reliability_weight"
        private const val KEY_MAX_LATENCY_MS = "max_latency_ms"
        private const val KEY_MAX_COST_PER_REQUEST = "max_cost_per_request"
        private const val KEY_PREFERRED_PROVIDER = "preferred_provider"
        private const val KEY_PREFERRED_MODEL = "preferred_model"
        private const val KEY_AUTO_FALLBACK = "auto_fallback"
        private const val KEY_STREAMING_DEFAULT = "streaming_default"
        private const val KEY_CACHE_ENABLED = "cache_enabled"
        private const val KEY_RETRY_COUNT = "retry_count"
        private const val KEY_TIMEOUT_MS = "timeout_ms"
        private const val KEY_BUDGET_DAILY = "budget_daily_usd"
        private const val KEY_BUDGET_MONTHLY = "budget_monthly_usd"
        private const val KEY_ENABLE_OBSERVABILITY = "enable_observability"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Reactive state flows
    private val _strategyFlow = MutableStateFlow(getStrategy())
    val strategyFlow: StateFlow<ModelRouter.RoutingStrategy> = _strategyFlow.asStateFlow()
    
    private val _preferencesFlow = MutableStateFlow(getAllPreferences())
    val preferencesFlow: StateFlow<RoutingPreferences> = _preferencesFlow.asStateFlow()

    /**
     * Стратегия маршрутизации.
     */
    var strategy: ModelRouter.RoutingStrategy
        get() = getStrategy()
        set(value) {
            prefs.edit().putString(KEY_STRATEGY, value.name).apply()
            _strategyFlow.value = value
            notifyPreferencesChanged()
        }

    /**
     * Вес стоимости при маршрутизации (0.0 - 1.0).
     */
    var costWeight: Float
        get() = prefs.getFloat(KEY_COST_WEIGHT, 0.3f)
        set(value) {
            prefs.edit().putFloat(KEY_COST_WEIGHT, value.coerceIn(0f, 1f)).apply()
            notifyPreferencesChanged()
        }

    /**
     * Вес latency при маршрутизации (0.0 - 1.0).
     */
    var latencyWeight: Float
        get() = prefs.getFloat(KEY_LATENCY_WEIGHT, 0.4f)
        set(value) {
            prefs.edit().putFloat(KEY_LATENCY_WEIGHT, value.coerceIn(0f, 1f)).apply()
            notifyPreferencesChanged()
        }

    /**
     * Вес качества при маршрутизации (0.0 - 1.0).
     */
    var qualityWeight: Float
        get() = prefs.getFloat(KEY_QUALITY_WEIGHT, 0.2f)
        set(value) {
            prefs.edit().putFloat(KEY_QUALITY_WEIGHT, value.coerceIn(0f, 1f)).apply()
            notifyPreferencesChanged()
        }

    /**
     * Вес надёжности при маршрутизации (0.0 - 1.0).
     */
    var reliabilityWeight: Float
        get() = prefs.getFloat(KEY_RELIABILITY_WEIGHT, 0.1f)
        set(value) {
            prefs.edit().putFloat(KEY_RELIABILITY_WEIGHT, value.coerceIn(0f, 1f)).apply()
            notifyPreferencesChanged()
        }

    /**
     * Максимально приемлемая latency (мс).
     */
    var maxAcceptableLatencyMs: Long
        get() = prefs.getLong(KEY_MAX_LATENCY_MS, 10000L)
        set(value) {
            prefs.edit().putLong(KEY_MAX_LATENCY_MS, value).apply()
            notifyPreferencesChanged()
        }

    /**
     * Максимальная стоимость за запрос (USD).
     */
    var maxCostPerRequest: Double
        get() = prefs.getString(KEY_MAX_COST_PER_REQUEST, "0.10")?.toDoubleOrNull() ?: 0.10
        set(value) {
            prefs.edit().putString(KEY_MAX_COST_PER_REQUEST, value.toString()).apply()
            notifyPreferencesChanged()
        }

    /**
     * Предпочитаемый провайдер (null = авто).
     */
    var preferredProvider: String?
        get() = prefs.getString(KEY_PREFERRED_PROVIDER, null)
        set(value) {
            prefs.edit().putString(KEY_PREFERRED_PROVIDER, value).apply()
            notifyPreferencesChanged()
        }

    /**
     * Предпочитаемая модель (null = авто).
     */
    var preferredModel: String?
        get() = prefs.getString(KEY_PREFERRED_MODEL, null)
        set(value) {
            prefs.edit().putString(KEY_PREFERRED_MODEL, value).apply()
            notifyPreferencesChanged()
        }

    /**
     * Автоматический fallback при ошибке.
     */
    var autoFallback: Boolean
        get() = prefs.getBoolean(KEY_AUTO_FALLBACK, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_FALLBACK, value).apply()
            notifyPreferencesChanged()
        }

    /**
     * Streaming по умолчанию.
     */
    var streamingDefault: Boolean
        get() = prefs.getBoolean(KEY_STREAMING_DEFAULT, false)
        set(value) {
            prefs.edit().putBoolean(KEY_STREAMING_DEFAULT, value).apply()
            notifyPreferencesChanged()
        }

    /**
     * Кэширование ответов.
     */
    var cacheEnabled: Boolean
        get() = prefs.getBoolean(KEY_CACHE_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_CACHE_ENABLED, value).apply()
            notifyPreferencesChanged()
        }

    /**
     * Количество retries.
     */
    var retryCount: Int
        get() = prefs.getInt(KEY_RETRY_COUNT, 2)
        set(value) {
            prefs.edit().putInt(KEY_RETRY_COUNT, value.coerceIn(0, 5)).apply()
            notifyPreferencesChanged()
        }

    /**
     * Таймаут запроса (мс).
     */
    var timeoutMs: Long
        get() = prefs.getLong(KEY_TIMEOUT_MS, 60000L)
        set(value) {
            prefs.edit().putLong(KEY_TIMEOUT_MS, value).apply()
            notifyPreferencesChanged()
        }

    /**
     * Дневной бюджет (USD).
     */
    var budgetDailyUsd: Double
        get() = prefs.getString(KEY_BUDGET_DAILY, "5.00")?.toDoubleOrNull() ?: 5.00
        set(value) {
            prefs.edit().putString(KEY_BUDGET_DAILY, value.toString()).apply()
            notifyPreferencesChanged()
        }

    /**
     * Месячный бюджет (USD).
     */
    var budgetMonthlyUsd: Double
        get() = prefs.getString(KEY_BUDGET_MONTHLY, "50.00")?.toDoubleOrNull() ?: 50.00
        set(value) {
            prefs.edit().putString(KEY_BUDGET_MONTHLY, value.toString()).apply()
            notifyPreferencesChanged()
        }

    /**
     * Включить observability (tracing/metrics).
     */
    var enableObservability: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_OBSERVABILITY, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLE_OBSERVABILITY, value).apply()
            notifyPreferencesChanged()
        }

    /**
     * Provider-specific preferences.
     */
    private val providerPrefs = mutableMapOf<String, ProviderSpecificPreferences>()

    /**
     * Получает предпочтения для конкретного провайдера.
     */
    fun getProviderPreferences(providerId: String): ProviderSpecificPreferences {
        return providerPrefs.getOrPut(providerId) {
            loadProviderPreferences(providerId)
        }
    }

    /**
     * Сохраняет предпочтения для провайдера.
     */
    fun setProviderPreferences(providerId: String, providerPrefs: ProviderSpecificPreferences) {
        this.providerPrefs[providerId] = providerPrefs
        saveProviderPreferences(providerId, providerPrefs)
        notifyPreferencesChanged()
    }

    /**
     * Сбрасывает все настройки к default.
     */
    fun resetToDefaults() {
        prefs.edit().clear().apply()
        providerPrefs.clear()
        _strategyFlow.value = getStrategy()
        _preferencesFlow.value = getAllPreferences()
        Log.i(TAG, "Preferences reset to defaults")
    }

    /**
     * Экспортирует настройки в JSON.
     */
    fun exportToJson(): String {
        val allPrefs = getAllPreferences()
        return """
        {
            "strategy": "${allPrefs.strategy}",
            "costWeight": ${allPrefs.costWeight},
            "latencyWeight": ${allPrefs.latencyWeight},
            "qualityWeight": ${allPrefs.qualityWeight},
            "reliabilityWeight": ${allPrefs.reliabilityWeight},
            "maxAcceptableLatencyMs": ${allPrefs.maxAcceptableLatencyMs},
            "maxCostPerRequest": ${allPrefs.maxCostPerRequest},
            "preferredProvider": "${allPrefs.preferredProvider ?: ""}",
            "preferredModel": "${allPrefs.preferredModel ?: ""}",
            "autoFallback": ${allPrefs.autoFallback},
            "streamingDefault": ${allPrefs.streamingDefault},
            "cacheEnabled": ${allPrefs.cacheEnabled},
            "retryCount": ${allPrefs.retryCount},
            "timeoutMs": ${allPrefs.timeoutMs},
            "budgetDailyUsd": ${allPrefs.budgetDailyUsd},
            "budgetMonthlyUsd": ${allPrefs.budgetMonthlyUsd},
            "enableObservability": ${allPrefs.enableObservability}
        }
        """.trimIndent()
    }

    /**
     * Импортирует настройки из JSON.
     */
    fun importFromJson(json: String): Boolean {
        return try {
            // Упрощённый парсинг — в реальности используйте Gson/Moshi
            val map = parseSimpleJson(json)
            
            map["strategy"]?.let { strategy = ModelRouter.RoutingStrategy.valueOf(it) }
            map["costWeight"]?.toFloatOrNull()?.let { costWeight = it }
            map["latencyWeight"]?.toFloatOrNull()?.let { latencyWeight = it }
            map["qualityWeight"]?.toFloatOrNull()?.let { qualityWeight = it }
            map["reliabilityWeight"]?.toFloatOrNull()?.let { reliabilityWeight = it }
            map["maxAcceptableLatencyMs"]?.toLongOrNull()?.let { maxAcceptableLatencyMs = it }
            map["maxCostPerRequest"]?.toDoubleOrNull()?.let { maxCostPerRequest = it }
            map["preferredProvider"]?.let { preferredProvider = it.ifEmpty { null } }
            map["preferredModel"]?.let { preferredModel = it.ifEmpty { null } }
            map["autoFallback"]?.toBooleanStrictOrNull()?.let { autoFallback = it }
            map["streamingDefault"]?.toBooleanStrictOrNull()?.let { streamingDefault = it }
            map["cacheEnabled"]?.toBooleanStrictOrNull()?.let { cacheEnabled = it }
            map["retryCount"]?.toIntOrNull()?.let { retryCount = it }
            map["timeoutMs"]?.toLongOrNull()?.let { timeoutMs = it }
            map["budgetDailyUsd"]?.toDoubleOrNull()?.let { budgetDailyUsd = it }
            map["budgetMonthlyUsd"]?.toDoubleOrNull()?.let { budgetMonthlyUsd = it }
            map["enableObservability"]?.toBooleanStrictOrNull()?.let { enableObservability = it }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import preferences", e)
            false
        }
    }

    // Private helpers
    
    private fun getStrategy(): ModelRouter.RoutingStrategy {
        val name = prefs.getString(KEY_STRATEGY, ModelRouter.RoutingStrategy.WEIGHTED_LATENCY.name)
        return try {
            ModelRouter.RoutingStrategy.valueOf(name!!)
        } catch (e: Exception) {
            ModelRouter.RoutingStrategy.WEIGHTED_LATENCY
        }
    }

    private fun getAllPreferences(): RoutingPreferences {
        return RoutingPreferences(
            strategy = strategy,
            costWeight = costWeight,
            latencyWeight = latencyWeight,
            qualityWeight = qualityWeight,
            reliabilityWeight = reliabilityWeight,
            maxAcceptableLatencyMs = maxAcceptableLatencyMs,
            maxCostPerRequest = maxCostPerRequest,
            preferredProvider = preferredProvider,
            preferredModel = preferredModel,
            autoFallback = autoFallback,
            streamingDefault = streamingDefault,
            cacheEnabled = cacheEnabled,
            retryCount = retryCount,
            timeoutMs = timeoutMs,
            budgetDailyUsd = budgetDailyUsd,
            budgetMonthlyUsd = budgetMonthlyUsd,
            enableObservability = enableObservability
        )
    }

    private fun notifyPreferencesChanged() {
        _preferencesFlow.value = getAllPreferences()
    }

    private fun loadProviderPreferences(providerId: String): ProviderSpecificPreferences {
        val prefix = "provider_${providerId}_"
        return ProviderSpecificPreferences(
            isEnabled = prefs.getBoolean("${prefix}enabled", true),
            priority = prefs.getInt("${prefix}priority", 5),
            customBaseUrl = prefs.getString("${prefix}base_url", null),
            customTimeoutMs = prefs.getLong("${prefix}timeout", timeoutMs),
            maxTokensOverride = prefs.getInt("${prefix}max_tokens", 0).takeIf { it > 0 },
            customHeaders = emptyMap() // Сложный тип — требует отдельного хранения
        )
    }

    private fun saveProviderPreferences(providerId: String, prefsData: ProviderSpecificPreferences) {
        val prefix = "provider_${providerId}_"
        prefs.edit()
            .putBoolean("${prefix}enabled", prefsData.isEnabled)
            .putInt("${prefix}priority", prefsData.priority)
            .putString("${prefix}base_url", prefsData.customBaseUrl)
            .putLong("${prefix}timeout", prefsData.customTimeoutMs)
            .apply()
    }

    private fun parseSimpleJson(json: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val regex = """"(\w+)"\s*:\s*([^,\n}]+)""".toRegex()
        regex.findAll(json).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2].trim().removeSurrounding("\"")
            result[key] = value
        }
        return result
    }

    // Data classes

    data class RoutingPreferences(
        val strategy: ModelRouter.RoutingStrategy,
        val costWeight: Float,
        val latencyWeight: Float,
        val qualityWeight: Float,
        val reliabilityWeight: Float,
        val maxAcceptableLatencyMs: Long,
        val maxCostPerRequest: Double,
        val preferredProvider: String?,
        val preferredModel: String?,
        val autoFallback: Boolean,
        val streamingDefault: Boolean,
        val cacheEnabled: Boolean,
        val retryCount: Int,
        val timeoutMs: Long,
        val budgetDailyUsd: Double,
        val budgetMonthlyUsd: Double,
        val enableObservability: Boolean
    )

    data class ProviderSpecificPreferences(
        val isEnabled: Boolean = true,
        val priority: Int = 5, // 1-10, выше = приоритетнее
        val customBaseUrl: String? = null,
        val customTimeoutMs: Long = 60000L,
        val maxTokensOverride: Int? = null,
        val customHeaders: Map<String, String> = emptyMap()
    )
}
