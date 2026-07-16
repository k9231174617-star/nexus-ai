package com.nexus.agent.core.router

import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Управляет цепочкой fallback-провайдеров.
 * Определяет порядок fallback и логику переключения при failures.
 */
class FallbackChain(
    private val preferences: SharedPreferences? = null
) {
    companion object {
        private const val TAG = "FallbackChain"
        private const val PREFS_KEY_CHAIN = "fallback_chain_order"
        private const val PREFS_KEY_PRIMARY = "fallback_primary"
    }

    private val mutex = Mutex()
    
    // Основной список провайдеров в порядке приоритета
    private val chain = CopyOnWriteArrayList<String>()
    
    // Текущий primary провайдер
    private var primaryProvider: String? = null
    
    // Счётчики fallback invocations
    private val fallbackStats = mutableMapOf<String, FallbackStats>()

    data class FallbackStats(
        val providerId: String,
        var fallbackFromCount: Int = 0,
        var fallbackToCount: Int = 0,
        var lastFallbackTime: Long = 0
    )

    data class ChainConfig(
        val providers: List<String>,
        val primaryProvider: String?,
        val autoReorder: Boolean = true,      // Автоматически менять порядок на основе health
        val stickyPrimary: Boolean = false,   // Возвращаться к primary при восстановлении
        val maxFallbackDepth: Int = 3         // Максимальная глубина fallback
    )

    /**
     * Инициализирует цепочку fallback.
     */
    suspend fun initialize(config: ChainConfig) = mutex.withLock {
        chain.clear()
        chain.addAll(config.providers)
        
        primaryProvider = config.primaryProvider ?: chain.firstOrNull()
        
        // Загружаем сохранённый порядок если есть
        preferences?.getString(PREFS_KEY_CHAIN, null)?.let { saved ->
            val savedOrder = saved.split(",")
            if (savedOrder.all { it in chain }) {
                chain.clear()
                chain.addAll(savedOrder)
            }
        }
        
        // Инициализируем stats
        chain.forEach { providerId ->
            fallbackStats[providerId] = FallbackStats(providerId)
        }
        
        Log.i(TAG, "Fallback chain initialized: ${chain.joinToString(" -> ")}")
        Log.i(TAG, "Primary provider: $primaryProvider")
    }

    /**
     * Возвращает primary провайдер.
     */
    fun getPrimaryProvider(): String? = primaryProvider

    /**
     * Устанавливает primary провайдер.
     */
    suspend fun setPrimaryProvider(providerId: String) = mutex.withLock {
        if (providerId !in chain) {
            throw IllegalArgumentException("Provider $providerId not in fallback chain")
        }
        primaryProvider = providerId
        preferences?.edit()?.putString(PREFS_KEY_PRIMARY, providerId)?.apply()
        Log.i(TAG, "Primary provider set to: $providerId")
    }

    /**
     * Возвращает следующего провайдера в цепочке fallback.
     */
    fun getNextProvider(currentProviderId: String): String? {
        val currentIndex = chain.indexOf(currentProviderId)
        if (currentIndex == -1 || currentIndex >= chain.size - 1) {
            return null // Конец цепочки
        }
        
        val nextProvider = chain[currentIndex + 1]
        
        // Обновляем stats
        fallbackStats[currentProviderId]?.fallbackFromCount = 
            fallbackStats[currentProviderId]?.fallbackFromCount?.plus(1) ?: 1
        fallbackStats[nextProvider]?.fallbackToCount = 
            fallbackStats[nextProvider]?.fallbackToCount?.plus(1) ?: 1
        fallbackStats[nextProvider]?.lastFallbackTime = System.currentTimeMillis()
        
        Log.d(TAG, "Fallback: $currentProviderId -> $nextProvider")
        return nextProvider
    }

    /**
     * Возвращает провайдера по индексу.
     */
    fun getProviderAt(index: Int): String? {
        return chain.getOrNull(index)
    }

    /**
     * Возвращает позицию провайдера в цепочке.
     */
    fun getProviderIndex(providerId: String): Int {
        return chain.indexOf(providerId)
    }

    /**
     * Проверяет, является ли провайдер primary.
     */
    fun isPrimary(providerId: String): Boolean {
        return providerId == primaryProvider
    }

    /**
     * Проверяет, является ли провайдер последним в цепочке.
     */
    fun isLastResort(providerId: String): Boolean {
        return chain.lastOrNull() == providerId
    }

    /**
     * Перемещает провайдера в начало цепочки (повышает приоритет).
     */
    suspend fun promoteProvider(providerId: String) = mutex.withLock {
        if (providerId !in chain) return
        
        chain.remove(providerId)
        chain.add(0, providerId)
        saveChainOrder()
        Log.i(TAG, "Promoted $providerId to position 0")
    }

    /**
     * Перемещает провайдера в конец цепочки (понижает приоритет).
     */
    suspend fun demoteProvider(providerId: String) = mutex.withLock {
        if (providerId !in chain) return
        
        chain.remove(providerId)
        chain.add(providerId)
        saveChainOrder()
        Log.i(TAG, "Demoted $providerId to last position")
    }

    /**
     * Меняет позицию провайдера в цепочке.
     */
    suspend fun reorderProvider(providerId: String, newIndex: Int) = mutex.withLock {
        if (providerId !in chain || newIndex < 0 || newIndex >= chain.size) return
        
        chain.remove(providerId)
        chain.add(newIndex.coerceIn(0, chain.size), providerId)
        saveChainOrder()
        Log.i(TAG, "Reordered $providerId to position $newIndex")
    }

    /**
     * Добавляет провайдера в цепочку.
     */
    suspend fun addProvider(providerId: String, position: Int = -1) = mutex.withLock {
        if (providerId in chain) return
        
        if (position >= 0 && position <= chain.size) {
            chain.add(position, providerId)
        } else {
            chain.add(providerId)
        }
        
        fallbackStats[providerId] = FallbackStats(providerId)
        saveChainOrder()
        Log.i(TAG, "Added $providerId to chain at position ${chain.indexOf(providerId)}")
    }

    /**
     * Удаляет провайдера из цепочки.
     */
    suspend fun removeProvider(providerId: String) = mutex.withLock {
        chain.remove(providerId)
        fallbackStats.remove(providerId)
        
        if (primaryProvider == providerId) {
            primaryProvider = chain.firstOrNull()
        }
        
        saveChainOrder()
        Log.i(TAG, "Removed $providerId from chain")
    }

    /**
     * Автоматически переупорядочивает цепочку на основе health scores.
     */
    suspend fun autoReorder(healthScores: Map<String, Double>) = mutex.withLock {
        val sorted = chain.sortedByDescending { healthScores[it] ?: 0.0 }
        
        if (sorted != chain.toList()) {
            chain.clear()
            chain.addAll(sorted)
            saveChainOrder()
            Log.i(TAG, "Auto-reordered chain by health: ${chain.joinToString(" -> ")}")
        }
    }

    /**
     * Возвращает полную цепочку.
     */
    fun getChain(): List<String> = chain.toList()

    /**
     * Возвращает длину цепочки.
     */
    fun getChainLength(): Int = chain.size

    /**
     * Возвращает fallback path от провайдера.
     */
    fun getFallbackPath(fromProviderId: String): List<String> {
        val index = chain.indexOf(fromProviderId)
        if (index == -1) return emptyList()
        return chain.subList(index + 1, chain.size)
    }

    /**
     * Возвращает stats fallback.
     */
    fun getFallbackStats(): Map<String, FallbackStats> {
        return fallbackStats.toMap()
    }

    /**
     * Сбрасывает stats.
     */
    fun resetStats() {
        fallbackStats.values.forEach {
            it.fallbackFromCount = 0
            it.fallbackToCount = 0
            it.lastFallbackTime = 0
        }
    }

    /**
     * Проверяет, может ли произойти fallback от провайдера.
     */
    fun canFallback(fromProviderId: String): Boolean {
        val index = chain.indexOf(fromProviderId)
        return index != -1 && index < chain.size - 1
    }

    /**
     * Возвращает количество доступных fallback options.
     */
    fun getFallbackOptionsCount(fromProviderId: String): Int {
        val index = chain.indexOf(fromProviderId)
        if (index == -1) return 0
        return chain.size - index - 1
    }

    /**
     * Создаёт snapshot текущего состояния.
     */
    fun snapshot(): ChainSnapshot {
        return ChainSnapshot(
            chain = chain.toList(),
            primaryProvider = primaryProvider,
            stats = fallbackStats.toMap()
        )
    }

    /**
     * Восстанавливает состояние из snapshot.
     */
    suspend fun restore(snapshot: ChainSnapshot) = mutex.withLock {
        chain.clear()
        chain.addAll(snapshot.chain)
        primaryProvider = snapshot.primaryProvider
        fallbackStats.clear()
        fallbackStats.putAll(snapshot.stats)
        saveChainOrder()
    }

    private fun saveChainOrder() {
        preferences?.edit()?.putString(PREFS_KEY_CHAIN, chain.joinToString(","))?.apply()
    }

    data class ChainSnapshot(
        val chain: List<String>,
        val primaryProvider: String?,
        val stats: Map<String, FallbackStats>
    )
}
